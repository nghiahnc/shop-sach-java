package vn.tramsach.payments.domain;

import java.util.*;

public interface DemoCardRepository {
  Optional<SavedDemoCard> find(UUID buyerId);

  void saveIfAbsent(SavedDemoCard card);
}
