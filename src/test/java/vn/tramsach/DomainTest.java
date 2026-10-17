package vn.tramsach;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import vn.tramsach.cart.domain.*;
import vn.tramsach.orders.domain.*;
import vn.tramsach.orders.domain.Order;
import vn.tramsach.promotions.domain.*;
import vn.tramsach.shared.domain.*;

class DomainTest {
  @Test
  void integerMoney() {
    assertEquals(384000, new Money(128000).times(3).vnd());
    assertEquals(400000, new Money(384000).plus(new Money(16000)).vnd());
  }

  @Test
  void negativeMoney() {
    assertThrows(BusinessException.class, () -> new Money(-1));
  }

  @Test
  void overflowFails() {
    assertThrows(ArithmeticException.class, () -> new Money(Long.MAX_VALUE).plus(new Money(1)));
  }

  @Test
  void cartEncapsulates() {
    var c = new Cart();
    var id = UUID.randomUUID();
    c.add(id, 2);
    c.add(id, 1);
    assertEquals(3, c.count());
    assertThrows(UnsupportedOperationException.class, () -> c.items().clear());
  }

  @Test
  void cartInvalidQuantity() {
    var c = new Cart();
    var id = UUID.randomUUID();
    assertThrows(BusinessException.class, () -> c.add(id, -1));
    assertThrows(BusinessException.class, () -> c.set(id, 0));
    assertThrows(BusinessException.class, () -> c.set(id, 100));
  }

  @Test
  void cartRemove() {
    var c = new Cart();
    var id = UUID.randomUUID();
    c.add(id, 1);
    c.remove(id);
    assertTrue(c.items().isEmpty());
  }

  @Test
  void percentagePolymorphism() {
    DiscountPolicy p = new PercentageDiscountPolicy(100000, 10);
    assertEquals(12800, p.apply(new Money(128000)).vnd());
  }

  @Test
  void fixedDiscountCapped() {
    DiscountPolicy p = new FixedAmountDiscountPolicy(0, 20000);
    assertEquals(10000, p.apply(new Money(10000)).vnd());
  }

  @Test
  void minimumDiscount() {
    assertThrows(
        BusinessException.class,
        () -> new PercentageDiscountPolicy(100000, 10).apply(new Money(99000)));
  }

  @Test
  void invalidPercentage() {
    assertThrows(BusinessException.class, () -> new PercentageDiscountPolicy(0, 101));
  }

  @Test
  void shippingThreshold() {
    ShippingPolicy p = new StandardShippingPolicy();
    assertEquals(20000, p.fee(new Money(299999)).vnd());
    assertEquals(0, p.fee(new Money(300000)).vnd());
  }

  private Order order(String method) {
    return new Order(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "TS-TEST",
        method,
        new Delivery("Bạn đọc", "0901234567", "123 Đường Sách, Cần Thơ"),
        List.of(new OrderLine(UUID.randomUUID(), "Sách", new Money(128000), 2)),
        new Money(25600),
        new Money(20000),
        Instant.now(),
        Instant.now().plusSeconds(1800),
        "COD".equals(method) ? OrderStatus.CONFIRMED : OrderStatus.PENDING_PAYMENT,
        false,
        false,
        null,
        null,
        null,
        0);
  }

  @Test
  void orderSnapshotsAndTotals() {
    var o = order("COD");
    assertEquals(250400, o.total().vnd());
    assertThrows(UnsupportedOperationException.class, () -> o.lines().clear());
  }

  @Test
  void emptyOrder() {
    assertThrows(
        BusinessException.class,
        () ->
            new Order(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "TS",
                "COD",
                new Delivery("An", "0901234567", "123 Đường Sách"),
                List.of(),
                new Money(0),
                new Money(0),
                Instant.now(),
                Instant.now(),
                OrderStatus.CONFIRMED,
                false,
                false,
                null,
                null,
                null,
                0));
  }

  @Test
  void codRevenueAfterCompletion() {
    var o = order("COD");
    assertFalse(o.paid());
    assertThrows(BusinessException.class, o::complete);
    o.ship();
    o.complete();
    assertTrue(o.paid());
    assertEquals(OrderStatus.COMPLETED, o.status());
  }

  @Test
  void cancellationRestoresOnce() {
    var o = order("COD");
    o.cancel();
    assertTrue(o.releaseStock());
    assertFalse(o.releaseStock());
    assertThrows(BusinessException.class, o::ship);
  }

  @Test
  void cardFailureDoesNotPayOrShip() {
    var o = order("STRIPE");
    assertFalse(o.paid());
    assertThrows(BusinessException.class, o::ship);
    assertEquals(OrderStatus.PENDING_PAYMENT, o.status());
  }

  @Test
  void duplicateCardSuccessIsIdempotent() {
    var o = order("STRIPE");
    o.markPaid();
    o.markPaid();
    assertTrue(o.paid());
    assertEquals(OrderStatus.CONFIRMED, o.status());
    assertThrows(BusinessException.class, o::cancel);
  }

  @Test
  void expireRestoresOnce() {
    var o = order("STRIPE");
    o.expire();
    assertTrue(o.releaseStock());
    assertFalse(o.releaseStock());
    assertThrows(BusinessException.class, o::markPaid);
  }

  @Test
  void invalidDelivery() {
    assertThrows(BusinessException.class, () -> new Delivery("An", "abc", "x"));
  }
}
