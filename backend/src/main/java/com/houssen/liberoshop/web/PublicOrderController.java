package com.houssen.liberoshop.web;

import com.houssen.liberoshop.service.OnlineOrderService;
import com.houssen.liberoshop.web.dto.PublicMenuResponse;
import com.houssen.liberoshop.web.dto.PublicOrderRequest;
import com.houssen.liberoshop.web.dto.PublicOrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ordering from a table's QR code. Public: the customer has no account. The token in the path
 * is the only credential -- see {@link OnlineOrderService} for what it does and does not allow.
 */
@RestController
@RequestMapping("/api/public/tables/{token}")
public class PublicOrderController {

    private final OnlineOrderService onlineOrders;

    public PublicOrderController(OnlineOrderService onlineOrders) {
        this.onlineOrders = onlineOrders;
    }

    @GetMapping("/menu")
    public PublicMenuResponse menu(@PathVariable String token) {
        return onlineOrders.menu(token);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicOrderResponse order(@PathVariable String token, @Valid @RequestBody PublicOrderRequest request) {
        return onlineOrders.order(token, request);
    }

    @GetMapping("/orders/{invoiceNumber}")
    public PublicOrderResponse status(@PathVariable String token, @PathVariable String invoiceNumber) {
        return onlineOrders.status(token, invoiceNumber);
    }
}
