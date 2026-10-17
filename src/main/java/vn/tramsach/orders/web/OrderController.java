package vn.tramsach.orders.web;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import vn.tramsach.cart.application.CartPricingService;
import vn.tramsach.cart.application.CartSession;
import vn.tramsach.orders.application.OrderService;
import vn.tramsach.orders.domain.*;
import vn.tramsach.payments.application.PaymentService;
import vn.tramsach.payments.domain.PaymentUnavailable;
import vn.tramsach.shared.domain.BusinessException;

@Controller
public class OrderController {
  private final CartSession carts;
  private final CartPricingService pricing;
  private final OrderService orders;
  private final PaymentService payments;
  private final Environment env;
  private final vn.tramsach.demo.application.DemoDefaults demo;

  public OrderController(
      CartSession carts,
      CartPricingService pricing,
      OrderService orders,
      PaymentService payments,
      Environment env,
      vn.tramsach.demo.application.DemoDefaults demo) {
    this.carts = carts;
    this.pricing = pricing;
    this.orders = orders;
    this.payments = payments;
    this.env = env;
    this.demo = demo;
  }

  public record CheckoutForm(
      @NotBlank @Size(max = 100, message = "Tên tối đa 100 ký tự.") String name,
      @Pattern(regexp = "[0-9+ ()-]{9,20}", message = "Số điện thoại không hợp lệ.") String phone,
      @Size(min = 10, max = 500, message = "Địa chỉ cần 10–500 ký tự.") String address,
      @Pattern(regexp = "COD|STRIPE", message = "Chọn phương thức thanh toán.") String method,
      @NotNull UUID key) {}

  private void checkoutModel(HttpSession session, Model model) {
    model.addAttribute("quote", pricing.quote(carts.get(session)));
    model.addAttribute("currency", env.getProperty("STRIPE_CURRENCY", "vnd"));
  }

  @GetMapping("/checkout")
  String checkout(HttpSession session, Model model, Principal principal) {
    payments.reconcilePending();
    if (carts.get(session).items().isEmpty()) return "redirect:/cart";
    model.addAttribute(
        "form",
        new CheckoutForm(
            demo.enabled() ? demo.name() : "",
            demo.enabled() ? demo.phone() : "",
            demo.enabled() ? demo.address() : "",
            demo.enabled() ? "STRIPE" : "COD",
            carts.key(session)));
    checkoutModel(session, model);
    return "checkout";
  }

  @PostMapping("/checkout")
  String place(
      @Valid @ModelAttribute("form") CheckoutForm form,
      BindingResult errors,
      HttpSession session,
      Model model,
      Principal principal) {
    if (errors.hasErrors()) {
      checkoutModel(session, model);
      return "checkout";
    }
    UUID current = carts.key(session);
    UUID previous = (UUID) session.getAttribute("submittedKey");
    if (!form.key().equals(current) && !form.key().equals(previous))
      throw new BusinessException("Giỏ hàng đã thay đổi. Hãy mở lại trang thanh toán.");
    var o =
        orders.place(
            principal.getName(),
            form.key(),
            carts.get(session),
            new Delivery(form.name(), form.phone(), form.address()),
            form.method());
    session.setAttribute("lastOrder", o.id());
    session.setAttribute("lastOrderKey", form.key());
    session.setAttribute("submittedKey", form.key());
    try {
      o = payments.start(o.id());
    } catch (PaymentUnavailable e) {
      return "redirect:/account/orders/" + o.id() + "?payment=error";
    }
    if ("COD".equals(o.method())) {
      if (form.key().equals(current)) carts.clear(session);
      return "redirect:/account/orders/" + o.id();
    }
    if (o.paymentUrl() != null && o.status() == OrderStatus.PENDING_PAYMENT)
      return "redirect:" + o.paymentUrl();
    return "redirect:/account/orders/" + o.id();
  }

  @GetMapping("/account/orders")
  String mine(Principal principal, Model model) {
    model.addAttribute("orders", orders.mine(principal.getName()));
    return "orders";
  }

  @GetMapping("/account/orders/{id}")
  String detail(@PathVariable UUID id, Principal principal, HttpSession session, Model model) {
    var o = orders.owned(id, principal.getName());
    if (o.paid()
        && id.equals(session.getAttribute("lastOrder"))
        && carts.key(session).equals(session.getAttribute("lastOrderKey"))) carts.clear(session);
    model.addAttribute("order", o);
    model.addAttribute("admin", false);
    return "order";
  }

  @PostMapping("/account/orders/{id}/retry")
  String retry(@PathVariable UUID id, Principal principal) {
    orders.owned(id, principal.getName());
    payments.reconcile(id);
    var o = payments.start(id);
    return o.status() == OrderStatus.PENDING_PAYMENT && o.paymentUrl() != null
        ? "redirect:" + o.paymentUrl()
        : "redirect:/account/orders/" + id;
  }

  @PostMapping("/account/orders/{id}/refresh")
  String refresh(@PathVariable UUID id, Principal principal) {
    orders.owned(id, principal.getName());
    payments.reconcile(id);
    return "redirect:/account/orders/" + id;
  }

  @PostMapping("/account/orders/{id}/cancel")
  String cancel(@PathVariable UUID id, Principal principal) {
    orders.cancelOwned(id, principal.getName());
    return "redirect:/account/orders/" + id;
  }
}
