package vn.tramsach.orders.domain;

import vn.tramsach.shared.domain.BusinessException;

public record Delivery(String name, String phone, String address) {
  public Delivery {
    if (name == null
        || name.isBlank()
        || name.length() > 100
        || phone == null
        || !phone.matches("[0-9+ ()-]{9,20}")
        || address == null
        || address.trim().length() < 10
        || address.length() > 500)
      throw new BusinessException("Kiểm tra tên, số điện thoại và địa chỉ (ít nhất 10 ký tự).");
  }
}
