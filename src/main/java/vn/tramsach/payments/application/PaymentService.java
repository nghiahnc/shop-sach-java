package vn.tramsach.payments.application;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.tramsach.orders.application.OrderService;
import vn.tramsach.orders.domain.*;
import vn.tramsach.payments.domain.*;

@Service
public class PaymentService {
  private final List<PaymentGateway> gateways;
  private final OrderService orders;

  public PaymentService(List<PaymentGateway> gateways, OrderService orders) {
    this.gateways = List.copyOf(gateways);
    this.orders = orders;
  }

  private PaymentGateway gateway(String method) {
    return gateways.stream().filter(g -> method.equals(g.method())).findFirst().orElseThrow();
  }

  private RemotePaymentGateway remote() {
    return (RemotePaymentGateway) gateway("STRIPE");
  }

  public Order start(UUID id) {
    var o = orders.get(id);
    if (o.status() != OrderStatus.PENDING_PAYMENT
        && !("COD".equals(o.method()) && o.sessionId() == null)) return o;
    if (o.sessionId() != null) return o;
    var p =
        gateway(o.method())
            .create(
                new PaymentGateway.Request(
                    o.id(), o.code(), o.total().vnd(), o.expiresAt(), o.memberId()));
    return orders.attach(o.id(), p.sessionId(), p.url(), p.currency(), p.amount());
  }

  public void reconcile(UUID id) {
    var o = orders.get(id);
    if (o.status() != OrderStatus.PENDING_PAYMENT) return;
    if (o.sessionId() == null) {
      try {
        o = start(id);
      } catch (PaymentUnavailable e) {
        if (e.definitelyNotCreated() && Instant.now().isAfter(o.expiresAt())) orders.expire(id);
        return;
      }
    }
    if (o.sessionId() == null) return;
    var p = remote().inspect(o.sessionId());
    if (p.paid()) orders.paid(o.id(), p.sessionId(), p.currency(), p.amount());
    else if ("expired".equals(p.status())) orders.expire(o.id(), p.sessionId());
  }

  public void reconcilePending() {
    for (var id : orders.pending())
      try {
        reconcile(id);
      } catch (PaymentUnavailable ignored) {
        /* Next visit/webhook retries; never release ambiguous payments. */
      }
  }

  public PaymentGateway.Snapshot inspect(String session) {
    return remote().inspect(session);
  }
}
