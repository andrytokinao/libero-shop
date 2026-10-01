package com.houssen.liberoshop.web;

import com.houssen.liberoshop.service.OnlineOrderService;
import com.houssen.liberoshop.web.dto.CreateDiningTableRequest;
import com.houssen.liberoshop.web.dto.DiningTableResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The tables and their QR codes: the super-admin's, like the rest of the configuration. */
@RestController
@RequestMapping("/api/tables")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class DiningTableController {

    private final OnlineOrderService onlineOrders;

    public DiningTableController(OnlineOrderService onlineOrders) {
        this.onlineOrders = onlineOrders;
    }

    @GetMapping
    public List<DiningTableResponse> tables() {
        return onlineOrders.tables();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiningTableResponse create(@Valid @RequestBody CreateDiningTableRequest request) {
        return onlineOrders.createTable(request.name());
    }

    @PostMapping("/{id}/regenerate")
    public DiningTableResponse regenerate(@PathVariable Long id) {
        return onlineOrders.regenerateToken(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        onlineOrders.deleteTable(id);
    }
}
