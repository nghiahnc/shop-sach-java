package vn.tramsach.config;

import org.springframework.context.annotation.*;
import vn.tramsach.cart.domain.*;

@Configuration
class DomainConfiguration {
  @Bean
  ShippingPolicy shippingPolicy() {
    return new StandardShippingPolicy();
  }
}
