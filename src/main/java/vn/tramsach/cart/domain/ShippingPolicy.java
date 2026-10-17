package vn.tramsach.cart.domain;

import vn.tramsach.shared.domain.Money;

public interface ShippingPolicy {
  Money fee(Money merchandise);
}
