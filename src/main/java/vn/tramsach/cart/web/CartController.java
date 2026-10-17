package vn.tramsach.cart.web;

import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.tramsach.cart.application.CartPricingService;
import vn.tramsach.cart.application.CartSession;
import vn.tramsach.catalog.application.CatalogService;
import vn.tramsach.shared.domain.BusinessException;

@Controller
public class CartController {
  private final CartSession carts;
  private final CatalogService catalog;
  private final CartPricingService pricing;

  public CartController(CartSession carts, CatalogService catalog, CartPricingService pricing) {
    this.carts = carts;
    this.catalog = catalog;
    this.pricing = pricing;
  }

  @GetMapping("/cart")
  String cart(HttpSession session, Model model) {
    var cart = carts.get(session);
    model.addAttribute("cart", cart);
    model.addAttribute("couponInput", cart.coupon().isBlank() ? "DOCSACH10" : cart.coupon());
    model.addAttribute(
        "lines",
        cart.items().entrySet().stream()
            .map(
                e ->
                    new CartPricingService.Line(
                        catalog.get(e.getKey()),
                        e.getValue(),
                        catalog.get(e.getKey()).price().times(e.getValue())))
            .toList());
    try {
      model.addAttribute("quote", pricing.quote(cart));
    } catch (BusinessException e) {
      model.addAttribute("error", e.getMessage());
    }
    return "cart";
  }

  @PostMapping("/cart/add")
  String add(
      @RequestParam UUID bookId,
      @RequestParam(defaultValue = "1") int quantity,
      HttpSession session,
      RedirectAttributes flash) {
    if (quantity < 1) throw new BusinessException("Số lượng phải từ 1 đến 99.");
    var cart = carts.get(session);
    cart.add(bookId, quantity);
    pricing.quote(cart);
    carts.save(session, cart);
    flash.addFlashAttribute("notice", "Đã thêm sách vào giỏ.");
    return "redirect:/cart";
  }

  @PostMapping("/cart/update")
  String update(@RequestParam UUID bookId, @RequestParam int quantity, HttpSession session) {
    var cart = carts.get(session);
    cart.set(bookId, quantity);
    if (quantity > catalog.get(bookId).stock())
      throw new BusinessException("Số lượng vượt tồn kho.");
    carts.save(session, cart);
    return "redirect:/cart";
  }

  @PostMapping("/cart/remove")
  String remove(@RequestParam UUID bookId, HttpSession session) {
    var cart = carts.get(session);
    cart.remove(bookId);
    if (cart.items().isEmpty()) cart.coupon("");
    carts.save(session, cart);
    return "redirect:/cart";
  }

  @PostMapping("/cart/coupon")
  String coupon(
      @RequestParam(defaultValue = "") String code, HttpSession session, RedirectAttributes flash) {
    var cart = carts.get(session);
    cart.coupon(code);
    try {
      pricing.quote(cart);
      carts.save(session, cart);
      flash.addFlashAttribute(
          "notice", code.isBlank() ? "Đã bỏ mã giảm giá." : "Đã áp dụng mã giảm giá.");
    } catch (BusinessException e) {
      flash.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/cart";
  }
}
