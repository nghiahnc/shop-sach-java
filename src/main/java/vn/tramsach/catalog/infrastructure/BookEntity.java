package vn.tramsach.catalog.infrastructure;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "books")
public class BookEntity {
  @Id public UUID id;
  public String slug;
  public String title;
  public String author;

  @Column(columnDefinition = "text")
  public String description;

  public long price;
  public int stock;
  public boolean active;

  @Column(name = "category_id")
  public UUID categoryId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id", insertable = false, updatable = false)
  public CategoryEntity category;

  public int cover;
}
