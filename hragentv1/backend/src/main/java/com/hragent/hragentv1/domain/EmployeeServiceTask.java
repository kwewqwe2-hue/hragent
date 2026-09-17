package com.hragent.hragentv1.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "employee_service_tasks", uniqueConstraints = @UniqueConstraint(columnNames = {"tenantId", "employeeId", "eventKey"}))
public class EmployeeServiceTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long tenantId;
    @Column(nullable = false) private Long employeeId;
    @Column(nullable = false, length = 100) private String eventKey;
    @Column(nullable = false, length = 30) private String kind;
    @Column(nullable = false, length = 120) private String title;
    @Column(length = 600) private String description;
    private LocalDate dueDate;
    @Column(length = 1000) private String actionUrl;
    @Column(nullable = false, length = 30) private String status = "OPEN";
    private LocalDate snoozedUntil;
    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long value) { employeeId = value; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String value) { eventKey = value; }
    public String getKind() { return kind; }
    public void setKind(String value) { kind = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate value) { dueDate = value; }
    public String getActionUrl() { return actionUrl; }
    public void setActionUrl(String value) { actionUrl = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public LocalDate getSnoozedUntil() { return snoozedUntil; }
    public void setSnoozedUntil(LocalDate value) { snoozedUntil = value; }
}
