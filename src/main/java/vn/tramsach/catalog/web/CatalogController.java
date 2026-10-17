package vn.tramsach.catalog.web;

import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.tramsach.catalog.application.CatalogService;
import vn.tramsach.shared.domain.BusinessException;

@Controller
public class CatalogController {
  private final CatalogService catalog;

  public CatalogController(CatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/")
  String home(Model model) {
    model.addAttribute(
        "categories", catalog.categories().stream().filter(c -> c.active()).toList());
    model.addAttribute("featured", catalog.featured());
    return "home";
  }

  @GetMapping("/about")
  String about() {
    return "about";
  }

  @GetMapping("/books")
  String browse(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) UUID category,
      @RequestParam(required = false) Long min,
      @RequestParam(required = false) Long max,
      @RequestParam(defaultValue = "title") String sort,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    if (q.length() > 160 || (min != null && min < 0) || (max != null && max < 0))
      throw new BusinessException("Bộ lọc không hợp lệ.");
    model.addAttribute("page", catalog.search(q, category, min, max, sort, page));
    model.addAttribute(
        "categories", catalog.categories().stream().filter(c -> c.active()).toList());
    model.addAttribute("q", q);
    model.addAttribute("category", category);
    model.addAttribute("min", min);
    model.addAttribute("max", max);
    model.addAttribute("sort", sort);
    return "catalog";
  }

  @GetMapping("/books/{id}")
  String detail(@PathVariable UUID id, Model model) {
    var b = catalog.get(id);
    if (!catalog.sellable(b)) throw new BusinessException("Sách đang tạm ngừng bán.");
    model.addAttribute("book", b);
    model.addAttribute(
        "related",
        catalog.search("", b.categoryId(), null, null, "title", 0).content().stream()
            .filter(x -> !x.id().equals(id))
            .limit(3)
            .toList());
    return "book";
  }
}
