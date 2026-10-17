const fs = require("node:fs"),
  path = require("node:path"),
  assert = require("node:assert/strict");
const playwright = require("playwright");
const root = path.resolve(__dirname, ".."),
  base = process.argv[2] || "http://localhost:8080",
  branch = process.argv[3] || "development";
const env = JSON.parse(
  fs.readFileSync(path.join(root, `.local/env-${branch}.json`)),
);
const out = path.join(
  root,
  "artifacts",
  base.startsWith("https:") ? "production" : "local",
);
fs.mkdirSync(out, { recursive: true });
const results = [];
function pass(name, details) {
  results.push({ name, passed: true, ...details });
  console.log("PASS " + name);
}
(async () => {
  const browser = await playwright.chromium.launch({
    channel: "msedge",
    headless: true,
  });
  try {
    const context = await browser.newContext({
        viewport: { width: 1440, height: 1000 },
      }),
      page = await context.newPage();
    page.setDefaultTimeout(60000);
    page.setDefaultNavigationTimeout(120000);
    const errors = [];
    page.on("pageerror", (e) => errors.push(e.message));
    for (const width of [375, 768, 1440]) {
      await page.setViewportSize({ width, height: width === 375 ? 850 : 1000 });
      const res = await page.goto(base + "/");
      assert.equal(res.status(), 200);
      await page.locator(".book-card img").first().waitFor();
      await page.evaluate(async () => {
        await Promise.all(
          Array.from(document.images).map((i) => i.decode().catch(() => {})),
        );
      });
      assert.equal(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
        true,
        "Horizontal overflow",
      );
      assert.equal(await page.locator(".book-card").count(), 9);
      assert.equal(
        await page
          .locator(".book-card img")
          .evaluateAll((images) =>
            images.every((i) => i.complete && i.naturalWidth > 0),
          ),
        true,
      );
      await page.screenshot({
        path: path.join(out, `home-${width}.png`),
        fullPage: true,
      });
      pass("responsive-" + width);
    }
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.goto(base + "/books?q=huong+doi+tuong");
    assert.equal(await page.locator(".book-card").count(), 1);
    pass("Vietnamese search without accents");
    await page.goto(
      base +
        "/books?category=00000000-0000-0000-0000-000000000003&sort=priceAsc",
    );
    assert.equal(await page.locator(".book-card").count(), 3);
    pass("category filter");
    await page.goto(base + "/login");
    await page.getByLabel("Email", { exact: true }).fill(env.CUSTOMER_EMAIL);
    await page.getByLabel("Mật khẩu", { exact: true }).fill("wrongPassword");
    await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
    await page.getByRole("alert").waitFor();
    pass("incorrect password rejected");
    await login(page, env.CUSTOMER_EMAIL, env.CUSTOMER_PASSWORD);
    const denied = await page.goto(base + "/admin");
    assert.equal(denied.status(), 403);
    pass("customer blocked from admin");
    await page.goto(base + "/books/00000000-0000-0000-0000-000000000065");
    await page
      .getByRole("button", { name: "Thêm vào giỏ sách", exact: true })
      .click();
    await page.waitForURL("**/cart");
    await page.getByLabel("Mã giảm giá", { exact: true }).fill("DOCSACH10");
    await page.getByRole("button", { name: "Áp dụng", exact: true }).click();
    await page.waitForURL("**/cart");
    await page.getByLabel("Số lượng", { exact: true }).fill("2");
    await page.getByRole("button", { name: "Cập nhật", exact: true }).click();
    await page.waitForURL("**/cart");
    await page.getByText("250.400 ₫", { exact: true }).waitFor();
    pass("cart quantity and coupon totals");
    await page.screenshot({ path: path.join(out, "cart.png"), fullPage: true });
    const storage = await context.storageState();
    fs.writeFileSync(
      path.join(root, ".local/qa-session.json"),
      JSON.stringify(storage),
    );
    await page.getByRole("link", { name: "Tiến hành đặt hàng" }).click();
    await page.waitForURL("**/checkout");
    await page.getByLabel("Người nhận", { exact: true }).fill("Bạn đọc Demo");
    await page.getByLabel("Số điện thoại", { exact: true }).fill("0901234567");
    await page
      .getByLabel("Địa chỉ nhận hàng", { exact: true })
      .fill("123 Đường Sách, Ninh Kiều, Cần Thơ");
    await page.screenshot({
      path: path.join(out, "checkout.png"),
      fullPage: true,
    });
    await page
      .getByRole("button", { name: "Xác nhận đặt hàng", exact: true })
      .click();
    await page.waitForURL("**/account/orders/*");
    const orderId = page.url().split("/").at(-1);
    await page.getByText("Đã xác nhận", { exact: true }).waitFor();
    await page.screenshot({
      path: path.join(out, "receipt-cod.png"),
      fullPage: true,
    });
    pass("COD checkout", { orderId });
    await page.goto(base + "/cart");
    await page
      .getByText("Giỏ sách đang chờ một câu chuyện.", { exact: true })
      .waitFor();
    pass("cart cleared after saved COD order");
    const adminContext = await browser.newContext({
        viewport: { width: 1440, height: 1000 },
      }),
      admin = await adminContext.newPage();
    admin.setDefaultTimeout(60000);
    admin.setDefaultNavigationTimeout(120000);
    await login(admin, env.ADMIN_EMAIL, env.ADMIN_PASSWORD);
    await admin.goto(base + "/admin");
    await admin.screenshot({
      path: path.join(out, "admin-dashboard.png"),
      fullPage: true,
    });
    for (const route of [
      "/admin/books",
      "/admin/books/new",
      "/admin/books/00000000-0000-0000-0000-000000000065",
      "/admin/categories",
      "/admin/coupons",
    ]) {
      const r = await admin.goto(base + route);
      assert.equal(r.status(), 200, route);
    }
    pass("admin pages");
    await admin.goto(base + "/admin/orders/" + orderId);
    await admin
      .getByRole("button", { name: "Bắt đầu giao", exact: true })
      .click();
    await admin
      .getByRole("button", { name: "Hoàn tất giao", exact: true })
      .click();
    await admin.getByText("Hoàn tất", { exact: true }).waitFor();
    await admin.getByText("COD · Đã thu tiền", { exact: true }).waitFor();
    pass("COD fulfillment recognizes revenue");
    await page.goto(base + "/account/orders/" + orderId);
    await page.getByText("Hoàn tất", { exact: true }).waitFor();
    await page.screenshot({
      path: path.join(out, "receipt-completed.png"),
      fullPage: true,
    });
    await page.goto(base + "/account/orders");
    pass("customer order history");
    await admin.goto(
      base + "/admin/books/00000000-0000-0000-0000-000000000065",
    );
    await admin
      .getByLabel("Giới thiệu", { exact: true })
      .fill(
        "Một lời nhắc dịu dàng để dừng lại và tìm lại nhịp sống của riêng mình. Đây là sách mẫu cho dự án Trạm Sách.",
      );
    await admin
      .getByRole("button", { name: "Lưu đầu sách", exact: true })
      .click();
    await admin.waitForURL("**/admin/books");
    pass("admin saves book");
    await admin.goto(base + "/admin/coupons");
    await admin.getByLabel("Mã (A–Z, 0–9)", { exact: true }).fill("QAONLY");
    await admin.getByLabel("Chính sách", { exact: true }).selectOption("FIXED");
    await admin.getByLabel("Giá trị giảm", { exact: true }).fill("10000");
    await admin
      .getByLabel("Đơn tối thiểu (VND)", { exact: true })
      .fill("100000");
    await admin.getByLabel("Hoạt động", { exact: true }).uncheck();
    await admin
      .getByRole("button", { name: "Lưu mã giảm giá", exact: true })
      .click();
    await admin.waitForURL("**/admin/coupons");
    pass("admin creates disabled coupon");
    await page.setViewportSize({ width: 375, height: 850 });
    await page.goto(base + "/cart");
    assert.equal(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
      true,
    );
    assert.equal(errors.length, 0);
    pass("no browser JavaScript errors");
    await browser.close();
    fs.writeFileSync(
      path.join(out, "qa-browser.json"),
      JSON.stringify(
        { base, date: new Date().toISOString(), results },
        null,
        2,
      ),
    );
  } finally {
    if (browser.isConnected()) await browser.close();
  }
})().catch((e) => {
  fs.writeFileSync(path.join(out, "qa-browser-failure.txt"), e.stack);
  console.error(e.message);
  process.exitCode = 1;
});
async function login(page, email, password) {
  await page.goto(base + "/login");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Mật khẩu", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await page.waitForURL((url) => !url.pathname.endsWith("/login"));
}
