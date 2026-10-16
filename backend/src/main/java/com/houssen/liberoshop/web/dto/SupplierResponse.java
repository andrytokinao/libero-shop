package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Supplier;
import com.houssen.liberoshop.service.SupplierService.CreatedSupplier;

import java.math.BigDecimal;

/**
 * @param deliveryCount  number of supplies received from this supplier
 * @param unitsReceived  total units received, all products together
 */
public record SupplierResponse(Long id,
                               String name,
                               String contact,
                               String suppliedProducts,
                               long deliveryCount,
                               BigDecimal unitsReceived) {

    public static SupplierResponse of(Supplier supplier, long deliveryCount, BigDecimal unitsReceived) {
        return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getContact(),
                supplier.getSuppliedProducts(), deliveryCount, unitsReceived);
    }

    /** Just added: nothing received from them yet. */
    public static SupplierResponse created(CreatedSupplier supplier) {
        return new SupplierResponse(supplier.id(), supplier.name(), supplier.contact(), null, 0,
                BigDecimal.ZERO);
    }
}
