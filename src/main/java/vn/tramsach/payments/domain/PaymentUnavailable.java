package vn.tramsach.payments.domain;

public class PaymentUnavailable extends RuntimeException {
  private final boolean definitelyNotCreated;

  public PaymentUnavailable(boolean definitelyNotCreated) {
    super("Không kết nối được cổng thanh toán. Đơn và giỏ hàng vẫn được giữ; hãy thử lại.");
    this.definitelyNotCreated = definitelyNotCreated;
  }

  public boolean definitelyNotCreated() {
    return definitelyNotCreated;
  }
}
