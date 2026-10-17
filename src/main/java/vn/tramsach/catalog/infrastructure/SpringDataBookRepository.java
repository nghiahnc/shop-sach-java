package vn.tramsach.catalog.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataBookRepository extends JpaRepository<BookEntity, UUID> {
  @Query("select b from BookEntity b join fetch b.category order by b.title")
  java.util.List<BookEntity> catalogWithCategory();

  @Query("select b from BookEntity b join fetch b.category where b.id = :id")
  Optional<BookEntity> bookWithCategory(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from BookEntity b where b.id = :id")
  Optional<BookEntity> lockBook(@Param("id") UUID id);
}
