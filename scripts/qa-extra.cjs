const fs = require("node:fs"),
  path = require("node:path"),
  assert = require("node:assert/strict"),
  crypto = require("node:crypto");
const pw = require("playwright");
const root = path.resolve(__dirname, ".."),
  base = process.argv[2] || "http://localhost:8080",
  env = JSON.parse(
    fs.readFileSync(path.join(root, ".local/env-development.json")),
  ),
  out = path.join(root, "artifacts/local");
(async () => {
  const b = await pw.chromium.launch({ channel: "msedge", headless: true });
  try {
    const p = await b.newPage({ viewport: { width: 375, height: 850 } });
    p.setDefaultTimeout(60000);
    await p.goto(base + "/login");
    await p.keyboard.press("Tab");
    assert.equal(await p.locator(":focus").innerText(), "Đến nội dung chính");
    const email =
        "reader-" + crypto.randomBytes(4).toString("hex") + "@tramsach.demo",
      password = "DemoReader12345!";
    await p.goto(base + "/register");
    await p.getByLabel("Họ và tên", { exact: true }).fill("Bạn đọc mới");
    await p.getByLabel("Email", { exact: true }).fill(email);
    await p.getByLabel("Mật khẩu", { exact: true }).fill(password);
    await p.getByRole("button", { name: "Tạo tài khoản", exact: true }).click();
    await p.waitForURL("**/login?registered");
    await p.getByLabel("Email", { exact: true }).fill(email);
    await p.getByLabel("Mật khẩu", { exact: true }).fill(password);
    await p.getByRole("button", { name: "Đăng nhập", exact: true }).click();
    await p.waitForURL((url) => !url.pathname.endsWith("/login"));
    await p.goto(base + "/account/orders");
    await p.getByText("Chưa có đơn hàng nào.", { exact: true }).waitFor();
    await p.goto(base + "/books?min=150000&max=200000&sort=priceDesc");
    const prices = await p.locator(".book-bottom strong").allTextContents();
    const nums = prices.map((s) => Number(s.replace(/[^0-9]/g, "")));
    assert.ok(nums.every((n) => n >= 150000 && n <= 200000));
    assert.deepEqual(
      nums,
      [...nums].sort((a, b) => b - a),
    );
    await p.goto(base + "/books?page=1");
    assert.equal(await p.locator(".book-card").count(), 9);
    assert.equal(await p.locator("[aria-current=page]").innerText(), "2");
    await p.emulateMedia({ reducedMotion: "reduce" });
    await p.goto(base + "/");
    assert.equal(
      await p
        .locator(".cover-wrap img")
        .first()
        .evaluate((e) => getComputedStyle(e).transitionDuration),
      "0s",
    );
    await p.evaluate(() => (document.body.style.zoom = "1.25"));
    assert.equal(
      await p.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
      true,
    );
    await p.screenshot({
      path: path.join(out, "mobile-zoom-125.png"),
      fullPage: true,
    });
    const admin = await b.newPage();
    admin.setDefaultTimeout(60000);
    await admin.goto(base + "/login");
    await admin.getByLabel("Email", { exact: true }).fill(env.ADMIN_EMAIL);
    await admin
      .getByLabel("Mật khẩu", { exact: true })
      .fill(env.ADMIN_PASSWORD);
    await admin.getByRole("button", { name: "Đăng nhập", exact: true }).click();
    await admin.waitForURL((url) => !url.pathname.endsWith("/login"));
    await admin.goto(base + "/admin/categories");
    await admin
      .getByLabel("Thể loại mới", { exact: true })
      .fill("Góc thử nghiệm");
    await admin
      .getByRole("button", { name: "Thêm thể loại", exact: true })
      .click();
    await admin.waitForURL("**/admin/categories");
    const row = admin
      .locator("form")
      .filter({ has: admin.locator('input[value="Góc thử nghiệm"]') });
    await row.locator("input[type=checkbox]").uncheck();
    await row.getByRole("button", { name: "Lưu", exact: true }).click();
    await admin.goto(base + "/admin/books/new");
    await admin
      .getByLabel("Tên sách", { exact: true })
      .fill("Bản đọc thử — chưa mở bán");
    await admin.getByLabel("Tác giả", { exact: true }).fill("Trạm Sách");
    await admin.getByLabel("Đơn giá (VND)", { exact: true }).fill("149000");
    await admin.getByLabel("Tồn kho khả dụng", { exact: true }).fill("2");
    await admin
      .getByLabel("Giới thiệu", { exact: true })
      .fill("Bản đọc thử minh họa thao tác thêm sách trong trang quản trị.");
    await admin.getByLabel("Đang bán", { exact: true }).uncheck();
    await admin
      .getByRole("button", { name: "Lưu đầu sách", exact: true })
      .click();
    await admin.waitForURL("**/admin/books");
    await p.goto(base + "/books?q=Bản+đọc+thử");
    assert.equal(await p.locator(".book-card").count(), 0);
    const proof = {
      registration: true,
      keyboardSkipLink: true,
      priceFilteringAndSorting: true,
      pagination: true,
      reducedMotion: true,
      zoom125NoOverflow: true,
      adminCreatesCategory: true,
      adminDisablesCategory: true,
      adminCreatesInactiveBook: true,
      inactiveBookHidden: true,
      date: new Date().toISOString(),
    };
    fs.writeFileSync(
      path.join(out, "qa-extra.json"),
      JSON.stringify(proof, null, 2),
    );
    console.log(
      "PASS registration, keyboard, filters, pagination, reduced motion, zoom, admin category/book creation",
    );
  } finally {
    await b.close();
  }
})().catch((e) => {
  console.error(e.message);
  process.exitCode = 1;
});
