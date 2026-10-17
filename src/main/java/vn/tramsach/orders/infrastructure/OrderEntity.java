package vn.tramsach.orders.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "orders")
public class OrderEntity {
  @Id public UUID id;

  @Column(name = "member_id")
  public UUID memberId;

  @Column(name = "checkout_key")
  public UUID checkoutKey;

  public String code;
  public String method;
  public String name;
  public String phone;
  public String address;
  public long discount;
  public long shipping;
  public long total;

  @Column(name = "created_at")
  public Instant createdAt;

  @Column(name = "expires_at")
  public Instant expiresAt;

  public String status;
  public boolean paid;

  @Column(name = "stock_released")
  public boolean stockReleased;

  @Column(name = "session_id")
  public String sessionId;

  @Column(name = "payment_url", columnDefinition = "text")
  public String paymentUrl;

  @Column(name = "charged_currency")
  public String chargedCurrency;

  @Column(name = "charged_amount")
  public long chargedAmount;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "order_lines", joinColumns = @JoinColumn(name = "order_id"))
  @OrderColumn(name = "line_number")
  public List<LineValue> lines = new ArrayList<>();

  @Embeddable
  public static class LineValue {
    @Column(name = "book_id")
    public UUID bookId;

    public String title;

    @Column(name = "unit_price")
    public long unitPrice;

    public int quantity;
  }
}
