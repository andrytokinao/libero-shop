package com.houssen.libertyshop.service;

import com.houssen.libertyshop.entity.Payment;
import com.houssen.libertyshop.entity.RoleApp;
import com.houssen.libertyshop.repository.CashRemittanceRepository;
import com.houssen.libertyshop.repository.PaymentRepository;
import com.houssen.libertyshop.repository.UserAppRepository;
import com.houssen.libertyshop.web.dto.UserActivityResponse;
import com.houssen.libertyshop.web.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The super-admin view of the accounts and what each of them did today. */
@Service
@Transactional(readOnly = true)
public class UserAccountService {

    private final UserAppRepository users;
    private final PaymentRepository payments;
    private final CashRemittanceRepository remittances;
    private final DashboardService dashboardService;
    private final BusinessCalendar calendar;

    public UserAccountService(UserAppRepository users, PaymentRepository payments,
                              CashRemittanceRepository remittances, DashboardService dashboardService,
                              BusinessCalendar calendar) {
        this.users = users;
        this.payments = payments;
        this.remittances = remittances;
        this.dashboardService = dashboardService;
        this.calendar = calendar;
    }

    public List<UserResponse> findAll() {
        return users.findAllByOrderByFullNameAsc().stream().map(UserResponse::of).toList();
    }

    public List<UserResponse> findByRole(RoleApp role) {
        return users.findByRoleOrderByFullNameAsc(role).stream().map(UserResponse::of).toList();
    }

    public List<UserActivityResponse> findActivity() {
        Map<Long, Long> salesPerSeller = dashboardService.salesToday().stream()
                .collect(Collectors.groupingBy(sale -> sale.getSeller().getId(), Collectors.counting()));

        return users.findAllByOrderByFullNameAsc().stream()
                .map(user -> {
                    List<Payment> collectedToday = payments.findByCollectorAndPeriod(
                            user.getId(), calendar.startOfToday(), calendar.startOfTomorrow());
                    return new UserActivityResponse(
                            UserResponse.of(user),
                            salesPerSeller.getOrDefault(user.getId(), 0L),
                            collectedToday.stream().map(Payment::getAmount)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add),
                            // Cash taken at hand-over is what a depot agent's day amounts to.
                            user.getRole() == RoleApp.DEPOT_AGENT ? collectedToday.size() : 0,
                            remittances.countBySubmittedById(user.getId()));
                })
                .toList();
    }
}
