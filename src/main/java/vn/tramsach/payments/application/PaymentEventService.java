package vn.tramsach.payments.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tramsach.orders.application.OrderService;
import vn.tramsach.payments.domain.*;

@Service
public class PaymentEventService {
  private final PaymentEventRepository events;
  private final OrderService orders;

  public PaymentEventService(PaymentEventRepository events, OrderService orders) {
    this.events = events;
    this.orders = orders;
  }

  @Transactional
  public boolean apply(String eventId, UUID orderId, PaymentGateway.Snapshot p) {
    if (!events.claim(eventId, p.sessionId())) return false;
    if (p.paid()) orders.paid(orderId, p.sessionId(), p.currency(), p.amount());
    else if ("expired".equals(p.status())) orders.expire(orderId, p.sessionId());
    return true;
  }
}
