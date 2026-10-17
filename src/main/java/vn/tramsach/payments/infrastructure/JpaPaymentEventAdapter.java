package vn.tramsach.payments.infrastructure;

import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import vn.tramsach.payments.domain.PaymentEventRepository;

@Repository
public class JpaPaymentEventAdapter implements PaymentEventRepository {
  @PersistenceContext private EntityManager em;

  public boolean claim(String eventId, String sessionId) {
    return em.createNativeQuery(
                "INSERT INTO payment_events(event_id,session_id) VALUES (:event,:session) ON"
                    + " CONFLICT (event_id) DO NOTHING")
            .setParameter("event", eventId)
            .setParameter("session", sessionId)
            .executeUpdate()
        == 1;
  }
}
