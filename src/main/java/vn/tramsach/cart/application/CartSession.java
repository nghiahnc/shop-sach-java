package vn.tramsach.cart.application;

import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.tramsach.cart.domain.Cart;

@Component
public class CartSession {
  public Cart get(HttpSession session) {
    var old = (Cart) session.getAttribute("cart");
    var cart = new Cart();
    if (old != null) {
      old.items().forEach(cart::set);
      cart.coupon(old.coupon());
    }
    return cart;
  }

  public void save(HttpSession session, Cart cart) {
    session.setAttribute("cart", cart);
    session.setAttribute("checkoutKey", UUID.randomUUID());
  }

  public UUID key(HttpSession session) {
    var key = (UUID) session.getAttribute("checkoutKey");
    if (key == null) {
      key = UUID.randomUUID();
      session.setAttribute("checkoutKey", key);
    }
    return key;
  }

  public void clear(HttpSession session) {
    save(session, new Cart());
    session.removeAttribute("lastOrder");
  }
}
