package com.hragent.hragentv1.domain;
import jakarta.persistence.*;
import java.time.*;
@Entity
@Table(name="er_signals", uniqueConstraints=@UniqueConstraint(columnNames={"tenantId","participant","topic","week"}))
public class ErSignal {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false)
    private Long tenantId;
    @Column(length=80)
    private String department;
    @Column(length=80)
    private String topic;
    @Column(length=64)
    private String participant;
    
    private boolean unresolved;
    
    private LocalDate week;
    @Version private Long version;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId=value; }
    public String getDepartment() { return department; }
    public void setDepartment(String value) { department=value; }
    public String getTopic() { return topic; }
    public void setTopic(String value) { topic=value; }
    public String getParticipant() { return participant; }
    public void setParticipant(String value) { participant=value; }
    public boolean getUnresolved() { return unresolved; }
    public void setUnresolved(boolean value) { unresolved=value; }
    public LocalDate getWeek() { return week; }
    public void setWeek(LocalDate value) { week=value; }
}
