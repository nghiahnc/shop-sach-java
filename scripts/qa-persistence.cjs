const fs = require("node:fs"),
  path = require("node:path"),
  assert = require("node:assert/strict");
const pw = require("playwright");
const root = path.resolve(__dirname, ".."),
  mode = process.argv[2] || "prepare",
  base = process.argv[3] || "http://localhost:8080",
  branch = base.startsWith("https:") ? "production" : "development",
  env = JSON.parse(
    fs.readFileSync(path.join(root, `.local/env-${branch}.json`)),
  );
const file = path.join(root, `.local/persistence-${branch}.json`),
  out = path.join(
    root,
    `artifacts/${branch === "production" ? "production" : "local"}`,
  );
(async () => {
  const browser = await pw.chromium.launch({
    channel: "msedge",
    headless: true,
  });
  try {
    const saved = mode === "verify" ? JSON.parse(fs.readFileSync(file)) : null;
    const context = await browser.newContext(
        saved ? { storageState: saved } : {},
      ),
      page = await context.newPage();
    page.setDefaultTimeout(60000);
    page.setDefaultNavigationTimeout(120000);
    if (mode === "prepare") {
      await page.goto(base + "/login");
      await page.getByLabel("Email", { exact: true }).fill(env.CUSTOMER_EMAIL);
      await page
        .getByLabel("Mật khẩu", { exact: true })
        .fill(env.CUSTOMER_PASSWORD);
      await page
        .getByRole("button", { name: "Đăng nhập", exact: true })
        .click();
      await page.waitForURL((url) => !url.pathname.endsWith("/login"));
      await page.goto(base + "/books/00000000-0000-0000-0000-00000000006d");
      await page
        .getByRole("button", { name: "Thêm vào giỏ sách", exact: true })
        .click();
      await page.waitForURL("**/cart");
      fs.writeFileSync(file, JSON.stringify(await context.storageState()));
      console.log("PASS persistence cart prepared");
    } else {
      const r = await page.goto(base + "/cart");
      assert.equal(r.status(), 200);
      await page
        .getByRole("heading", {
          name: "Java từ những điều cơ bản",
          exact: true,
        })
        .waitFor();
      await page
        .getByRole("link", { name: "Đơn của tôi", exact: true })
        .waitFor();
      await page.screenshot({
        path: path.join(out, "cart-after-restart.png"),
        fullPage: true,
      });
      fs.writeFileSync(
        path.join(out, "persistence.json"),
        JSON.stringify(
          {
            base,
            verified: true,
            sessionAuthenticated: true,
            cartRestored: true,
            after:
              branch === "production" ? "Vercel redeployment" : "JVM restart",
            date: new Date().toISOString(),
          },
          null,
          2,
        ),
      );
      await page.getByRole("button", { name: "Xóa", exact: true }).click();
      console.log(
        "PASS authenticated session and cart survived " + branch + " restart",
      );
    }
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e.message);
  process.exitCode = 1;
});
