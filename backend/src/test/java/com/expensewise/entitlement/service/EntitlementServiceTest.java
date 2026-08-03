package com.expensewise.entitlement.service;

import com.expensewise.entitlement.Feature;
import com.expensewise.entitlement.entity.UserFeatureEntitlement;
import com.expensewise.entitlement.repository.UserFeatureEntitlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserFeatureEntitlementRepository entitlementRepository;

    private EntitlementService entitlementService;

    @BeforeEach
    void setUp() {
        entitlementService = new EntitlementService(entitlementRepository);
    }

    @Test
    void seedDefaultsEnablesAllFiveFeatures() {
        entitlementService.seedDefaults(USER_ID);

        ArgumentCaptor<UserFeatureEntitlement> captor = ArgumentCaptor.forClass(UserFeatureEntitlement.class);
        verify(entitlementRepository, times(Feature.values().length)).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(UserFeatureEntitlement::isEnabled);
        assertThat(captor.getAllValues()).extracting(UserFeatureEntitlement::getFeature)
                .containsExactlyInAnyOrder(Feature.values());
    }

    @Test
    void seedDefaultsWithAnExplicitSubsetDisablesEverythingElse() {
        entitlementService.seedDefaults(USER_ID, Set.of(Feature.TRANSACTIONS));

        ArgumentCaptor<UserFeatureEntitlement> captor = ArgumentCaptor.forClass(UserFeatureEntitlement.class);
        verify(entitlementRepository, times(Feature.values().length)).save(captor.capture());

        Map<Feature, Boolean> byFeature = new java.util.EnumMap<>(Feature.class);
        captor.getAllValues().forEach(e -> byFeature.put(e.getFeature(), e.isEnabled()));

        assertThat(byFeature.get(Feature.TRANSACTIONS)).isTrue();
        assertThat(byFeature.get(Feature.BUDGETS)).isFalse();
        assertThat(byFeature.get(Feature.CATEGORIES)).isFalse();
        assertThat(byFeature.get(Feature.REPORTS)).isFalse();
        assertThat(byFeature.get(Feature.AI_ASSISTANT)).isFalse();
    }

    @Test
    void isEnabledReflectsTheStoredRow() {
        when(entitlementRepository.findByUserIdAndFeature(USER_ID, Feature.BUDGETS))
                .thenReturn(Optional.of(new UserFeatureEntitlement(USER_ID, Feature.BUDGETS, false)));

        assertThat(entitlementService.isEnabled(USER_ID, Feature.BUDGETS)).isFalse();
    }

    @Test
    void isEnabledDefaultsToTrueWhenNoRowExists() {
        when(entitlementRepository.findByUserIdAndFeature(USER_ID, Feature.REPORTS)).thenReturn(Optional.empty());

        assertThat(entitlementService.isEnabled(USER_ID, Feature.REPORTS)).isTrue();
    }

    @Test
    void replaceAllUpdatesExistingRowsInPlaceAndCreatesMissingOnes() {
        UserFeatureEntitlement existingTransactions = new UserFeatureEntitlement(USER_ID, Feature.TRANSACTIONS, true);
        when(entitlementRepository.findByUserId(USER_ID)).thenReturn(List.of(existingTransactions));

        entitlementService.replaceAll(USER_ID, Set.of(Feature.CATEGORIES));

        assertThat(existingTransactions.isEnabled()).isFalse();
        verify(entitlementRepository, times(Feature.values().length - 1)).save(any(UserFeatureEntitlement.class));
    }
}
