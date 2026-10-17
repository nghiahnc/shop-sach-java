package vn.tramsach.payments.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.tramsach.payments.domain.*;

/** The provider call runs between, never inside, the repository's short transactions. */
@Service
public class DemoCardService {
  private final DemoCardRepository repository;
  private final DemoCardProvisioner provisioner;

  public DemoCardService(DemoCardRepository repository, DemoCardProvisioner provisioner) {
    this.repository = repository;
    this.provisioner = provisioner;
  }

  public SavedDemoCard forBuyer(UUID buyerId) {
    var existing = repository.find(buyerId);
    if (existing.isPresent()) return existing.get();
    var created = provisioner.provision(buyerId);
    repository.saveIfAbsent(created);
    return repository.find(buyerId).orElseThrow();
  }
}
