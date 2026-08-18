package com.hragent.hragentv1.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_accounts")
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tenantId;

    private Long accountId;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(nullable = false, length = 120)
    private String passwordHash;

    @Column(nullable = false, length = 80)
    private String employeeNo;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(length = 80)
    private String department;

    @Column(length = 80)
    private String title;

    @Column(length = 120)
    private String email;

    @Column(length = 40)
    private String phone;

    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EmployeeStatus employeeStatus = EmployeeStatus.ACTIVE;

    private Long managerId;

    @Column(length = 128)
    private String dingtalkUserId;

    @Column(length = 128)
    private String dingtalkStaffId;

    private LocalDateTime dingtalkBoundAt;

    @Column(length = 128)
    private String dingtalkBindingCodeHash;

    private LocalDateTime dingtalkBindingCodeExpiresAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo) {
        this.employeeNo = employeeNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public EmployeeStatus getEmployeeStatus() {
        return employeeStatus;
    }

    public void setEmployeeStatus(EmployeeStatus employeeStatus) {
        this.employeeStatus = employeeStatus;
    }

    public Long getManagerId() {
        return managerId;
    }

    public void setManagerId(Long managerId) {
        this.managerId = managerId;
    }

    public String getDingtalkUserId() {
        return dingtalkUserId;
    }

    public void setDingtalkUserId(String dingtalkUserId) {
        this.dingtalkUserId = dingtalkUserId;
    }

    public String getDingtalkStaffId() {
        return dingtalkStaffId;
    }

    public void setDingtalkStaffId(String dingtalkStaffId) {
        this.dingtalkStaffId = dingtalkStaffId;
    }

    public LocalDateTime getDingtalkBoundAt() {
        return dingtalkBoundAt;
    }

    public void setDingtalkBoundAt(LocalDateTime dingtalkBoundAt) {
        this.dingtalkBoundAt = dingtalkBoundAt;
    }

    public String getDingtalkBindingCodeHash() {
        return dingtalkBindingCodeHash;
    }

    public void setDingtalkBindingCodeHash(String dingtalkBindingCodeHash) {
        this.dingtalkBindingCodeHash = dingtalkBindingCodeHash;
    }

    public LocalDateTime getDingtalkBindingCodeExpiresAt() {
        return dingtalkBindingCodeExpiresAt;
    }

    public void setDingtalkBindingCodeExpiresAt(LocalDateTime dingtalkBindingCodeExpiresAt) {
        this.dingtalkBindingCodeExpiresAt = dingtalkBindingCodeExpiresAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
