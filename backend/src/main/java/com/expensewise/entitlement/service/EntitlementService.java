package com.expensewise.entitlement.service;

import com.expensewise.entitlement.Feature;
import com.expensewise.entitlement.entity.UserFeatureEntitlement;
import com.expensewise.entitlement.repository.UserFeatureEntitlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every USER account gets one row per {@link Feature} value (never a sparse
 * table) — explicit and boring: "is this feature enabled" is always a direct
 * column read, never "enabled unless a row says otherwise". ADMIN accounts
 * are never gated by this table at all; see {@code FeatureEntitlementInterceptor}.
 */
@Service
public class EntitlementService {

    private final UserFeatureEntitlementRepository entitlementRepository;

    public EntitlementService(UserFeatureEntitlementRepository entitlementRepository) {
        this.entitlementRepository = entitlementRepository;
    }

    /** New users default to all features enabled unless the admin specifies otherwise. */
    @Transactional
    public void seedDefaults(Long userId) {
        seedDefaults(userId, Set.of(Feature.values()));
    }

    @Transactional
    public void seedDefaults(Long userId, Set<Feature> enabledFeatures) {
        for (Feature feature : Feature.values()) {
            entitlementRepository.save(new UserFeatureEntitlement(userId, feature, enabledFeatures.contains(feature)));
        }
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(Long userId, Feature feature) {
        return entitlementRepository.findByUserIdAndFeature(userId, feature)
                .map(UserFeatureEntitlement::isEnabled)
                // A USER account should always have all five rows (seeded at creation),
                // so this only applies defensively — default to enabled rather than
                // silently locking a user out of a feature no row exists for yet.
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public Map<Feature, Boolean> getEntitlements(Long userId) {
        Map<Feature, Boolean> result = new EnumMap<>(Feature.class);
        for (Feature feature : Feature.values()) {
            result.put(feature, true);
        }
        for (UserFeatureEntitlement entitlement : entitlementRepository.findByUserId(userId)) {
            result.put(entitlement.getFeature(), entitlement.isEnabled());
        }
        return result;
    }

    /** Replaces all five rows atomically to match {@code enabledFeatures} exactly. */
    @Transactional
    public void replaceAll(Long userId, Set<Feature> enabledFeatures) {
        List<UserFeatureEntitlement> existing = entitlementRepository.findByUserId(userId);
        Map<Feature, UserFeatureEntitlement> byFeature = new EnumMap<>(Feature.class);
        for (UserFeatureEntitlement entitlement : existing) {
            byFeature.put(entitlement.getFeature(), entitlement);
        }

        for (Feature feature : Feature.values()) {
            boolean enabled = enabledFeatures.contains(feature);
            UserFeatureEntitlement entitlement = byFeature.get(feature);
            if (entitlement == null) {
                entitlementRepository.save(new UserFeatureEntitlement(userId, feature, enabled));
            } else {
                entitlement.setEnabled(enabled);
            }
        }
    }
}
