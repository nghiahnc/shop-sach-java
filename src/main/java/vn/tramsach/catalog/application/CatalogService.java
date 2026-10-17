package vn.tramsach.catalog.application;

import java.text.Normalizer;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tramsach.catalog.domain.*;
import vn.tramsach.shared.domain.*;

@Service
@Transactional(readOnly = true)
public class CatalogService {
  private final CatalogRepository repository;

  public CatalogService(CatalogRepository repository) {
    this.repository = repository;
  }

  public record Page(List<Book> content, int number, int pages, int total) {}

  private String normalize(String s) {
    return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replace('đ', 'd');
  }

  public Page search(String query, UUID category, Long min, Long max, String sort, int page) {
    var activeCategories =
        categories().stream().filter(Category::active).map(Category::id).toList();
    var all =
        repository.books().stream()
            .filter(b -> b.active() && activeCategories.contains(b.categoryId()))
            .filter(b -> normalize(b.title() + " " + b.author()).contains(normalize(query)))
            .filter(b -> category == null || category.equals(b.categoryId()))
            .filter(b -> min == null || b.price().vnd() >= min)
            .filter(b -> max == null || b.price().vnd() <= max)
            .sorted(
                "priceAsc".equals(sort)
                    ? Comparator.comparingLong(b -> b.price().vnd())
                    : "priceDesc".equals(sort)
                        ? Comparator.<Book>comparingLong(b -> b.price().vnd()).reversed()
                        : Comparator.comparing(Book::title))
            .toList();
    int pages = Math.max(1, (all.size() + 11) / 12), p = Math.max(0, Math.min(page, pages - 1));
    return new Page(
        all.subList(Math.min(p * 12, all.size()), Math.min((p + 1) * 12, all.size())),
        p,
        pages,
        all.size());
  }

  public List<Book> all() {
    return repository.books();
  }

  public List<Book> featured() {
    return repository.featured(8);
  }

  public Book get(UUID id) {
    return repository.find(id).orElseThrow(() -> new BusinessException("Không tìm thấy sách."));
  }

  public List<Category> categories() {
    return repository.categories();
  }

  public boolean sellable(Book b) {
    return b.active()
        && categories().stream().anyMatch(c -> c.id().equals(b.categoryId()) && c.active());
  }

  @Transactional
  public Book reserve(UUID id, int quantity) {
    if (quantity < 1 || quantity > 99) throw new BusinessException("Số lượng phải từ 1 đến 99.");
    var b = repository.lock(id).orElseThrow(() -> new BusinessException("Không tìm thấy sách."));
    if (!sellable(b) || b.stock() < quantity)
      throw new BusinessException("Sách “" + b.title() + "” không đủ tồn kho.");
    repository.save(
        new Book(
            b.id(),
            b.slug(),
            b.title(),
            b.author(),
            b.description(),
            b.price(),
            b.stock() - quantity,
            b.active(),
            b.categoryId(),
            b.categoryName(),
            b.cover()));
    return b;
  }

  @Transactional
  public void restore(UUID id, int quantity) {
    var b = repository.lock(id).orElseThrow();
    repository.save(
        new Book(
            b.id(),
            b.slug(),
            b.title(),
            b.author(),
            b.description(),
            b.price(),
            Math.addExact(b.stock(), quantity),
            b.active(),
            b.categoryId(),
            b.categoryName(),
            b.cover()));
  }

  @Transactional
  public void edit(
      UUID id,
      String title,
      String author,
      String description,
      long price,
      int stock,
      UUID category,
      int cover,
      boolean active) {
    if (title == null
        || title.isBlank()
        || title.length() > 160
        || author == null
        || author.isBlank()
        || author.length() > 120
        || description == null
        || description.length() > 5000
        || price < 1000
        || price > 100000000
        || stock < 0
        || stock > 100000
        || cover < 1
        || cover > 18) throw new BusinessException("Thông tin sách không hợp lệ.");
    if (categories().stream().noneMatch(c -> c.id().equals(category)))
      throw new BusinessException("Thể loại không tồn tại.");
    UUID key = id == null ? UUID.randomUUID() : id;
    if (id != null)
      repository.lock(id).orElseThrow(() -> new BusinessException("Không tìm thấy sách."));
    repository.save(
        new Book(
            key,
            "sach-" + key,
            title.trim(),
            author.trim(),
            description.trim(),
            new Money(price),
            stock,
            active,
            category,
            "",
            cover));
  }

  @Transactional
  public void editCategory(UUID id, String name, boolean active) {
    if (name == null || name.isBlank() || name.length() > 80)
      throw new BusinessException("Tên thể loại phải từ 1 đến 80 ký tự.");
    UUID key = id == null ? UUID.randomUUID() : id;
    if (id != null && categories().stream().noneMatch(c -> c.id().equals(id)))
      throw new BusinessException("Thể loại không tồn tại.");
    repository.saveCategory(new Category(key, name.trim(), "the-loai-" + key, active));
  }
}
