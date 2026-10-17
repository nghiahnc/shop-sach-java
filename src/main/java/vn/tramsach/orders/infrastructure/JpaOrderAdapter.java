package vn.tramsach.orders.infrastructure;

import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.tramsach.orders.domain.*;
import vn.tramsach.shared.domain.*;

@Repository
public class JpaOrderAdapter implements OrderRepository {
  @PersistenceContext private EntityManager em;

  private Order map(OrderEntity e) {
    return new Order(
        e.id,
        e.memberId,
        e.checkoutKey,
        e.code,
        e.method,
        new Delivery(e.name, e.phone, e.address),
        e.lines.stream()
            .map(l -> new OrderLine(l.bookId, l.title, new Money(l.unitPrice), l.quantity))
            .toList(),
        new Money(e.discount),
        new Money(e.shipping),
        e.createdAt,
        e.expiresAt,
        OrderStatus.valueOf(e.status),
        e.paid,
        e.stockReleased,
        e.sessionId,
        e.paymentUrl,
        e.chargedCurrency,
        e.chargedAmount);
  }

  public Optional<Order> find(UUID id) {
    return Optional.ofNullable(em.find(OrderEntity.class, id)).map(this::map);
  }

  public Optional<Order> byCheckout(UUID key) {
    return em.createQuery("from OrderEntity where checkoutKey=:key", OrderEntity.class)
        .setParameter("key", key)
        .getResultStream()
        .findFirst()
        .map(this::map);
  }

  public Order lock(UUID id) {
    // Lock the parent before loading its collection. Hibernate follow-on locking with
    // an eager collection can otherwise hydrate a stale state before acquiring the lock.
    var ids =
        em.createNativeQuery("SELECT id FROM orders WHERE id=:id FOR UPDATE")
            .setParameter("id", id)
            .getResultList();
    if (ids.isEmpty()) throw new BusinessException("Không tìm thấy đơn hàng.");
    var e = em.find(OrderEntity.class, id);
    em.refresh(e);
    return map(e);
  }

  public void save(Order o) {
    var e = em.find(OrderEntity.class, o.id());
    boolean fresh = e == null;
    if (fresh) {
      e = new OrderEntity();
      e.id = o.id();
    }
    e.memberId = o.memberId();
    e.checkoutKey = o.checkoutKey();
    e.code = o.code();
    e.method = o.method();
    e.name = o.delivery().name();
    e.phone = o.delivery().phone();
    e.address = o.delivery().address();
    e.discount = o.discount().vnd();
    e.shipping = o.shipping().vnd();
    e.total = o.total().vnd();
    e.createdAt = o.createdAt();
    e.expiresAt = o.expiresAt();
    e.status = o.status().name();
    e.paid = o.paid();
    e.stockReleased = o.stockReleased();
    e.sessionId = o.sessionId();
    e.paymentUrl = o.paymentUrl();
    e.chargedCurrency = o.chargedCurrency();
    e.chargedAmount = o.chargedAmount();
    if (e.lines.isEmpty())
      for (var l : o.lines()) {
        var v = new OrderEntity.LineValue();
        v.bookId = l.bookId();
        v.title = l.title();
        v.unitPrice = l.unitPrice().vnd();
        v.quantity = l.quantity();
        e.lines.add(v);
      }
    if (fresh) em.persist(e);
    em.flush();
  }

  public List<Order> all() {
    return em
        .createQuery("from OrderEntity order by createdAt desc", OrderEntity.class)
        .getResultList()
        .stream()
        .map(this::map)
        .toList();
  }

  public List<UUID> pending() {
    return em.createQuery(
            "select id from OrderEntity where status='PENDING_PAYMENT' order by createdAt",
            UUID.class)
        .setMaxResults(50)
        .getResultList();
  }
}
