package vn.tramsach.cart.domain;

import java.io.*;
import java.util.*;
import vn.tramsach.shared.domain.BusinessException;

public final class Cart implements Serializable {
  @Serial private static final long serialVersionUID = 1L;
  private final Map<UUID, Integer> items = new LinkedHashMap<>();
  private String coupon = "";

  public Map<UUID, Integer> items() {
    return Collections.unmodifiableMap(items);
  }

  public void add(UUID book, int quantity) {
    if (quantity < 1) throw new BusinessException("Số lượng phải từ 1 đến 99.");
    set(book, Math.addExact(items.getOrDefault(book, 0), quantity));
  }

  public void set(UUID book, int quantity) {
    if (book == null || quantity < 1 || quantity > 99)
      throw new BusinessException("Số lượng phải từ 1 đến 99.");
    if (!items.containsKey(book) && items.size() >= 30)
      throw new BusinessException("Giỏ tối đa 30 đầu sách.");
    items.put(book, quantity);
  }

  public void remove(UUID book) {
    items.remove(book);
  }

  public int count() {
    return items.values().stream().mapToInt(Integer::intValue).sum();
  }

  public String coupon() {
    return coupon;
  }

  public void coupon(String code) {
    if (code != null && code.length() > 24) throw new BusinessException("Mã giảm giá quá dài.");
    coupon = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
  }
}
