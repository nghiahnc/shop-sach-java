package vn.tramsach.config;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.tramsach.cart.application.CartSession;
import vn.tramsach.payments.domain.PaymentUnavailable;
import vn.tramsach.shared.domain.BusinessException;

@ControllerAdvice
class WebAdvice {
  private final CartSession carts;
  private final vn.tramsach.demo.application.DemoDefaults demo;

  WebAdvice(CartSession carts, vn.tramsach.demo.application.DemoDefaults demo) {
    this.carts = carts;
    this.demo = demo;
  }

  @ModelAttribute
  void common(Model model, HttpServletRequest request, Principal principal) {
    boolean publicPage = StorefrontRoutes.publicPage(request);
    var session = publicPage ? null : request.getSession(false);
    model.addAttribute("publicPage", publicPage);
    model.addAttribute("demo", demo);
    model.addAttribute("demoMode", demo.enabled());
    model.addAttribute("cartCount", session == null ? 0 : carts.get(session).count());
    model.addAttribute("signedIn", !publicPage && principal != null);
    model.addAttribute("isAdmin", !publicPage && request.isUserInRole("ADMIN"));
    model.addAttribute("accountEmail", publicPage || principal == null ? "" : principal.getName());
  }

  @ExceptionHandler({BusinessException.class, PaymentUnavailable.class})
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  String business(RuntimeException error, Model model) {
    model.addAttribute("message", error.getMessage());
    return "error/business";
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  String conflict(Model model) {
    model.addAttribute("message", "Dữ liệu vừa thay đổi hoặc đã tồn tại. Hãy tải lại và thử lại.");
    return "error/business";
  }
}
