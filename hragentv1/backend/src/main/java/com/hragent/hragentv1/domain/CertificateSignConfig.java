package com.hragent.hragentv1.domain;
import jakarta.persistence.*;
@Entity @Table(name="certificate_sign_configs")
public class CertificateSignConfig {
 @Id public Long tenantId;
 @Lob @Column(columnDefinition="TEXT") public String encryptedConfig;
 public boolean enabled;
}
