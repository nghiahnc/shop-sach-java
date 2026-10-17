package vn.tramsach.payments.infrastructure;

import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.tramsach.payments.domain.*;

@Repository
public class JpaDemoCardAdapter implements DemoCardRepository {
  @PersistenceContext private EntityManager em;

  @Transactional(readOnly = true)
  public Optional<SavedDemoCard> find(UUID buyerId) {
    var rows =
        em.createNativeQuery(
                "SELECT customer_id,payment_method_id FROM demo_payment_profiles WHERE"
                    + " buyer_id=:buyer")
            .setParameter("buyer", buyerId)
            .getResultList();
    if (rows.isEmpty()) return Optional.empty();
    var row = (Object[]) rows.getFirst();
    return Optional.of(new SavedDemoCard(buyerId, (String) row[0], (String) row[1]));
  }

  @Transactional
  public void saveIfAbsent(SavedDemoCard card) {
    em.createNativeQuery(
            "INSERT INTO demo_payment_profiles(buyer_id,customer_id,payment_method_id) VALUES"
                + " (:buyer,:customer,:method) ON CONFLICT (buyer_id) DO NOTHING")
        .setParameter("buyer", card.buyerId())
        .setParameter("customer", card.customerId())
        .setParameter("method", card.paymentMethodId())
        .executeUpdate();
  }
}
