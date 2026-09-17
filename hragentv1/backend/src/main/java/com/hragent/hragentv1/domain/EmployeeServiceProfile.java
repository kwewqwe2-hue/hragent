package com.hragent.hragentv1.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

/** HR-maintained applicability and event dates; never inferred from an employee's chat. */
@Entity
@Table(name = "employee_service_profiles", uniqueConstraints = @UniqueConstraint(columnNames = {"tenantId", "employeeId"}))
public class EmployeeServiceProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long tenantId;
    @Column(nullable = false) private Long employeeId;
    @Column(length = 80) private String location;
    @Column(length = 80) private String jobGrade;
    @Column(length = 80) private String workType;
    @Column(length = 160) private String legalEntity;
    private LocalDate probationEndDate;
    private LocalDate medicalCheckDeadline;
    private LocalDate annualLeaveExpiresAt;
    @Column(length = 1000) private String medicalBookingUrl;

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long value) { employeeId = value; }
    public String getLocation() { return location; }
    public void setLocation(String value) { location = value; }
    public String getJobGrade() { return jobGrade; }
    public void setJobGrade(String value) { jobGrade = value; }
    public String getWorkType() { return workType; }
    public void setWorkType(String value) { workType = value; }
    public String getLegalEntity() { return legalEntity; }
    public void setLegalEntity(String value) { legalEntity = value; }
    public LocalDate getProbationEndDate() { return probationEndDate; }
    public void setProbationEndDate(LocalDate value) { probationEndDate = value; }
    public LocalDate getMedicalCheckDeadline() { return medicalCheckDeadline; }
    public void setMedicalCheckDeadline(LocalDate value) { medicalCheckDeadline = value; }
    public LocalDate getAnnualLeaveExpiresAt() { return annualLeaveExpiresAt; }
    public void setAnnualLeaveExpiresAt(LocalDate value) { annualLeaveExpiresAt = value; }
    public String getMedicalBookingUrl() { return medicalBookingUrl; }
    public void setMedicalBookingUrl(String value) { medicalBookingUrl = value; }
}
