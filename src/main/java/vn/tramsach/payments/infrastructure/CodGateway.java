package vn.tramsach.payments.infrastructure;

import org.springframework.stereotype.Component;
import vn.tramsach.payments.domain.PaymentGateway;

@Component
public class CodGateway implements PaymentGateway {
  public String method() {
    return "COD";
  }

  public Checkout create(Request r) {
    return new Checkout(null, "/account/orders/" + r.orderId(), "vnd", r.totalVnd());
  }
}
