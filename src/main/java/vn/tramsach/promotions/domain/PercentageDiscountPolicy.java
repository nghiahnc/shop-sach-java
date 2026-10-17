package vn.tramsach.promotions.domain;

import vn.tramsach.shared.domain.BusinessException;

public final class PercentageDiscountPolicy extends DiscountPolicy {
  private final int percent;

  public PercentageDiscountPolicy(long minimum, int percent) {
    super(minimum);
    if (percent < 1 || percent > 100)
      throw new BusinessException("Phần trăm giảm phải từ 1 đến 100.");
    this.percent = percent;
  }

  protected long calculate(long subtotal) {
    return Math.multiplyExact(subtotal, percent) / 100;
  }
}
