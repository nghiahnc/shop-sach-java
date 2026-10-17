package vn.tramsach.orders.domain;

import java.util.UUID;
import vn.tramsach.shared.domain.*;

public record OrderLine(UUID bookId, String title, Money unitPrice, int quantity) {
  public OrderLine {
    if (bookId == null || title == null || quantity < 1 || quantity > 99)
      throw new BusinessException("Dòng đơn hàng không hợp lệ.");
  }

  public Money total() {
    return unitPrice.times(quantity);
  }
}
