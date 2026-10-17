package vn.tramsach.cart.domain;

import vn.tramsach.shared.domain.Money;

public final class StandardShippingPolicy implements ShippingPolicy {
  public Money fee(Money merchandise) {
    return new Money(merchandise.vnd() >= 300000 ? 0 : 20000);
  }
}
