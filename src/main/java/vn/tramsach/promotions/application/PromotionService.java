package vn.tramsach.promotions.application;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tramsach.promotions.domain.*;
import vn.tramsach.shared.domain.*;

@Service
@Transactional(readOnly = true)
public class PromotionService {
  private final CouponRepository repository;

  public PromotionService(CouponRepository repository) {
    this.repository = repository;
  }

  public Money discount(String code, Money subtotal) {
    if (code == null || code.isBlank()) return new Money(0);
    var c =
        repository
            .find(code.trim().toUpperCase(Locale.ROOT))
            .orElseThrow(() -> new BusinessException("Mã giảm giá không tồn tại."));
    if (!c.active() || (c.expiresAt() != null && !c.expiresAt().isAfter(Instant.now())))
      throw new BusinessException("Mã giảm giá đã hết hạn hoặc tạm ngừng.");
    return c.policy().apply(subtotal);
  }

  public List<Coupon> all() {
    return repository.all();
  }

  @Transactional
  public void save(
      String code, String kind, long value, long minimum, boolean active, Instant expires) {
    code = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    if (!code.matches("[A-Z0-9]{3,24}")
        || !("PERCENT".equals(kind) || "FIXED".equals(kind))
        || value < 1
        || value > 100000000
        || minimum < 0
        || minimum > 100000000
        || ("PERCENT".equals(kind) && value > 100))
      throw new BusinessException("Thông tin mã giảm giá không hợp lệ.");
    repository.save(new Coupon(code, kind, value, minimum, active, expires));
  }
}
