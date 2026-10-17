package vn.tramsach.config;

import jakarta.servlet.http.HttpServletRequest;

final class StorefrontRoutes {
  private StorefrontRoutes() {}

  static String path(HttpServletRequest request) {
    return request.getRequestURI().substring(request.getContextPath().length());
  }

  static boolean publicPage(HttpServletRequest request) {
    return ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
        && ("/".equals(path(request)) || "/about".equals(path(request)));
  }

  static boolean asset(HttpServletRequest request) {
    String path = path(request);
    return ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
        && (path.startsWith("/images/")
            || path.startsWith("/fonts/")
            || path.matches("/site(?:-[a-f0-9]{32})?\\.(?:css|js)"));
  }
}
