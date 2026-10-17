package vn.tramsach.catalog.domain;

import java.util.*;

public interface CatalogRepository {
  List<Book> books();

  List<Book> featured(int limit);

  Optional<Book> find(UUID id);

  Optional<Book> lock(UUID id);

  void save(Book book);

  List<Category> categories();

  void saveCategory(Category category);
}
