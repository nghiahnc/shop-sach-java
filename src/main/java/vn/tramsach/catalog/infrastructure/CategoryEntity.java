package vn.tramsach.catalog.infrastructure;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class CategoryEntity {
  @Id public UUID id;
  public String name;
  public String slug;
  public boolean active;

  public String getName() {
    return name;
  }

  public UUID getId() {
    return id;
  }

  public String getSlug() {
    return slug;
  }

  public boolean isActive() {
    return active;
  }
}
