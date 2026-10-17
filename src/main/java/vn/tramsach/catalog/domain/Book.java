package vn.tramsach.catalog.domain;

import java.util.UUID;
import vn.tramsach.shared.domain.Money;

public record Book(
    UUID id,
    String slug,
    String title,
    String author,
    String description,
    Money price,
    int stock,
    boolean active,
    UUID categoryId,
    String categoryName,
    int cover) {}
