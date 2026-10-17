package vn.tramsach.payments.domain;

/** Only online gateways expose provider state; manual COD does not need this capability. */
public interface RemotePaymentGateway extends PaymentGateway {
  Snapshot inspect(String sessionId);
}
