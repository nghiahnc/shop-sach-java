package vn.tramsach.orders.domain;

import java.time.Instant;
import java.util.*;
import vn.tramsach.shared.domain.*;

public final class Order {
  private final UUID id, memberId, checkoutKey;
  private final String code, method;
  private final Delivery delivery;
  private final List<OrderLine> lines;
  private final Money subtotal, discount, shipping, total;
  private final Instant createdAt, expiresAt;
  private OrderStatus status;
  private boolean paid, stockReleased;
  private String sessionId, paymentUrl, chargedCurrency;
  private long chargedAmount;

  public Order(
      UUID id,
      UUID memberId,
      UUID checkoutKey,
      String code,
      String method,
      Delivery delivery,
      List<OrderLine> lines,
      Money discount,
      Money shipping,
      Instant createdAt,
      Instant expiresAt,
      OrderStatus status,
      boolean paid,
      boolean stockReleased,
      String sessionId,
      String paymentUrl,
      String chargedCurrency,
      long chargedAmount) {
    if (lines == null || lines.isEmpty()) throw new BusinessException("Giỏ hàng đang trống.");
    if (!Set.of("COD", "STRIPE").contains(method))
      throw new BusinessException("Phương thức thanh toán không hợp lệ.");
    this.id = id;
    this.memberId = memberId;
    this.checkoutKey = checkoutKey;
    this.code = code;
    this.method = method;
    this.delivery = delivery;
    this.lines = List.copyOf(lines);
    this.subtotal = lines.stream().map(OrderLine::total).reduce(new Money(0), Money::plus);
    if (discount.vnd() > subtotal.vnd()) throw new BusinessException("Số tiền giảm không hợp lệ.");
    this.discount = discount;
    this.shipping = shipping;
    this.total = new Money(subtotal.vnd() - discount.vnd()).plus(shipping);
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.status = status;
    this.paid = paid;
    this.stockReleased = stockReleased;
    this.sessionId = sessionId;
    this.paymentUrl = paymentUrl;
    this.chargedCurrency = chargedCurrency;
    this.chargedAmount = chargedAmount;
  }

  public void attachPayment(String sessionId, String paymentUrl, String currency, long amount) {
    if (this.sessionId != null && !this.sessionId.equals(sessionId))
      throw new BusinessException("Phiên thanh toán không khớp.");
    this.sessionId = sessionId;
    this.paymentUrl = paymentUrl;
    this.chargedCurrency = currency;
    this.chargedAmount = amount;
  }

  public void markPaid() {
    if (paid) return;
    if (status != OrderStatus.PENDING_PAYMENT || stockReleased)
      throw new BusinessException("Đơn không thể nhận thanh toán ở trạng thái hiện tại.");
    paid = true;
    status = OrderStatus.CONFIRMED;
  }

  public void ship() {
    if (status != OrderStatus.CONFIRMED || ("STRIPE".equals(method) && !paid))
      throw new BusinessException("Đơn chưa đủ điều kiện giao hàng.");
    status = OrderStatus.SHIPPING;
  }

  public void complete() {
    if (status != OrderStatus.SHIPPING) throw new BusinessException("Chỉ hoàn tất đơn đang giao.");
    status = OrderStatus.COMPLETED;
    paid = true;
  }

  public void cancel() {
    if (paid || status != OrderStatus.CONFIRMED || !"COD".equals(method))
      throw new BusinessException("Chỉ hủy đơn COD chưa giao.");
    status = OrderStatus.CANCELLED;
  }

  public void expire() {
    if (paid || status != OrderStatus.PENDING_PAYMENT)
      throw new BusinessException("Đơn không thể hết hạn.");
    status = OrderStatus.EXPIRED;
  }

  public boolean releaseStock() {
    if (stockReleased) return false;
    if (status != OrderStatus.CANCELLED && status != OrderStatus.EXPIRED)
      throw new BusinessException("Chưa được hoàn tồn.");
    stockReleased = true;
    return true;
  }

  public UUID id() {
    return id;
  }

  public UUID memberId() {
    return memberId;
  }

  public UUID checkoutKey() {
    return checkoutKey;
  }

  public String code() {
    return code;
  }

  public String method() {
    return method;
  }

  public Delivery delivery() {
    return delivery;
  }

  public List<OrderLine> lines() {
    return lines;
  }

  public Money subtotal() {
    return subtotal;
  }

  public Money discount() {
    return discount;
  }

  public Money shipping() {
    return shipping;
  }

  public Money total() {
    return total;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public OrderStatus status() {
    return status;
  }

  public boolean paid() {
    return paid;
  }

  public boolean stockReleased() {
    return stockReleased;
  }

  public String sessionId() {
    return sessionId;
  }

  public String paymentUrl() {
    return paymentUrl;
  }

  public String chargedCurrency() {
    return chargedCurrency;
  }

  public long chargedAmount() {
    return chargedAmount;
  }
}
