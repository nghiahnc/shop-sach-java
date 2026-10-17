package vn.tramsach.payments.infrastructure;

import com.stripe.exception.*;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import vn.tramsach.demo.application.DemoDefaults;
import vn.tramsach.payments.application.DemoCardService;
import vn.tramsach.payments.domain.*;

@Component
public class StripeGateway implements RemotePaymentGateway {
  private final Environment env;
  private final DemoDefaults demo;
  private final DemoCardService cards;

  public StripeGateway(Environment env, DemoDefaults demo, DemoCardService cards) {
    this.env = env;
    this.demo = demo;
    this.cards = cards;
  }

  public String method() {
    return "STRIPE";
  }

  private RequestOptions options(String key) {
    String secret = env.getProperty("STRIPE_SECRET_KEY", "");
    if (!secret.startsWith("sk_test_")) throw new PaymentUnavailable(false);
    return RequestOptions.builder()
        .setApiKey(secret)
        .setIdempotencyKey(key)
        .setMaxNetworkRetries(2)
        .build();
  }

  public Checkout create(Request r) {
    // Stripe may prune idempotency keys after 24h. Never recreate an ambiguous
    // orphan beyond that window; retain its reservation for manual reconciliation.
    if (java.time.Instant.now().isAfter(r.expiresAt().minusSeconds(1860).plusSeconds(23 * 3600)))
      throw new PaymentUnavailable(false);
    String currency = env.getProperty("STRIPE_CURRENCY", "vnd").toLowerCase(java.util.Locale.ROOT);
    if (!java.util.Set.of("vnd", "usd").contains(currency)) throw new PaymentUnavailable(false);
    long amount = "usd".equals(currency) ? (r.totalVnd() * 100 + 25999) / 26000 : r.totalVnd();
    String origin = env.getProperty("APP_BASE_URL", "http://localhost:8080");
    var price =
        SessionCreateParams.LineItem.PriceData.builder()
            .setCurrency(currency)
            .setUnitAmount(amount)
            .setProductData(
                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                    .setName("Trạm Sách · " + r.code() + " · SANDBOX")
                    .build())
            .build();
    var builder =
        SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
            .setClientReferenceId(r.orderId().toString())
            .putMetadata("shop", "tram-sach-java")
            .putMetadata("order_id", r.orderId().toString())
            .setSuccessUrl(origin + "/account/orders/" + r.orderId() + "?payment=return")
            .setCancelUrl(origin + "/account/orders/" + r.orderId() + "?payment=cancel")
            .setExpiresAt(r.expiresAt().getEpochSecond())
            .addLineItem(
                SessionCreateParams.LineItem.builder().setQuantity(1L).setPriceData(price).build());
    if (demo.enabled() && r.buyerId() != null) {
      options(null); // Validate test-only credentials before provisioning any fixture.
      var card = cards.forBuyer(r.buyerId());
      builder
          .setCustomer(card.customerId())
          .setSavedPaymentMethodOptions(
              SessionCreateParams.SavedPaymentMethodOptions.builder()
                  .addAllowRedisplayFilter(
                      SessionCreateParams.SavedPaymentMethodOptions.AllowRedisplayFilter.ALWAYS)
                  .build());
    }
    var params = builder.build();
    try {
      var s = Session.create(params, options("tram-sach-" + r.orderId()));
      return new Checkout(s.getId(), s.getUrl(), s.getCurrency(), s.getAmountTotal());
    } catch (StripeException e) {
      throw new PaymentUnavailable(
          e instanceof InvalidRequestException invalid && "expires_at".equals(invalid.getParam()));
    }
  }

  public Snapshot inspect(String id) {
    try {
      var s = Session.retrieve(id, options(null));
      if (Boolean.TRUE.equals(s.getLivemode())) throw new PaymentUnavailable(false);
      return new Snapshot(
          s.getId(),
          s.getStatus(),
          "paid".equals(s.getPaymentStatus()),
          s.getCurrency(),
          s.getAmountTotal());
    } catch (StripeException e) {
      throw new PaymentUnavailable(false);
    }
  }
}
