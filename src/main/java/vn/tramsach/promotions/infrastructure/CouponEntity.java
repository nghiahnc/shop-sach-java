package vn.tramsach.promotions.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "coupons")
public class CouponEntity {
  @Id public String code;
  public String kind;
  public long value;
  public long minimum;
  public boolean active;

  @Column(name = "expires_at")
  public Instant expiresAt;
}
