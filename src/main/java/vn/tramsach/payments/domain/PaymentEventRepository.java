package vn.tramsach.payments.domain;

public interface PaymentEventRepository {
  boolean claim(String eventId, String sessionId);
}
