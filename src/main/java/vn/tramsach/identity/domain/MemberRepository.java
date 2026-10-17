package vn.tramsach.identity.domain;

import java.util.*;

public interface MemberRepository {
  Optional<Member> byEmail(String email);

  Member lock(UUID id);

  void insert(Member member);
}
