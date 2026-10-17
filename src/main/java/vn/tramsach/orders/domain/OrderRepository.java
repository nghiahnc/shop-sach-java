package vn.tramsach.orders.domain;

import java.util.*;

public interface OrderRepository {
  Optional<Order> find(UUID id);

  Optional<Order> byCheckout(UUID key);

  Order lock(UUID id);

  void save(Order order);

  List<Order> all();

  List<UUID> pending();
}
