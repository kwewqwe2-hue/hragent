package com.hragent.hragentv1.domain;
import jakarta.persistence.*;
import java.time.*;
@Entity
@Table(name="er_journey_tasks", uniqueConstraints=@UniqueConstraint(columnNames={"tenantId","employeeId","taskKey"}))
public class ErJourneyTask {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false)
    private Long tenantId;
    @Column(nullable=false)
    private Long employeeId;
    @Column(nullable=false,length=60)
    private String taskKey;
    
    private boolean done;
    
    private LocalDateTime updatedAt;
    @Version private Long version;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId=value; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long value) { employeeId=value; }
    public String getTaskKey() { return taskKey; }
    public void setTaskKey(String value) { taskKey=value; }
    public boolean getDone() { return done; }
    public void setDone(boolean value) { done=value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt=value; }
}
