package com.hragent.hragentv1.domain;
import jakarta.persistence.*;
@Entity
@Table(name="er_pulse_responses", uniqueConstraints=@UniqueConstraint(columnNames={"tenantId","period","participant"}))
public class ErPulseResponse {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long tenantId;
    @Column(nullable=false,length=7) private String period;
    @Column(nullable=false,length=64) private String participant;
    @Column(nullable=false,length=1000) private String encryptedAnswers;
    public Long getTenantId(){return tenantId;}
    public void setTenantId(Long v){tenantId=v;}
    public String getPeriod(){return period;}
    public void setPeriod(String v){period=v;}
    public String getParticipant(){return participant;}
    public void setParticipant(String v){participant=v;}
    public String getEncryptedAnswers(){return encryptedAnswers;}
    public void setEncryptedAnswers(String v){encryptedAnswers=v;}
}
