package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * How this installation works, in one row -- see {@code ShopSettingsService}.
 *
 * <p>Explicit switches rather than the type alone: the type pre-fills them, and a shop whose way
 * of working sits between two types adjusts the one that differs.
 */
@Entity
@Table(name = "shop_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopSettings {

    /** Always {@code ShopSettingsService.SINGLETON_ID}: there is one shop per installation. */
    @Id
    private Long id;

    /**
     * A plain varchar rather than the native ENUM H2 would create: a type added later must not
     * need a migration to widen the column.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 40)
    private BusinessType businessType;

    @Column(nullable = false)
    private boolean separateDelivery;

    @Column(nullable = false)
    private boolean payAtDepot;

    @Column(nullable = false)
    private boolean dualControlRemittance;

    /** With a default: added after the table, whose existing row must get a value. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean cancelAfterDelivery;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean orderTakerCollects;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean onlineOrdering;

    public ShopFeatures features() {
        return new ShopFeatures(separateDelivery, payAtDepot, dualControlRemittance, cancelAfterDelivery,
                orderTakerCollects, onlineOrdering);
    }
}
