package vn.tramsach;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import vn.tramsach.demo.application.DemoDefaults;
import vn.tramsach.payments.application.DemoCardService;
import vn.tramsach.payments.domain.*;

class DemoDefaultsTest {
  @Test
  void disabledByDefault() {
    assertFalse(new DemoDefaults(new MockEnvironment()).enabled());
  }

  @Test
  void uniqueRegistrationEmails() {
    var d = new DemoDefaults(new MockEnvironment().withProperty("DEMO_MODE", "true"));
    assertTrue(d.enabled());
    var emails = new HashSet<String>();
    for (int i = 0; i < 1000; i++) assertTrue(emails.add(d.registrationEmail()));
    assertFalse(emails.contains(d.email()));
  }

  @Test
  void savedCardIsReusedWithoutAnotherProviderCall() {
    var r = mock(DemoCardRepository.class);
    var p = mock(DemoCardProvisioner.class);
    var id = UUID.randomUUID();
    var card = new SavedDemoCard(id, "cus_test", "pm_test");
    when(r.find(id)).thenReturn(Optional.of(card));
    assertEquals(card, new DemoCardService(r, p).forBuyer(id));
    verifyNoInteractions(p);
    verify(r, never()).saveIfAbsent(any());
  }

  @Test
  void savesOnlyProviderReferencesAndReturnsCanonicalRow() {
    var r = mock(DemoCardRepository.class);
    var p = mock(DemoCardProvisioner.class);
    var id = UUID.randomUUID();
    var card = new SavedDemoCard(id, "cus_test", "pm_test");
    when(r.find(id)).thenReturn(Optional.empty(), Optional.of(card));
    when(p.provision(id)).thenReturn(card);
    assertEquals(card, new DemoCardService(r, p).forBuyer(id));
    verify(p).provision(id);
    verify(r).saveIfAbsent(card);
  }

  @Test
  void providerFailureDoesNotPersistIncompleteProfile() {
    var r = mock(DemoCardRepository.class);
    var p = mock(DemoCardProvisioner.class);
    var id = UUID.randomUUID();
    when(r.find(id)).thenReturn(Optional.empty());
    when(p.provision(id)).thenThrow(new PaymentUnavailable(false));
    assertThrows(PaymentUnavailable.class, () -> new DemoCardService(r, p).forBuyer(id));
    verify(r, never()).saveIfAbsent(any());
  }
}
