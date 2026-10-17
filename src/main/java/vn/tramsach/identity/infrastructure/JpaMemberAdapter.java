package vn.tramsach.identity.infrastructure;

import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.tramsach.identity.domain.*;

@Repository
public class JpaMemberAdapter implements MemberRepository {
  @PersistenceContext private EntityManager em;

  private Member map(MemberEntity e) {
    return new Member(e.id, e.email, e.name, e.passwordHash, e.role);
  }

  public Optional<Member> byEmail(String email) {
    return em.createQuery("from MemberEntity where email=:email", MemberEntity.class)
        .setParameter("email", email)
        .getResultStream()
        .findFirst()
        .map(this::map);
  }

  public Member lock(UUID id) {
    return map(em.find(MemberEntity.class, id, LockModeType.PESSIMISTIC_WRITE));
  }

  public void insert(Member m) {
    var e = new MemberEntity();
    e.id = m.id();
    e.email = m.email();
    e.name = m.name();
    e.passwordHash = m.passwordHash();
    e.role = m.role();
    em.persist(e);
    em.flush();
  }
}
