package vn.tramsach.promotions.domain;

import java.util.*;

public interface CouponRepository {
  Optional<Coupon> find(String code);

  List<Coupon> all();

  void save(Coupon coupon);
}
