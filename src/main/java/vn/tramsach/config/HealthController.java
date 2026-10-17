package vn.tramsach.config;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController {
  @GetMapping("/health")
  Map<String, String> health() {
    return Map.of("status", "UP", "application", "Tram Sach", "runtime", "Java 21 / Spring Boot");
  }
}
