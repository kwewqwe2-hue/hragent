package com.hragent.hragentv1.domain;
import jakarta.persistence.*;
import java.time.*;
@Entity
@Table(name="er_settings")
public class ErSettings {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false, unique=true)
    private Long tenantId;
    @Column(length=160)
    private String eapName;
    @Column(length=80)
    private String eapPhone;
    @Column(length=1000)
    private String eapUrl;
    @Column(length=500)
    private String erContact;
    @Column(length=1000)
    private String investigatorIds;
    @Version private Long version;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId=value; }
    public String getEapName() { return eapName; }
    public void setEapName(String value) { eapName=value; }
    public String getEapPhone() { return eapPhone; }
    public void setEapPhone(String value) { eapPhone=value; }
    public String getEapUrl() { return eapUrl; }
    public void setEapUrl(String value) { eapUrl=value; }
    public String getErContact() { return erContact; }
    public void setErContact(String value) { erContact=value; }
    public String getInvestigatorIds() { return investigatorIds; }
    public void setInvestigatorIds(String value) { investigatorIds=value; }
}
