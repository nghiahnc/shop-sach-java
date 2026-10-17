package vn.tramsach.payments.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.tramsach.payments.application.*;
import vn.tramsach.shared.domain.BusinessException;

@RestController
public class StripeWebhookController {
  private final Environment env;
  private final ObjectMapper json;
  private final PaymentService payments;
  private final PaymentEventService events;

  public StripeWebhookController(
      Environment env, ObjectMapper json, PaymentService payments, PaymentEventService events) {
    this.env = env;
    this.json = json;
    this.payments = payments;
    this.events = events;
  }

  @PostMapping("/payments/stripe/webhook")
  public ResponseEntity<Map<String, Object>> webhook(
      @RequestBody String body,
      @RequestHeader(value = "Stripe-Signature", defaultValue = "") String signature)
      throws Exception {
    try {
      Webhook.constructEvent(body, signature, env.getProperty("STRIPE_WEBHOOK_SECRET", ""));
    } catch (SignatureVerificationException | IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("received", false));
    }
    var event = json.readTree(body);
    String type = event.path("type").asText();
    if (event.path("livemode").asBoolean())
      return ResponseEntity.badRequest().body(Map.of("received", false));
    if (!Set.of(
            "checkout.session.completed",
            "checkout.session.expired",
            "checkout.session.async_payment_succeeded",
            "checkout.session.async_payment_failed")
        .contains(type)) return ResponseEntity.ok(Map.of("ignored", true));
    var session = event.path("data").path("object");
    String id = session.path("id").asText();
    String order = session.path("metadata").path("order_id").asText();
    if (!"tram-sach-java".equals(session.path("metadata").path("shop").asText()))
      return ResponseEntity.ok(Map.of("ignored", true));
    // Confirm current provider state outside the database transaction; out-of-order events are
    // safe.
    var snapshot = payments.inspect(id);
    try {
      return ResponseEntity.ok(
          Map.of(
              "received",
              true,
              "applied",
              events.apply(event.path("id").asText(), UUID.fromString(order), snapshot)));
    } catch (BusinessException e) {
      return ResponseEntity.status(409).body(Map.of("received", false));
    }
  }
}
