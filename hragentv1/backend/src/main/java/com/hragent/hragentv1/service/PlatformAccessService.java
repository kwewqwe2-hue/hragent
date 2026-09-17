package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.PlatformAccessRequest;
import com.hragent.hragentv1.domain.PlatformAccessStatus;
import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.PlatformAccessDtos;
import com.hragent.hragentv1.repo.PlatformAccessRequestRepository;
import com.hragent.hragentv1.repo.UserAccountRepository;
import com.hragent.hragentv1.web.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PlatformAccessService {
    private final PlatformAccessRequestRepository requests;
    private final UserAccountRepository users;

    public PlatformAccessService(PlatformAccessRequestRepository requests, UserAccountRepository users) {
        this.requests = requests;
        this.users = users;
    }

    @Transactional
    public PlatformAccessDtos.View create(UserAccount actor, PlatformAccessDtos.CreateRequest input) {
        if (!input.platformApi() && !input.agentApi()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Select at least one access scope");
        }
        UserAccount employee;
        PlatformAccessStatus initialStatus;
        if (actor.getRole() == Role.EMPLOYEE) {
            if (actor.getManagerId() == null) throw new AppException(HttpStatus.FORBIDDEN, "Employee must have a direct manager");
            employee = actor;
            initialStatus = PlatformAccessStatus.PENDING_MANAGER;
        } else if (actor.getRole() == Role.MANAGER) {
            if (input.employeeId() == null) throw new AppException(HttpStatus.BAD_REQUEST, "Employee is required for a manager request");
            employee = users.findById(input.employeeId())
                    .filter(candidate -> candidate.getTenantId().equals(actor.getTenantId()))
                    .filter(candidate -> candidate.getRole() == Role.EMPLOYEE)
                    .filter(candidate -> actor.getId().equals(candidate.getManagerId()))
                    .orElseThrow(() -> new AppException(HttpStatus.FORBIDDEN, "Only direct employees can be requested"));
            initialStatus = PlatformAccessStatus.PENDING_HR;
        } else {
            throw new AppException(HttpStatus.FORBIDDEN, "Only employees or managers can submit requests");
        }
        PlatformAccessRequest request = new PlatformAccessRequest();
        request.setTenantId(actor.getTenantId());
        request.setEmployeeId(employee.getId());
        request.setManagerId(employee.getManagerId());
        request.setPlatformApi(input.platformApi());
        request.setAgentApi(input.agentApi());
        request.setReason(input.reason());
        request.setStatus(initialStatus);
        if (actor.getRole() == Role.MANAGER) {
            request.setReviewedById(actor.getId());
            request.setReviewedAt(LocalDateTime.now());
        }
        return view(requests.save(request));
    }

    public List<PlatformAccessDtos.View> mine(UserAccount actor) {
        return requests.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(actor.getTenantId(), actor.getId()).stream().map(this::view).toList();
    }

    public List<PlatformAccessDtos.View> manager(UserAccount actor) {
        if (actor.getRole() != Role.MANAGER) throw new AppException(HttpStatus.FORBIDDEN, "Manager role required");
        return requests.findByTenantIdAndManagerIdOrderByCreatedAtDesc(actor.getTenantId(), actor.getId()).stream().map(this::view).toList();
    }

    public List<PlatformAccessDtos.View> pending(UserAccount actor) {
        if (actor.getRole() != Role.HR) throw new AppException(HttpStatus.FORBIDDEN, "HR role required");
        return requests.findByTenantIdAndStatusInOrderByCreatedAtDesc(actor.getTenantId(), List.of(PlatformAccessStatus.PENDING, PlatformAccessStatus.PENDING_HR)).stream().map(this::view).toList();
    }

    @Transactional
    public PlatformAccessDtos.View managerReview(UserAccount actor, Long id, PlatformAccessDtos.ReviewRequest input) {
        PlatformAccessRequest request = find(actor, id);
        if (actor.getRole() != Role.MANAGER || !actor.getId().equals(request.getManagerId()) || request.getStatus() != PlatformAccessStatus.PENDING_MANAGER) {
            throw new AppException(HttpStatus.FORBIDDEN, "Manager review unavailable");
        }
        applyReview(request, actor, input, input.approved() ? PlatformAccessStatus.PENDING_HR : PlatformAccessStatus.REJECTED);
        return view(requests.save(request));
    }

    @Transactional
    public PlatformAccessDtos.View review(UserAccount actor, Long id, PlatformAccessDtos.ReviewRequest input) {
        PlatformAccessRequest request = find(actor, id);
        if (actor.getRole() != Role.HR || (request.getStatus() != PlatformAccessStatus.PENDING && request.getStatus() != PlatformAccessStatus.PENDING_HR)) {
            throw new AppException(HttpStatus.FORBIDDEN, "HR review unavailable");
        }
        applyReview(request, actor, input, input.approved() ? PlatformAccessStatus.APPROVED : PlatformAccessStatus.REJECTED);
        return view(requests.save(request));
    }

    private void applyReview(PlatformAccessRequest request, UserAccount actor, PlatformAccessDtos.ReviewRequest input, PlatformAccessStatus status) {
        request.setStatus(status);
        request.setReviewOpinion(input.opinion());
        request.setReviewedById(actor.getId());
        request.setReviewedAt(LocalDateTime.now());
    }

    private PlatformAccessRequest find(UserAccount actor, Long id) {
        return requests.findByIdAndTenantId(id, actor.getTenantId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Request not found"));
    }

    private PlatformAccessDtos.View view(PlatformAccessRequest request) {
        UserAccount employee = users.findById(request.getEmployeeId()).orElseThrow();
        UserAccount manager = users.findById(request.getManagerId()).orElseThrow();
        return new PlatformAccessDtos.View(request.getId(), employee.getId(), employee.getName(), manager.getId(), manager.getName(),
                request.isPlatformApi(), request.isAgentApi(), request.getStatus(), request.getReason(), request.getReviewOpinion(),
                request.getCreatedAt(), request.getReviewedAt());
    }
}
