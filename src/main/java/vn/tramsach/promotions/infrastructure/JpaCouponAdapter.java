package vn.tramsach.promotions.infrastructure;

import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.tramsach.promotions.domain.*;

@Repository
public class JpaCouponAdapter implements CouponRepository {
  @PersistenceContext private EntityManager em;

  private Coupon map(CouponEntity c) {
    return new Coupon(c.code, c.kind, c.value, c.minimum, c.active, c.expiresAt);
  }

  public Optional<Coupon> find(String code) {
    return Optional.ofNullable(em.find(CouponEntity.class, code)).map(this::map);
  }

  public List<Coupon> all() {
    return em
        .createQuery("from CouponEntity order by code", CouponEntity.class)
        .getResultList()
        .stream()
        .map(this::map)
        .toList();
  }

  public void save(Coupon c) {
    var e = new CouponEntity();
    e.code = c.code();
    e.kind = c.kind();
    e.value = c.value();
    e.minimum = c.minimum();
    e.active = c.active();
    e.expiresAt = c.expiresAt();
    em.merge(e);
  }
}
