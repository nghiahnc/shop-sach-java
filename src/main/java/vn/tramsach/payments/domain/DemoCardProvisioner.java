package vn.tramsach.payments.domain;

import java.util.UUID;

public interface DemoCardProvisioner {
  SavedDemoCard provision(UUID buyerId);
}
