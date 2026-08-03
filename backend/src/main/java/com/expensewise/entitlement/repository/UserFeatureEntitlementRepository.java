package com.expensewise.entitlement.repository;

import com.expensewise.entitlement.Feature;
import com.expensewise.entitlement.entity.UserFeatureEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserFeatureEntitlementRepository extends JpaRepository<UserFeatureEntitlement, Long> {

    List<UserFeatureEntitlement> findByUserId(Long userId);

    Optional<UserFeatureEntitlement> findByUserIdAndFeature(Long userId, Feature feature);

    void deleteByUserId(Long userId);

    interface FeatureDisabledCount {
        Feature getFeature();

        long getDisabledCount();
    }

    // Counts explicit disables, not explicit enables — a USER account with
    // no row at all for a feature is still able to use it (see
    // EntitlementService.isEnabled's default-to-enabled fallback), so
    // "% of users with this feature active" must be computed as
    // (regularUsers - disabledCount), never as a raw count of enabled=true
    // rows. Counting enabled rows directly would undercount whenever a user
    // is missing a row (e.g. an account created before this table existed).
    @Query("""
            select e.feature as feature, count(e) as disabledCount
            from UserFeatureEntitlement e
            where e.enabled = false
            group by e.feature
            """)
    List<FeatureDisabledCount> countDisabledByFeature();
}
