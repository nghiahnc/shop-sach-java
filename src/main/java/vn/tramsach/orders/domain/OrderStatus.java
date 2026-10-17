package vn.tramsach.orders.domain;

public enum OrderStatus {
  PENDING_PAYMENT("Chờ thanh toán"),
  CONFIRMED("Đã xác nhận"),
  SHIPPING("Đang giao"),
  COMPLETED("Hoàn tất"),
  CANCELLED("Đã hủy"),
  EXPIRED("Hết hạn thanh toán");
  private final String label;

  OrderStatus(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
