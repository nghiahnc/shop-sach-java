package vn.tramsach.payments.infrastructure;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.net.RequestOptions;
import com.stripe.param.*;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import vn.tramsach.demo.application.DemoDefaults;
import vn.tramsach.payments.domain.*;

@Component
public class StripeDemoCardProvisioner implements DemoCardProvisioner {
  private final Environment env;
  private final DemoDefaults demo;

  public StripeDemoCardProvisioner(Environment env, DemoDefaults demo) {
    this.env = env;
    this.demo = demo;
  }

  private RequestOptions options(UUID id, String operation) {
    String key = env.getProperty("STRIPE_SECRET_KEY", "");
    if (!demo.enabled() || !key.startsWith("sk_test_")) throw new PaymentUnavailable(false);
    return RequestOptions.builder()
        .setApiKey(key)
        .setIdempotencyKey("tram-sach-demo-v1-" + operation + "-" + id)
        .setMaxNetworkRetries(2)
        .build();
  }

  public SavedDemoCard provision(UUID buyerId) {
    String email = "reader-" + buyerId + "@tramsach.demo";
    try {
      var customer =
          Customer.create(
              CustomerCreateParams.builder()
                  .setName(DemoDefaults.NAME + " · Demo")
                  .setEmail(email)
                  .putMetadata("shop", "tram-sach-java")
                  .putMetadata("demo_buyer", buyerId.toString())
                  .build(),
              options(buyerId, "customer"));
      var billing =
          PaymentMethodCreateParams.BillingDetails.builder()
              .setName(DemoDefaults.NAME + " · Demo")
              .setEmail(email)
              .setPhone("+84901234567")
              .setAddress(
                  PaymentMethodCreateParams.BillingDetails.Address.builder()
                      .setCountry("VN")
                      .setCity("Can Tho")
                      .setLine1("123 Duong Sach - DEMO")
                      .build())
              .build();
      var method =
          PaymentMethod.create(
              PaymentMethodCreateParams.builder()
                  .setType(PaymentMethodCreateParams.Type.CARD)
                  .setCard(PaymentMethodCreateParams.Token.builder().setToken("tok_visa").build())
                  .setAllowRedisplay(PaymentMethodCreateParams.AllowRedisplay.ALWAYS)
                  .setBillingDetails(billing)
                  .build(),
              options(buyerId, "method"));
      method.attach(
          PaymentMethodAttachParams.builder().setCustomer(customer.getId()).build(),
          options(buyerId, "attach"));
      return new SavedDemoCard(buyerId, customer.getId(), method.getId());
    } catch (StripeException e) {
      throw new PaymentUnavailable(false);
    }
  }
}
