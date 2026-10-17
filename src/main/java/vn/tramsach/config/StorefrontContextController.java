package vn.tramsach.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import vn.tramsach.cart.application.CartSession;

@RestController
@RequestMapping("/storefront")
public class StorefrontContextController {
  private final CartSession carts;

  public StorefrontContextController(CartSession carts) {
    this.carts = carts;
  }

  public record Context(boolean signedIn, boolean isAdmin, int cartCount) {}

  public record Token(String parameterName, String headerName, String token) {}

  @GetMapping("/context")
  Context context(HttpServletRequest request, Authentication authentication) {
    boolean signedIn =
        authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);
    boolean admin =
        signedIn
            && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    var session = request.getSession(false);
    return new Context(signedIn, admin, session == null ? 0 : carts.get(session).count());
  }

  @GetMapping("/csrf")
  Token csrf(CsrfToken csrf) {
    return new Token(csrf.getParameterName(), csrf.getHeaderName(), csrf.getToken());
  }
}
