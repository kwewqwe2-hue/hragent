package com.hragent.hragentv1.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "workspace_memberships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"workspaceId", "accountId"})
)
public class WorkspaceMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long workspaceId;

    @Column(nullable = false)
    private Long accountId;

    private Long employeeProfileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role = Role.EMPLOYEE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MembershipStatus status = MembershipStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime reviewedAt;

    private Long reviewedByAccountId;

    @Column(length = 80)
    private String draftEmployeeNo;

    @Column(length = 40)
    private String draftPhone;

    @Column(length = 80)
    private String draftDepartment;

    @Column(length = 80)
    private String draftTitle;

    @Column(length = 80)
    private String draftManagerEmployeeNo;

    private LocalDate draftEntryDate;

    public Long getId() {
        return id;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getEmployeeProfileId() {
        return employeeProfileId;
    }

    public void setEmployeeProfileId(Long employeeProfileId) {
        this.employeeProfileId = employeeProfileId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public void setStatus(MembershipStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Long getReviewedByAccountId() {
        return reviewedByAccountId;
    }

    public void setReviewedByAccountId(Long reviewedByAccountId) {
        this.reviewedByAccountId = reviewedByAccountId;
    }

    public String getDraftEmployeeNo() {
        return draftEmployeeNo;
    }

    public void setDraftEmployeeNo(String draftEmployeeNo) {
        this.draftEmployeeNo = draftEmployeeNo;
    }

    public String getDraftPhone() {
        return draftPhone;
    }

    public void setDraftPhone(String draftPhone) {
        this.draftPhone = draftPhone;
    }

    public String getDraftDepartment() {
        return draftDepartment;
    }

    public void setDraftDepartment(String draftDepartment) {
        this.draftDepartment = draftDepartment;
    }

    public String getDraftTitle() {
        return draftTitle;
    }

    public void setDraftTitle(String draftTitle) {
        this.draftTitle = draftTitle;
    }

    public String getDraftManagerEmployeeNo() {
        return draftManagerEmployeeNo;
    }

    public void setDraftManagerEmployeeNo(String draftManagerEmployeeNo) {
        this.draftManagerEmployeeNo = draftManagerEmployeeNo;
    }

    public LocalDate getDraftEntryDate() {
        return draftEntryDate;
    }

    public void setDraftEntryDate(LocalDate draftEntryDate) {
        this.draftEntryDate = draftEntryDate;
    }
}
