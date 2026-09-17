package com.hragent.hragentv1.domain;
import jakarta.persistence.*;
import java.time.*;
@Entity
@Table(name="er_preferences", uniqueConstraints=@UniqueConstraint(columnNames={"tenantId","employeeId"}))
public class ErPreference {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false)
    private Long tenantId;
    @Column(nullable=false)
    private Long employeeId;
    
    private boolean analyticsEnabled;
    @Version private Long version;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId=value; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long value) { employeeId=value; }
    public boolean getAnalyticsEnabled() { return analyticsEnabled; }
    public void setAnalyticsEnabled(boolean value) { analyticsEnabled=value; }
}
