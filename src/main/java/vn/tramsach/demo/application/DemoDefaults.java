package vn.tramsach.demo.application;

import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Public, fictional fixture values. Never reads owner/admin credentials. */
@Component
public class DemoDefaults {
  public static final String EMAIL = "demo@tramsach.demo";
  public static final String PASSWORD = "TramSachDemo2026!";
  public static final String NAME = "Nguyễn Minh An";
  public static final String PHONE = "0901234567";
  public static final String ADDRESS = "123 Đường Sách, Ninh Kiều, Cần Thơ (demo)";
  private final boolean enabled;

  public DemoDefaults(Environment env) {
    enabled = env.getProperty("DEMO_MODE", Boolean.class, false);
  }

  public boolean enabled() {
    return enabled;
  }

  public String email() {
    return EMAIL;
  }

  public String password() {
    return PASSWORD;
  }

  public String name() {
    return NAME;
  }

  public String phone() {
    return PHONE;
  }

  public String address() {
    return ADDRESS;
  }

  public String registrationEmail() {
    return "reader-" + UUID.randomUUID().toString().replace("-", "") + "@tramsach.demo";
  }
}
