package vn.tramsach;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import vn.tramsach.payments.domain.*;
import vn.tramsach.payments.infrastructure.StripeGateway;

class StripeRecoveryTest {
  @Test
  void oldAmbiguousSessionNeverCreatesNewCharge() {
    var gateway =
        new StripeGateway(
            new MockEnvironment(),
            new vn.tramsach.demo.application.DemoDefaults(new MockEnvironment()),
            org.mockito.Mockito.mock(vn.tramsach.payments.application.DemoCardService.class));
    var error =
        assertThrows(
            PaymentUnavailable.class,
            () ->
                gateway.create(
                    new PaymentGateway.Request(
                        UUID.randomUUID(), "TS-OLD", 128000, Instant.now().minusSeconds(86400))));
    assertFalse(
        error.definitelyNotCreated(),
        "Retain stock for manual reconciliation after idempotency retention window");
  }

  @Test
  void missingKeyCannotBeUsedAsEvidenceToReleaseStock() {
    var gateway =
        new StripeGateway(
            new MockEnvironment(),
            new vn.tramsach.demo.application.DemoDefaults(new MockEnvironment()),
            org.mockito.Mockito.mock(vn.tramsach.payments.application.DemoCardService.class));
    var error =
        assertThrows(
            PaymentUnavailable.class,
            () ->
                gateway.create(
                    new PaymentGateway.Request(
                        UUID.randomUUID(), "TS-NEW", 128000, Instant.now().plusSeconds(1810))));
    assertFalse(error.definitelyNotCreated());
  }
}
