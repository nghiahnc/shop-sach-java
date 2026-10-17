package vn.tramsach.payments.domain;

import java.time.Instant;
import java.util.UUID;

public interface PaymentGateway {
  String method();

  Checkout create(Request request);

  record Request(UUID orderId, String code, long totalVnd, Instant expiresAt, UUID buyerId) {
    public Request(UUID orderId, String code, long totalVnd, Instant expiresAt) {
      this(orderId, code, totalVnd, expiresAt, null);
    }
  }

  record Checkout(String sessionId, String url, String currency, long amount) {}

  record Snapshot(String sessionId, String status, boolean paid, String currency, long amount) {}
}
