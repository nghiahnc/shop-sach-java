package vn.tramsach.admin.web;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.tramsach.catalog.application.CatalogService;
import vn.tramsach.orders.application.OrderService;
import vn.tramsach.payments.application.PaymentService;
import vn.tramsach.promotions.application.PromotionService;
import vn.tramsach.shared.domain.BusinessException;

@Controller
@RequestMapping("/admin")
public class AdminController {
  private final CatalogService catalog;
  private final OrderService orders;
  private final PromotionService promotions;
  private final PaymentService payments;
  private final vn.tramsach.demo.application.DemoDefaults demo;

  public AdminController(
      CatalogService catalog,
      OrderService orders,
      PromotionService promotions,
      PaymentService payments,
      vn.tramsach.demo.application.DemoDefaults demo) {
    this.catalog = catalog;
    this.orders = orders;
    this.promotions = promotions;
    this.payments = payments;
    this.demo = demo;
  }

  @GetMapping
  String dashboard(Model model) {
    payments.reconcilePending();
    var all = orders.all();
    model.addAttribute("orders", all);
    model.addAttribute(
        "revenue", all.stream().filter(o -> o.paid()).mapToLong(o -> o.total().vnd()).sum());
    model.addAttribute("bookCount", catalog.all().size());
    model.addAttribute(
        "pendingCount",
        all.stream().filter(o -> o.status().name().equals("PENDING_PAYMENT")).count());
    return "admin/dashboard";
  }

  @GetMapping("/books")
  String books(Model model) {
    model.addAttribute("books", catalog.all());
    return "admin/books";
  }

  @GetMapping("/books/new")
  String newBook(Model model) {
    if (demo.enabled()) {
      var b = catalog.get(UUID.fromString("00000000-0000-0000-0000-000000000065"));
      model.addAttribute(
          "book",
          new vn.tramsach.catalog.domain.Book(
              null,
              b.slug(),
              b.title(),
              b.author(),
              b.description(),
              b.price(),
              5,
              false,
              b.categoryId(),
              b.categoryName(),
              b.cover()));
    }
    model.addAttribute("categories", catalog.categories());
    return "admin/book-form";
  }

  @GetMapping("/books/{id}")
  String editBook(@PathVariable UUID id, Model model) {
    model.addAttribute("book", catalog.get(id));
    model.addAttribute("categories", catalog.categories());
    return "admin/book-form";
  }

  @PostMapping("/books/save")
  String saveBook(
      @RequestParam(required = false) UUID id,
      @RequestParam String title,
      @RequestParam String author,
      @RequestParam String description,
      @RequestParam long price,
      @RequestParam int stock,
      @RequestParam UUID categoryId,
      @RequestParam int cover,
      @RequestParam(defaultValue = "false") boolean active,
      Model model) {
    try {
      catalog.edit(id, title, author, description, price, stock, categoryId, cover, active);
    } catch (BusinessException e) {
      model.addAttribute("error", e.getMessage());
      model.addAttribute("categories", catalog.categories());
      model.addAttribute(
          "book",
          new vn.tramsach.catalog.domain.Book(
              id,
              "",
              title,
              author,
              description,
              new vn.tramsach.shared.domain.Money(Math.max(0, price)),
              stock,
              active,
              categoryId,
              "",
              cover));
      return "admin/book-form";
    }
    return "redirect:/admin/books";
  }

  @GetMapping("/categories")
  String categories(Model model) {
    model.addAttribute("categories", catalog.categories());
    return "admin/categories";
  }

  @PostMapping("/categories")
  String category(
      @RequestParam(required = false) UUID id,
      @RequestParam String name,
      @RequestParam(defaultValue = "false") boolean active,
      Model model) {
    try {
      catalog.editCategory(id, name, active);
    } catch (BusinessException e) {
      model.addAttribute("error", e.getMessage());
      model.addAttribute(
          "categoryInput",
          Map.of("id", id == null ? "" : id.toString(), "name", name, "active", active));
      return categories(model);
    }
    return "redirect:/admin/categories";
  }

  @GetMapping("/coupons")
  String coupons(Model model) {
    model.addAttribute("coupons", promotions.all());
    return "admin/coupons";
  }

  @PostMapping("/coupons")
  String coupon(
      @RequestParam String code,
      @RequestParam String kind,
      @RequestParam long value,
      @RequestParam long minimum,
      @RequestParam(defaultValue = "false") boolean active,
      @RequestParam(defaultValue = "") String expires,
      Model model) {
    try {
      promotions.save(
          code,
          kind,
          value,
          minimum,
          active,
          expires.isBlank()
              ? null
              : LocalDate.parse(expires)
                  .plusDays(1)
                  .atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh"))
                  .toInstant());
    } catch (BusinessException | java.time.format.DateTimeParseException e) {
      model.addAttribute(
          "error", "Thông tin mã giảm giá không hợp lệ. Kiểm tra giá trị và ngày áp dụng.");
      model.addAttribute(
          "couponInput",
          Map.of(
              "code", code, "kind", kind, "value", value, "minimum", minimum, "active", active,
              "expires", expires));
      return coupons(model);
    }
    return "redirect:/admin/coupons";
  }

  @GetMapping("/orders/{id}")
  String detail(@PathVariable UUID id, Model model) {
    model.addAttribute("order", orders.get(id));
    model.addAttribute("admin", true);
    return "order";
  }

  @PostMapping("/orders/{id}")
  String transition(@PathVariable UUID id, @RequestParam String action) {
    orders.transition(id, action);
    return "redirect:/admin/orders/" + id;
  }
}
