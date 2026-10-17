package vn.tramsach.config;

import java.time.*;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import vn.tramsach.shared.domain.Money;

@Component("ui")
public class Ui {
  public String money(long value) {
    return new Money(value).display();
  }

  public String time(Instant time) {
    return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
        .format(time);
  }

  public String charged(String currency, long amount) {
    return "usd".equals(currency)
        ? String.format(java.util.Locale.US, "%.2f USD", amount / 100.0)
        : money(amount);
  }
}
