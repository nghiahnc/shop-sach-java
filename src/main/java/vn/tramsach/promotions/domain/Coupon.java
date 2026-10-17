package vn.tramsach.promotions.domain;

import java.time.Instant;

public record Coupon(
    String code, String kind, long value, long minimum, boolean active, Instant expiresAt) {
  public DiscountPolicy policy() {
    return "PERCENT".equals(kind)
        ? new PercentageDiscountPolicy(minimum, Math.toIntExact(value))
        : new FixedAmountDiscountPolicy(minimum, value);
  }
}
