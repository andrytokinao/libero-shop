package com.houssen.libertyshop.web;

import com.houssen.libertyshop.security.CurrentUser;
import com.houssen.libertyshop.service.DashboardService;
import com.houssen.libertyshop.web.dto.AdminDashboardResponse;
import com.houssen.libertyshop.web.dto.CashierDashboardResponse;
import com.houssen.libertyshop.web.dto.DepotDashboardResponse;
import com.houssen.libertyshop.web.dto.RevenueReportResponse;
import com.houssen.libertyshop.web.dto.StockDashboardResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One endpoint per landing page. Each is restricted to the role that owns it, so the
 * figures a role is not meant to see are never even computed for it.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUser currentUser;

    public DashboardController(DashboardService dashboardService, CurrentUser currentUser) {
        this.dashboardService = dashboardService;
        this.currentUser = currentUser;
    }

    @GetMapping("/cashier")
    @PreAuthorize("hasRole('CASHIER')")
    public CashierDashboardResponse cashier() {
        return dashboardService.forCashier(currentUser.require());
    }

    @GetMapping("/depot")
    @PreAuthorize("hasRole('DEPOT_AGENT')")
    public DepotDashboardResponse depot() {
        return dashboardService.forDepotAgent(currentUser.require());
    }

    @GetMapping("/stock")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public StockDashboardResponse stock() {
        return dashboardService.forDepotManager();
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AdminDashboardResponse admin() {
        return dashboardService.forAdmin();
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public RevenueReportResponse revenue() {
        return dashboardService.revenueReport();
    }
}
