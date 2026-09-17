package com.hragent.hragentv1.domain;
import jakarta.persistence.*;
import java.time.*;
@Entity
@Table(name="er_knowledge_gaps", uniqueConstraints=@UniqueConstraint(columnNames={"tenantId","topic"}))
public class ErKnowledgeGap {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false)
    private Long tenantId;
    @Column(nullable=false,length=80)
    private String topic;
    @Column(length=30)
    private String status;
    
    private long occurrences;
    
    private LocalDateTime updatedAt;
    @Version private Long version;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId=value; }
    public String getTopic() { return topic; }
    public void setTopic(String value) { topic=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
    public long getOccurrences() { return occurrences; }
    public void setOccurrences(long value) { occurrences=value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt=value; }
}
