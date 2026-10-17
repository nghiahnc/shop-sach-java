package vn.tramsach.catalog.infrastructure;

import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.tramsach.catalog.domain.*;
import vn.tramsach.shared.domain.Money;

@Repository
public class JpaCatalogAdapter implements CatalogRepository {
  @PersistenceContext private EntityManager em;
  private final SpringDataBookRepository books;

  public JpaCatalogAdapter(SpringDataBookRepository books) {
    this.books = books;
  }

  private Book map(BookEntity b) {
    var c = b.category;
    return new Book(
        b.id,
        b.slug,
        b.title,
        b.author,
        b.description,
        new Money(b.price),
        b.stock,
        b.active,
        b.categoryId,
        c == null ? "" : c.getName(),
        b.cover);
  }

  public List<Book> books() {
    return books.catalogWithCategory().stream().map(this::map).toList();
  }

  public Optional<Book> find(UUID id) {
    return books.bookWithCategory(id).map(this::map);
  }

  public List<Book> featured(int limit) {
    return em
        .createQuery(
            "select b from BookEntity b join fetch b.category c "
                + "where b.active = true and c.active = true order by b.cover, b.id",
            BookEntity.class)
        .setMaxResults(limit)
        .getResultList()
        .stream()
        .map(this::map)
        .toList();
  }

  public Optional<Book> lock(UUID id) {
    return books.lockBook(id).map(this::map);
  }

  public void save(Book b) {
    var e = new BookEntity();
    e.id = b.id();
    e.slug = b.slug();
    e.title = b.title();
    e.author = b.author();
    e.description = b.description();
    e.price = b.price().vnd();
    e.stock = b.stock();
    e.active = b.active();
    e.categoryId = b.categoryId();
    e.category = em.getReference(CategoryEntity.class, b.categoryId());
    e.cover = b.cover();
    em.merge(e);
  }

  public List<Category> categories() {
    return em
        .createQuery("from CategoryEntity order by name", CategoryEntity.class)
        .getResultList()
        .stream()
        .map(c -> new Category(c.getId(), c.getName(), c.getSlug(), c.isActive()))
        .toList();
  }

  public void saveCategory(Category c) {
    var e = new CategoryEntity();
    e.id = c.id();
    e.name = c.name();
    e.slug = c.slug();
    e.active = c.active();
    em.merge(e);
  }
}
