package vn.tramsach;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.tramsach.cart.application.CartPricingService;
import vn.tramsach.cart.domain.Cart;
import vn.tramsach.catalog.application.CatalogService;
import vn.tramsach.identity.application.IdentityService;
import vn.tramsach.orders.application.OrderService;
import vn.tramsach.orders.domain.*;
import vn.tramsach.orders.domain.Order;
import vn.tramsach.payments.application.*;
import vn.tramsach.payments.domain.*;
import vn.tramsach.payments.infrastructure.StripeGateway;
import vn.tramsach.shared.domain.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TRAM_SACH_TEST", matches = "isolated")
class BookstoreIT {
  @DynamicPropertySource
  static void isolated(DynamicPropertyRegistry r) throws Exception {
    var data = new ObjectMapper().readTree(Files.readString(Path.of(".local/env-test.json")));
    var production =
        new ObjectMapper().readTree(Files.readString(Path.of(".local/env-production.json")));
    if (data.path("DB_URL").asText().equals(production.path("DB_URL").asText()))
      throw new IllegalStateException("Refusing tests on production");
    data.fields().forEachRemaining(e -> r.add(e.getKey(), () -> e.getValue().asText()));
  }

  @Autowired CatalogService catalog;
  @Autowired IdentityService identity;
  @Autowired CartPricingService pricing;
  @Autowired OrderService orders;
  @Autowired PaymentService payments;
  @Autowired PaymentEventService events;
  @Autowired DemoCardService demoCards;
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @MockitoBean StripeGateway stripe;
  String email;
  UUID book;
  final UUID category = UUID.fromString("00000000-0000-0000-0000-000000000003");

  @BeforeEach
  void setup() {
    reset(stripe);
    when(stripe.method()).thenReturn("STRIPE");
    doThrow(new PaymentUnavailable(false)).when(stripe).create(any());
    doThrow(new PaymentUnavailable(false)).when(stripe).inspect(any());
    email = "it-" + UUID.randomUUID() + "@tramsach.demo";
    identity.register(email, "Integration Test", "TestPassword123!");
    book = UUID.randomUUID();
    catalog.edit(
        null,
        "IT " + book,
        "Test Author",
        "Sách dành riêng cho kiểm thử.",
        128000,
        10,
        category,
        7,
        true);
    book =
        catalog.all().stream()
            .filter(b -> b.title().equals("IT " + book))
            .findFirst()
            .orElseThrow()
            .id();
  }

  Cart cart(int count) {
    var c = new Cart();
    c.add(book, count);
    return c;
  }

  Delivery delivery() {
    return new Delivery("Bạn đọc kiểm thử", "0901234567", "123 Đường Sách, Cần Thơ");
  }

  Order place(String method) {
    return orders.place(email, UUID.randomUUID(), cart(1), delivery(), method);
  }

  @Test
  void flywayAndRealPostgres() {
    assertTrue(
        db.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)
            >= 3);
    assertTrue(db.queryForObject("select version()", String.class).contains("PostgreSQL"));
  }

  @Test
  void quoteDiscountShipping() {
    var c = cart(2);
    c.coupon("DOCSACH10");
    var q = pricing.quote(c);
    assertEquals(256000, q.subtotal().vnd());
    assertEquals(25600, q.discount().vnd());
    assertEquals(250400, q.total().vnd());
  }

  @Test
  void emptyAndInvalidCheckout() {
    assertThrows(
        BusinessException.class,
        () -> orders.place(email, UUID.randomUUID(), new Cart(), delivery(), "COD"));
    assertThrows(
        BusinessException.class,
        () -> orders.place(email, UUID.randomUUID(), cart(1), delivery(), "OTHER"));
    assertEquals(10, catalog.get(book).stock());
  }

  @Test
  void duplicateCheckoutConcurrent() throws Exception {
    var key = UUID.randomUUID();
    try (var executor = Executors.newFixedThreadPool(4)) {
      var futures = new ArrayList<Future<Order>>();
      for (int i = 0; i < 4; i++)
        futures.add(executor.submit(() -> orders.place(email, key, cart(1), delivery(), "COD")));
      var ids = new HashSet<UUID>();
      for (var f : futures) ids.add(f.get(30, TimeUnit.SECONDS).id());
      assertEquals(1, ids.size());
      assertEquals(9, catalog.get(book).stock());
    }
  }

  @Test
  void lastCopyConcurrent() throws Exception {
    var b = catalog.get(book);
    catalog.edit(
        book, b.title(), b.author(), b.description(), b.price().vnd(), 1, category, 7, true);
    String second = "it-" + UUID.randomUUID() + "@tramsach.demo";
    identity.register(second, "Second", "TestPassword123!");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var one = executor.submit(() -> attempt(email));
      var two = executor.submit(() -> attempt(second));
      assertEquals(
          1, (one.get(30, TimeUnit.SECONDS) ? 1 : 0) + (two.get(30, TimeUnit.SECONDS) ? 1 : 0));
      assertEquals(0, catalog.get(book).stock());
    }
  }

  boolean attempt(String customer) {
    try {
      orders.place(customer, UUID.randomUUID(), cart(1), delivery(), "COD");
      return true;
    } catch (BusinessException e) {
      return false;
    }
  }

  @Test
  void cancelRestocksOnce() {
    var o = place("COD");
    assertEquals(9, catalog.get(book).stock());
    orders.transition(o.id(), "cancel");
    orders.transition(o.id(), "cancel");
    assertEquals(10, catalog.get(book).stock());
    assertFalse(orders.get(o.id()).paid());
  }

  @Test
  void concurrentCancelRestocksOnce() throws Exception {
    var order = place("COD");
    try (var executor = Executors.newFixedThreadPool(3)) {
      var calls = new ArrayList<Future<?>>();
      for (int i = 0; i < 3; i++)
        calls.add(executor.submit(() -> orders.transition(order.id(), "cancel")));
      for (var call : calls) call.get(30, TimeUnit.SECONDS);
    }
    assertEquals(10, catalog.get(book).stock());
  }

  @Test
  void codPaidOnlyWhenDelivered() {
    var o = place("COD");
    assertFalse(o.paid());
    assertThrows(BusinessException.class, () -> orders.transition(o.id(), "complete"));
    orders.transition(o.id(), "ship");
    orders.transition(o.id(), "complete");
    assertTrue(orders.get(o.id()).paid());
  }

  @Test
  void failedGatewayKeepsReservationAndRetry() {
    var c = cart(1);
    var o = orders.place(email, UUID.randomUUID(), c, delivery(), "STRIPE");
    doThrow(new PaymentUnavailable(false)).when(stripe).create(any());
    assertThrows(PaymentUnavailable.class, () -> payments.start(o.id()));
    assertEquals(1, c.count());
    assertEquals(OrderStatus.PENDING_PAYMENT, orders.get(o.id()).status());
    assertEquals(9, catalog.get(book).stock());
    doReturn(
            new PaymentGateway.Checkout(
                "cs_test_" + o.id(), "https://checkout.stripe.com/test", "vnd", o.total().vnd()))
        .when(stripe)
        .create(any());
    var retried = payments.start(o.id());
    assertNotNull(retried.sessionId());
    assertEquals(9, catalog.get(book).stock());
  }

  @Test
  void duplicateSignedEventDoesNotDuplicatePayment() {
    var o = place("STRIPE");
    String session = "cs_test_" + o.id();
    orders.attach(o.id(), session, "https://checkout.stripe.com/test", "vnd", o.total().vnd());
    var p = new PaymentGateway.Snapshot(session, "complete", true, "vnd", o.total().vnd());
    String event = "evt_it_" + UUID.randomUUID();
    assertTrue(events.apply(event, o.id(), p));
    assertFalse(events.apply(event, o.id(), p));
    assertTrue(events.apply(event + "different", o.id(), p));
    assertTrue(orders.get(o.id()).paid());
    assertEquals(9, catalog.get(book).stock());
  }

  @Test
  void mismatchedAmountRollsBackEvent() {
    var o = place("STRIPE");
    String session = "cs_test_" + o.id();
    orders.attach(o.id(), session, "https://checkout.stripe.com/test", "vnd", o.total().vnd());
    String event = "evt_it_" + UUID.randomUUID();
    assertThrows(
        BusinessException.class,
        () ->
            events.apply(
                event, o.id(), new PaymentGateway.Snapshot(session, "complete", true, "vnd", 1)));
    assertEquals(
        0,
        db.queryForObject(
            "select count(*) from payment_events where event_id=?", Integer.class, event));
    assertFalse(orders.get(o.id()).paid());
  }

  @Test
  void expiredWebhookRestocksOnce() {
    var o = place("STRIPE");
    String session = "cs_test_" + o.id();
    orders.attach(o.id(), session, "https://checkout.stripe.com/test", "vnd", o.total().vnd());
    var p = new PaymentGateway.Snapshot(session, "expired", false, "vnd", o.total().vnd());
    String event = "evt_it_" + UUID.randomUUID();
    events.apply(event, o.id(), p);
    events.apply(event, o.id(), p);
    assertEquals(10, catalog.get(book).stock());
    assertEquals(OrderStatus.EXPIRED, orders.get(o.id()).status());
  }

  @Test
  void unrelatedExpiredSessionCannotReleaseInventory() {
    var o = place("STRIPE");
    orders.attach(
        o.id(), "cs_own_" + o.id(), "https://checkout.stripe.com/test", "vnd", o.total().vnd());
    String event = "evt_it_" + UUID.randomUUID();
    assertThrows(
        BusinessException.class,
        () ->
            events.apply(
                event,
                o.id(),
                new PaymentGateway.Snapshot(
                    "cs_unrelated", "expired", false, "vnd", o.total().vnd())));
    assertEquals(9, catalog.get(book).stock());
    assertEquals(OrderStatus.PENDING_PAYMENT, orders.get(o.id()).status());
    assertEquals(
        0,
        db.queryForObject(
            "select count(*) from payment_events where event_id=?", Integer.class, event));
  }

  @Test
  void ownership() {
    var o = place("COD");
    assertThrows(AccessDeniedException.class, () -> orders.owned(o.id(), "admin@tramsach.demo"));
    assertEquals(o.id(), orders.owned(o.id(), email).id());
  }

  @Test
  void securityAndCsrf() throws Exception {
    mvc.perform(get("/admin").with(user(email).roles("CUSTOMER")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/cart/add").param("bookId", book.toString()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/admin/books").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
    mvc.perform(get("/checkout")).andExpect(status().is3xxRedirection());
  }

  @Test
  void registrationHashAndValidation() {
    assertNotEquals("TestPassword123!", identity.get(email).passwordHash());
    assertThrows(BusinessException.class, () -> identity.register("bad", "x", "short"));
    assertThrows(BusinessException.class, () -> identity.register(email, "x", "TestPassword123!"));
  }

  @Test
  void allTemplatesRender() throws Exception {
    for (String route :
        List.of("/", "/about", "/books?q=java", "/books/" + book, "/cart", "/login", "/register"))
      mvc.perform(get(route)).andExpect(status().isOk());
    for (String route :
        List.of(
            "/admin",
            "/admin/books",
            "/admin/books/new",
            "/admin/books/" + book,
            "/admin/categories",
            "/admin/coupons"))
      mvc.perform(get(route).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
    var o = place("COD");
    mvc.perform(get("/account/orders/" + o.id()).with(user(email))).andExpect(status().isOk());
    mvc.perform(get("/account/orders").with(user(email))).andExpect(status().isOk());
  }

  @Test
  void webhookRejectsUnsigned() throws Exception {
    mvc.perform(post("/payments/stripe/webhook").contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void demoRegistrationPrefillsAreUniqueAndPreserveInvalidInput() throws Exception {
    var a =
        mvc.perform(get("/register"))
            .andExpect(status().isOk())
            .andReturn()
            .getModelAndView()
            .getModel()
            .get("form");
    var b = mvc.perform(get("/register")).andReturn().getModelAndView().getModel().get("form");
    assertNotEquals(a, b);
    mvc.perform(
            post("/register")
                .with(csrf())
                .param("name", "Tên đã sửa")
                .param("email", "bad")
                .param("password", "TramSachDemo2026!"))
        .andExpect(status().isOk())
        .andExpect(
            result ->
                assertEquals(
                    "Tên đã sửa",
                    ((vn.tramsach.identity.web.IdentityController.RegisterForm)
                            result.getModelAndView().getModel().get("form"))
                        .name()))
        .andExpect(model().attributeHasFieldErrors("form", "email"));
    mvc.perform(get("/login")).andExpect(model().attribute("loginEmail", "demo@tramsach.demo"));
    assertEquals("CUSTOMER", identity.get("demo@tramsach.demo").role());
  }

  @Test
  void demoCheckoutPrefillsAndCardRepositoryDeduplicates() throws Exception {
    var added =
        mvc.perform(post("/cart/add").with(csrf()).param("bookId", book.toString()))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    mvc.perform(get("/checkout").cookie(added.getResponse().getCookie("SESSION")).with(user(email)))
        .andExpect(status().isOk())
        .andExpect(
            result ->
                assertEquals(
                    "Nguyễn Minh An",
                    ((vn.tramsach.orders.web.OrderController.CheckoutForm)
                            result.getModelAndView().getModel().get("form"))
                        .name()))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("0901234567")));
    var repo = org.springframework.test.util.ReflectionTestUtils.getField(demoCards, "repository");
    var r = (vn.tramsach.payments.domain.DemoCardRepository) repo;
    var id = identity.get(email).id();
    r.saveIfAbsent(new SavedDemoCard(id, "cus_it_" + id, "pm_it_" + id));
    r.saveIfAbsent(new SavedDemoCard(id, "cus_second_" + id, "pm_second_" + id));
    assertEquals("cus_it_" + id, r.find(id).orElseThrow().customerId());
  }

  @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;

  @Test
  void publicPagesAreCookieIndependentAndDoNotCreateSession() throws Exception {
    for (String route : List.of("/", "/about")) {
      var anonymous =
          mvc.perform(get(route))
              .andExpect(status().isOk())
              .andExpect(
                  header()
                      .string(
                          "Vercel-CDN-Cache-Control",
                          "public, s-maxage=60, stale-while-revalidate=600"))
              .andExpect(cookie().doesNotExist("SESSION"))
              .andReturn();
      assertNull(anonymous.getRequest().getSession(false));
      var privileged =
          mvc.perform(get(route).with(user("cache-admin@example.test").roles("ADMIN")))
              .andExpect(status().isOk())
              .andReturn();
      String html = anonymous.getResponse().getContentAsString();
      assertEquals(html, privileged.getResponse().getContentAsString());
      assertFalse(html.contains("name=\"_csrf\""));
      assertFalse(html.contains("cache-admin@example.test"));
      assertTrue(html.contains("data-public-page=\"true\""));
    }
  }

  @Test
  void contextAnonymousDoesNotCreateSession() throws Exception {
    var response =
        mvc.perform(get("/storefront/context"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "private, no-store"))
            .andExpect(cookie().doesNotExist("SESSION"))
            .andExpect(jsonPath("$.signedIn").value(false))
            .andExpect(jsonPath("$.isAdmin").value(false))
            .andExpect(jsonPath("$.cartCount").value(0))
            .andReturn();
    assertNull(response.getRequest().getSession(false));
  }

  @Test
  void contextIsPrivateAndSeparatesTwoUsers() throws Exception {
    var added =
        mvc.perform(
                post("/cart/add")
                    .with(user(email).roles("CUSTOMER"))
                    .with(csrf())
                    .param("bookId", book.toString())
                    .param("quantity", "2"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    mvc.perform(
            get("/storefront/context")
                .cookie(added.getResponse().getCookie("SESSION"))
                .with(user(email).roles("CUSTOMER")))
        .andExpect(jsonPath("$.signedIn").value(true))
        .andExpect(jsonPath("$.isAdmin").value(false))
        .andExpect(jsonPath("$.cartCount").value(2))
        .andExpect(header().string("Cache-Control", "private, no-store"));
    mvc.perform(get("/storefront/context").with(user("another@example.test").roles("ADMIN")))
        .andExpect(jsonPath("$.cartCount").value(0))
        .andExpect(jsonPath("$.isAdmin").value(true));
  }

  @Test
  void publicCardObtainsRealSessionCsrfBeforePost() throws Exception {
    var response =
        mvc.perform(get("/storefront/csrf"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "private, no-store"))
            .andReturn();
    var token = new ObjectMapper().readTree(response.getResponse().getContentAsString());
    assertFalse(token.path("token").asText().isBlank());
    mvc.perform(
            post("/cart/add")
                .cookie(response.getResponse().getCookie("SESSION"))
                .header(token.path("headerName").asText(), token.path("token").asText())
                .param("bookId", book.toString()))
        .andExpect(status().is3xxRedirection());
    mvc.perform(post("/cart/add").param("bookId", book.toString()))
        .andExpect(status().isForbidden())
        .andExpect(header().string("Cache-Control", "private, no-store"));
  }

  @Test
  void contentVersionedAssetsAndCssFontHaveImmutableCaching() throws Exception {
    String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();
    var css = java.util.regex.Pattern.compile("/site-[a-f0-9]{32}\\.css").matcher(html);
    assertTrue(css.find(), "Template must link the content-versioned stylesheet");
    String content =
        mvc.perform(get(css.group()))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"))
            .andExpect(cookie().doesNotExist("SESSION"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(content.matches("(?s).*/fonts/Manrope-[a-f0-9]{32}\\.ttf.*"));
    mvc.perform(get("/site.css"))
        .andExpect(header().string("Cache-Control", "public, max-age=0, must-revalidate"));
    mvc.perform(get("/images/missing-00000000000000000000000000000000.webp"))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Cache-Control", "private, no-store"));
    mvc.perform(get("/login")).andExpect(header().string("Cache-Control", "private, no-store"));
  }

  @Test
  void catalogReadsUseOneJoinAndFeaturedIsLimited() {
    var stats = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    stats.clear();
    assertTrue(catalog.featured().size() <= 8);
    assertEquals(1, stats.getPrepareStatementCount());
    stats.clear();
    assertFalse(catalog.all().isEmpty());
    assertEquals(1, stats.getPrepareStatementCount());
    stats.setStatisticsEnabled(false);
  }
}
