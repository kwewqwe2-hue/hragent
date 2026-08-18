package com.hragent.hragentv1.web;

import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.LeaveDtos;
import com.hragent.hragentv1.service.AuthService;
import com.hragent.hragentv1.service.LeaveService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/leave")
public class LeaveController {
    private final AuthService authService;
    private final LeaveService leaveService;

    public LeaveController(AuthService authService, LeaveService leaveService) {
        this.authService = authService;
        this.leaveService = leaveService;
    }

    @GetMapping("/types")
    public ApiResponse<List<LeaveDtos.LeaveTypeOption>> types() {
        return ApiResponse.ok(leaveService.leaveTypes());
    }

    @GetMapping("/balances")
    public ApiResponse<List<LeaveDtos.BalanceView>> balances(HttpServletRequest request) {
        UserAccount user = authService.requireUser(request);
        return ApiResponse.ok(leaveService.balances(user));
    }

    @PostMapping
    public ApiResponse<LeaveDtos.LeaveRequestView> create(
            HttpServletRequest servletRequest,
            @Valid @RequestBody LeaveDtos.CreateLeaveRequest request
    ) {
        UserAccount user = authService.requireUser(servletRequest);
        return ApiResponse.ok("提交成功，等待主管审批", leaveService.create(user, request));
    }

    @GetMapping("/my")
    public ApiResponse<List<LeaveDtos.LeaveRequestView>> mine(HttpServletRequest request) {
        UserAccount user = authService.requireUser(request);
        return ApiResponse.ok(leaveService.mine(user));
    }

    @GetMapping("/calendar")
    public ApiResponse<LeaveDtos.CalendarView> calendar(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(required = false) Integer year
    ) {
        UserAccount user = authService.requireUser(request);
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        return ApiResponse.ok(leaveService.calendar(user, year == null ? LocalDate.now().getYear() : year));
    }

    @GetMapping("/manager/pending")
    public ApiResponse<List<LeaveDtos.LeaveRequestView>> managerPending(HttpServletRequest request) {
        UserAccount user = authService.requireUser(request);
        authService.requireRole(user, Role.MANAGER);
        return ApiResponse.ok(leaveService.managerPending(user));
    }

    @PutMapping("/manager/{id}/review")
    public ApiResponse<LeaveDtos.LeaveRequestView> managerReview(
            HttpServletRequest servletRequest,
            @PathVariable Long id,
            @Valid @RequestBody LeaveDtos.ReviewRequest request
    ) {
        UserAccount user = authService.requireUser(servletRequest);
        authService.requireRole(user, Role.MANAGER);
        return ApiResponse.ok(leaveService.managerReview(user, id, request));
    }

    @GetMapping("/hr/pending")
    public ApiResponse<List<LeaveDtos.LeaveRequestView>> hrPending(HttpServletRequest request) {
        UserAccount user = authService.requireUser(request);
        authService.requireRole(user, Role.HR);
        return ApiResponse.ok(leaveService.hrPending(user));
    }

    @PutMapping("/hr/{id}/record")
    public ApiResponse<LeaveDtos.LeaveRequestView> hrRecord(
            HttpServletRequest servletRequest,
            @PathVariable Long id,
            @Valid @RequestBody LeaveDtos.ReviewRequest request
    ) {
        UserAccount user = authService.requireUser(servletRequest);
        authService.requireRole(user, Role.HR);
        return ApiResponse.ok(leaveService.hrRecord(user, id, request));
    }

    @GetMapping("/all")
    public ApiResponse<List<LeaveDtos.LeaveRequestView>> all(HttpServletRequest request) {
        UserAccount user = authService.requireUser(request);
        authService.requireRole(user, Role.HR);
        return ApiResponse.ok(leaveService.all(user.getTenantId()));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> stats(HttpServletRequest request) {
        UserAccount user = authService.requireUser(request);
        return ApiResponse.ok(leaveService.stats(user));
    }
}
