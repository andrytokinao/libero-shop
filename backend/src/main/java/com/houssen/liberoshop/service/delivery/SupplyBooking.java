package com.houssen.liberoshop.service.delivery;


import com.houssen.liberoshop.entity.DeliveryNote;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.web.dto.CreateSupplyRequest;
import lombok.Builder;

/** What is needed to record one receipt. {@code note} is null for a receipt booked alone. */
@Builder
public record SupplyBooking(CreateSupplyRequest request, UserApp receiver, DeliveryNote note) {
}