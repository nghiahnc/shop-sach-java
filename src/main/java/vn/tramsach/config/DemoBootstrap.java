package vn.tramsach.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import vn.tramsach.identity.application.IdentityService;

@Component
@Profile("!probe")
class DemoBootstrap implements ApplicationRunner {
  private final IdentityService identity;
  private final Environment env;
  private final vn.tramsach.demo.application.DemoDefaults demo;

  DemoBootstrap(
      IdentityService identity, Environment env, vn.tramsach.demo.application.DemoDefaults demo) {
    this.identity = identity;
    this.env = env;
    this.demo = demo;
  }

  public void run(org.springframework.boot.ApplicationArguments args) {
    if (demo.enabled()) identity.bootstrap(demo.email(), demo.name(), demo.password(), "CUSTOMER");
    identity.bootstrap(
        env.getProperty("ADMIN_EMAIL"),
        "Quản trị Trạm Sách",
        env.getProperty("ADMIN_PASSWORD"),
        "ADMIN");
    identity.bootstrap(
        env.getProperty("CUSTOMER_EMAIL"),
        "Bạn đọc demo",
        env.getProperty("CUSTOMER_PASSWORD"),
        "CUSTOMER");
  }
}
