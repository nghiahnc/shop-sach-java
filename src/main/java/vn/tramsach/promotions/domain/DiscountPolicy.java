package vn.tramsach.promotions.domain;

import vn.tramsach.shared.domain.*;

public abstract class DiscountPolicy {
  private final long minimum;

  protected DiscountPolicy(long minimum) {
    if (minimum < 0) throw new BusinessException("Điều kiện giảm giá không hợp lệ.");
    this.minimum = minimum;
  }

  public final Money apply(Money subtotal) {
    if (subtotal.vnd() < minimum)
      throw new BusinessException("Đơn chưa đạt giá trị tối thiểu của mã giảm giá.");
    return new Money(Math.min(subtotal.vnd(), calculate(subtotal.vnd())));
  }

  protected abstract long calculate(long subtotal);
}
