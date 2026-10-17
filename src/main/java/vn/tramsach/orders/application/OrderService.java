package vn.tramsach.orders.application;

import java.time.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tramsach.cart.application.CartPricingService;
import vn.tramsach.cart.domain.Cart;
import vn.tramsach.catalog.application.CatalogService;
import vn.tramsach.identity.application.IdentityService;
import vn.tramsach.orders.domain.*;
import vn.tramsach.shared.domain.*;

@Service
@Transactional(readOnly = true)
public class OrderService {
  private final OrderRepository repository;
  private final CatalogService catalog;
  private final CartPricingService pricing;
  private final IdentityService identity;

  public OrderService(
      OrderRepository repository,
      CatalogService catalog,
      CartPricingService pricing,
      IdentityService identity) {
    this.repository = repository;
    this.catalog = catalog;
    this.pricing = pricing;
    this.identity = identity;
  }

  @Transactional
  public Order place(String email, UUID key, Cart cart, Delivery delivery, String method) {
    var member = identity.lock(email);
    var old = repository.byCheckout(key);
    if (old.isPresent()) {
      if (!old.get().memberId().equals(member.id()))
        throw new AccessDeniedException("Đơn không thuộc tài khoản.");
      return old.get();
    }
    if (cart.items().isEmpty()) throw new BusinessException("Giỏ hàng đang trống.");
    // Lock in a stable order; pricing is calculated from the same locked rows.
    cart.items().keySet().stream()
        .sorted()
        .forEach(id -> catalog.reserve(id, cart.items().get(id)));
    // Reserved stock is already deducted: use product snapshots without checking remaining stock
    // again.
    var lines =
        cart.items().entrySet().stream()
            .map(
                e -> {
                  var b = catalog.get(e.getKey());
                  return new OrderLine(b.id(), b.title(), b.price(), e.getValue());
                })
            .toList();
    var quote = pricingForReserved(cart, lines);
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    var order =
        new Order(
            id,
            member.id(),
            key,
            "TS-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT),
            method,
            delivery,
            lines,
            quote[0],
            quote[1],
            now,
            now.plusSeconds(1860),
            "COD".equals(method) ? OrderStatus.CONFIRMED : OrderStatus.PENDING_PAYMENT,
            false,
            false,
            null,
            null,
            null,
            0);
    repository.save(order);
    return order;
  }

  private Money[] pricingForReserved(Cart cart, List<OrderLine> lines) {
    return pricing.reservedTotals(
        cart, lines.stream().map(OrderLine::total).reduce(new Money(0), Money::plus));
  }

  public Order get(UUID id) {
    return repository.find(id).orElseThrow(() -> new BusinessException("Không tìm thấy đơn hàng."));
  }

  public Order owned(UUID id, String email) {
    var o = get(id);
    if (!o.memberId().equals(identity.get(email).id()))
      throw new AccessDeniedException("Đơn không thuộc tài khoản.");
    return o;
  }

  public List<Order> mine(String email) {
    var id = identity.get(email).id();
    return repository.all().stream().filter(o -> o.memberId().equals(id)).toList();
  }

  public List<Order> all() {
    return repository.all();
  }

  public List<UUID> pending() {
    return repository.pending();
  }

  @Transactional
  public Order attach(UUID id, String session, String url, String currency, long amount) {
    var o = repository.lock(id);
    o.attachPayment(session, url, currency, amount);
    repository.save(o);
    return o;
  }

  @Transactional
  public void paid(UUID id, String session, String currency, long amount) {
    var o = repository.lock(id);
    if (!Objects.equals(o.sessionId(), session)
        || !Objects.equals(o.chargedCurrency(), currency)
        || o.chargedAmount() != amount)
      throw new BusinessException("Thông tin thanh toán không khớp đơn.");
    o.markPaid();
    repository.save(o);
  }

  @Transactional
  public void expire(UUID id) {
    expire(id, null);
  }

  @Transactional
  public void expire(UUID id, String sessionId) {
    var o = repository.lock(id);
    if (!Objects.equals(o.sessionId(), sessionId))
      throw new BusinessException("Phiên hết hạn không khớp đơn hàng.");
    if (o.status() != OrderStatus.PENDING_PAYMENT) return;
    o.expire();
    restore(o);
    repository.save(o);
  }

  private void restore(Order o) {
    if (o.releaseStock())
      o.lines().stream()
          .sorted(Comparator.comparing(OrderLine::bookId))
          .forEach(l -> catalog.restore(l.bookId(), l.quantity()));
  }

  @Transactional
  public void transition(UUID id, String action) {
    var o = repository.lock(id);
    switch (action) {
      case "ship" -> o.ship();
      case "complete" -> o.complete();
      case "cancel" -> {
        if (o.status() == OrderStatus.CANCELLED) return;
        o.cancel();
        restore(o);
      }
      default -> throw new BusinessException("Thao tác không hợp lệ.");
    }
    repository.save(o);
  }

  @Transactional
  public void cancelOwned(UUID id, String email) {
    owned(id, email);
    transition(id, "cancel");
  }
}
