package com.expensewise.entitlement.entity;

import com.expensewise.entitlement.Feature;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_feature_entitlements")
@Getter
@Setter
@NoArgsConstructor
public class UserFeatureEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Feature feature;

    @Column(nullable = false)
    private boolean enabled = true;

    public UserFeatureEntitlement(Long userId, Feature feature, boolean enabled) {
        this.userId = userId;
        this.feature = feature;
        this.enabled = enabled;
    }
}
