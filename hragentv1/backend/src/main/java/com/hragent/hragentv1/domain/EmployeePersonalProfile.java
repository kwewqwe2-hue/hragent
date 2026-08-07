package com.hragent.hragentv1.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "employee_personal_profiles",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenantId", "employeeId"})
)
public class EmployeePersonalProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Long employeeId;

    @Column(length = 80)
    private String legalName;

    @Column(length = 120)
    private String englishName;

    @Column(length = 20)
    private String gender;

    private LocalDate birthDate;

    @Column(length = 80)
    private String nationality;

    @Column(length = 40)
    private String idType;

    @Column(length = 500)
    private String idNumberEncrypted;

    @Column(length = 500)
    private String passportNumberEncrypted;

    private LocalDate passportExpiryDate;

    @Column(length = 40)
    private String employmentType;

    private LocalDate contractStartDate;

    private LocalDate contractEndDate;

    @Column(length = 160)
    private String workLocation;

    @Column(precision = 15, scale = 2)
    private BigDecimal monthlySalary;

    @Column(length = 10)
    private String currency;

    @Column(length = 240)
    private String homeAddress;

    @Column(length = 80)
    private String emergencyContactName;

    @Column(length = 40)
    private String emergencyContactPhone;

    private Long updatedByEmployeeId;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getEnglishName() {
        return englishName;
    }

    public void setEnglishName(String englishName) {
        this.englishName = englishName;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getIdType() {
        return idType;
    }

    public void setIdType(String idType) {
        this.idType = idType;
    }

    public String getIdNumberEncrypted() {
        return idNumberEncrypted;
    }

    public void setIdNumberEncrypted(String idNumberEncrypted) {
        this.idNumberEncrypted = idNumberEncrypted;
    }

    public String getPassportNumberEncrypted() {
        return passportNumberEncrypted;
    }

    public void setPassportNumberEncrypted(String passportNumberEncrypted) {
        this.passportNumberEncrypted = passportNumberEncrypted;
    }

    public LocalDate getPassportExpiryDate() {
        return passportExpiryDate;
    }

    public void setPassportExpiryDate(LocalDate passportExpiryDate) {
        this.passportExpiryDate = passportExpiryDate;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(String employmentType) {
        this.employmentType = employmentType;
    }

    public LocalDate getContractStartDate() {
        return contractStartDate;
    }

    public void setContractStartDate(LocalDate contractStartDate) {
        this.contractStartDate = contractStartDate;
    }

    public LocalDate getContractEndDate() {
        return contractEndDate;
    }

    public void setContractEndDate(LocalDate contractEndDate) {
        this.contractEndDate = contractEndDate;
    }

    public String getWorkLocation() {
        return workLocation;
    }

    public void setWorkLocation(String workLocation) {
        this.workLocation = workLocation;
    }

    public BigDecimal getMonthlySalary() {
        return monthlySalary;
    }

    public void setMonthlySalary(BigDecimal monthlySalary) {
        this.monthlySalary = monthlySalary;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getHomeAddress() {
        return homeAddress;
    }

    public void setHomeAddress(String homeAddress) {
        this.homeAddress = homeAddress;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public void setEmergencyContactName(String emergencyContactName) {
        this.emergencyContactName = emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public void setEmergencyContactPhone(String emergencyContactPhone) {
        this.emergencyContactPhone = emergencyContactPhone;
    }

    public Long getUpdatedByEmployeeId() {
        return updatedByEmployeeId;
    }

    public void setUpdatedByEmployeeId(Long updatedByEmployeeId) {
        this.updatedByEmployeeId = updatedByEmployeeId;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
