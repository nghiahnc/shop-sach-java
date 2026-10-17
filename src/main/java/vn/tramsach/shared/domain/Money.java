package vn.tramsach.shared.domain;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Locale;

public record Money(long vnd) implements Serializable {
  public Money {
    if (vnd < 0) throw new BusinessException("Số tiền không hợp lệ.");
  }

  public Money plus(Money other) {
    return new Money(Math.addExact(vnd, other.vnd));
  }

  public Money times(int quantity) {
    if (quantity < 1 || quantity > 99) throw new BusinessException("Số lượng phải từ 1 đến 99.");
    return new Money(Math.multiplyExact(vnd, quantity));
  }

  public String display() {
    return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(vnd) + " ₫";
  }
}
