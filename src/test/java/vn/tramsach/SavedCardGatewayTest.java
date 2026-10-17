package vn.tramsach;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import vn.tramsach.demo.application.DemoDefaults;
import vn.tramsach.payments.application.DemoCardService;
import vn.tramsach.payments.domain.*;
import vn.tramsach.payments.infrastructure.StripeGateway;

class SavedCardGatewayTest {
  @Test
  void savedCustomerAndRedisplayAreSentWithoutRawCardData() throws Exception {
    var env =
        new MockEnvironment()
            .withProperty("DEMO_MODE", "true")
            .withProperty("STRIPE_SECRET_KEY", "sk_test_fixture");
    var cards = mock(DemoCardService.class);
    var buyer = UUID.randomUUID();
    when(cards.forBuyer(buyer)).thenReturn(new SavedDemoCard(buyer, "cus_fixture", "pm_fixture"));
    var result = new Session();
    result.setId("cs_test_fixture");
    result.setUrl("https://checkout.stripe.com/test");
    result.setCurrency("vnd");
    result.setAmountTotal(148000L);
    try (var sdk = mockStatic(Session.class)) {
      sdk.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
          .thenAnswer(
              call -> {
                var p = call.getArgument(0, SessionCreateParams.class);
                var o = call.getArgument(1, RequestOptions.class);
                assertEquals("cus_fixture", p.getCustomer());
                assertEquals(
                    "always",
                    p.getSavedPaymentMethodOptions()
                        .getAllowRedisplayFilters()
                        .getFirst()
                        .getValue());
                assertTrue(o.getIdempotencyKey().startsWith("tram-sach-"));
                assertFalse(p.toMap().toString().contains("4242424242424242"));
                return result;
              });
      var request =
          new PaymentGateway.Request(
              UUID.randomUUID(), "TS-TEST", 148000, Instant.now().plusSeconds(1860), buyer);
      assertEquals(
          "cs_test_fixture",
          new StripeGateway(env, new DemoDefaults(env), cards).create(request).sessionId());
      verify(cards).forBuyer(buyer);
    }
  }

  @Test
  void liveKeyCannotProvisionDemoCard() {
    var env =
        new MockEnvironment()
            .withProperty("DEMO_MODE", "true")
            .withProperty("STRIPE_SECRET_KEY", "sk_live_fixture");
    var cards = mock(DemoCardService.class);
    assertThrows(
        PaymentUnavailable.class,
        () ->
            new StripeGateway(env, new DemoDefaults(env), cards)
                .create(
                    new PaymentGateway.Request(
                        UUID.randomUUID(),
                        "TS",
                        100000,
                        Instant.now().plusSeconds(1860),
                        UUID.randomUUID())));
    verifyNoInteractions(cards);
  }
}
