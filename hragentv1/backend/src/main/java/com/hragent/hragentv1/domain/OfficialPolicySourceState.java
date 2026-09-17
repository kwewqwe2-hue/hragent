package com.hragent.hragentv1.domain;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="official_policy_source_states")
public class OfficialPolicySourceState {
 @Id public String id;
 public String name;
 public String url;
 public LocalDateTime checkedAt;
 public LocalDateTime successfulAt;
 public int documentsChecked;
 public int recheckOffset;
 @Column(length=1000) public String error;
}
