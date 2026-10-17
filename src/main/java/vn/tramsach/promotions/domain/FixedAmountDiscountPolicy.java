package vn.tramsach.promotions.domain;

import vn.tramsach.shared.domain.BusinessException;

public final class FixedAmountDiscountPolicy extends DiscountPolicy {
  private final long amount;

  public FixedAmountDiscountPolicy(long minimum, long amount) {
    super(minimum);
    if (amount < 1) throw new BusinessException("Số tiền giảm phải lớn hơn 0.");
    this.amount = amount;
  }

  protected long calculate(long subtotal) {
    return amount;
  }
}
