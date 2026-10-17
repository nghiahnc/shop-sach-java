package vn.tramsach.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Public HTML is deliberately independent of cookies; personal state is fetched separately. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ResponseCacheFilter extends OncePerRequestFilter {
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    boolean page = StorefrontRoutes.publicPage(request);
    boolean asset = StorefrontRoutes.asset(request);
    boolean versioned = asset && StorefrontRoutes.path(request).matches(".*-[a-f0-9]{32}\\.[^/]+$");
    String browser =
        page
            ? "public, max-age=0, must-revalidate"
            : versioned
                ? "public, max-age=31536000, immutable"
                : asset ? "public, max-age=0, must-revalidate" : "private, no-store";
    response.setHeader("Cache-Control", browser);
    if (page)
      response.setHeader(
          "Vercel-CDN-Cache-Control", "public, s-maxage=60, stale-while-revalidate=600");
    else if (!versioned) response.setHeader("Vercel-CDN-Cache-Control", "no-store");
    chain.doFilter(
        request,
        new HttpServletResponseWrapper(response) {
          @Override
          public void setHeader(String name, String value) {
            super.setHeader(
                name,
                asset && getStatus() < 300 && "Cache-Control".equalsIgnoreCase(name)
                    ? browser
                    : value);
          }

          @Override
          public void setStatus(int code) {
            super.setStatus(code);
            if (code >= 300) noStore();
          }

          @Override
          public void sendError(int code, String message) throws IOException {
            noStore();
            super.sendError(code, message);
          }

          @Override
          public void sendError(int code) throws IOException {
            noStore();
            super.sendError(code);
          }

          @Override
          public void sendRedirect(String location) throws IOException {
            noStore();
            super.sendRedirect(location);
          }

          private void noStore() {
            super.setHeader("Cache-Control", "private, no-store");
            super.setHeader("Vercel-CDN-Cache-Control", "no-store");
          }
        });
  }
}
