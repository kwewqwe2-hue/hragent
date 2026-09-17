package com.hragent.hragentv1.domain;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
@Table(name="employee_payslips",uniqueConstraints=@UniqueConstraint(columnNames={"tenantId","employeeId","payMonth"}))
public class EmployeePayslip {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Version public Long version;
 @Column(nullable=false) public Long tenantId;
 @Column(nullable=false) public Long employeeId;
 @Column(nullable=false,length=7) public String payMonth;
 @Column(nullable=false,columnDefinition="TEXT") public String encryptedDetails;
 public Long publishedBy;
 public LocalDateTime updatedAt=LocalDateTime.now();
}
