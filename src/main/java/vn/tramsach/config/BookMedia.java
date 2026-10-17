package vn.tramsach.config;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component("media")
public class BookMedia {
  public String cover(int id) {
    return String.format(Locale.ROOT, "/images/book-%02d.webp", id);
  }
}
