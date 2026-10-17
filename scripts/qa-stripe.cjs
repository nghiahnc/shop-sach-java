const fs = require("node:fs"),
  path = require("node:path"),
  assert = require("node:assert/strict");
const pw = require("playwright");
const root = path.resolve(__dirname, ".."),
  base = "https://shop-sach-java.vercel.app",
  env = JSON.parse(
    fs.readFileSync(path.join(root, ".local/env-production.json")),
  );
const out = path.join(root, "artifacts/production");
fs.mkdirSync(out, { recursive: true });
(async () => {
  const browser = await pw.chromium.launch({
    channel: "msedge",
    headless: true,
  });
  try {
    const page = await browser.newPage({
      viewport: { width: 1280, height: 1000 },
    });
    page.setDefaultTimeout(60000);
    page.setDefaultNavigationTimeout(120000);
    await page.goto(base + "/login");
    await page.getByLabel("Email", { exact: true }).fill(env.CUSTOMER_EMAIL);
    await page
      .getByLabel("Mật khẩu", { exact: true })
      .fill(env.CUSTOMER_PASSWORD);
    await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
    await page.waitForURL((url) => !url.pathname.endsWith("/login"));
    await page.goto(base + "/books/00000000-0000-0000-0000-00000000006b");
    await page
      .getByRole("button", { name: "Thêm vào giỏ sách", exact: true })
      .click();
    await page.waitForURL("**/cart");
    await page.getByRole("link", { name: "Tiến hành đặt hàng" }).click();
    await page.waitForURL("**/checkout");
    await page
      .getByLabel("Người nhận", { exact: true })
      .fill("Bạn đọc Stripe Sandbox");
    await page.getByLabel("Số điện thoại", { exact: true }).fill("0901234567");
    await page
      .getByLabel("Địa chỉ nhận hàng", { exact: true })
      .fill("123 Đường Sách, Cần Thơ (sandbox)");
    await page.locator("#stripe").check();
    await page
      .getByRole("button", { name: "Xác nhận đặt hàng", exact: true })
      .click();
    await page.waitForURL(
      (url) =>
        url.hostname === "checkout.stripe.com" ||
        url.pathname.startsWith("/account/orders/"),
    );
    if (!page.url().includes("checkout.stripe.com")) {
      await page.screenshot({
        path: path.join(out, "stripe-start-error.png"),
        fullPage: true,
      });
      throw Error("Stripe start returned to order: " + page.url());
    }
    fs.writeFileSync(
      path.join(root, ".local/stripe-checkout.json"),
      JSON.stringify({
        url: page.url(),
        storage: await page.context().storageState(),
      }),
    );
    await page.screenshot({
      path: path.join(out, "stripe-checkout.png"),
      fullPage: true,
    });
    console.log("Stripe hosted checkout opened");
    const inputs = await page
      .locator("input")
      .evaluateAll((nodes) =>
        nodes.map((n) => ({
          name: n.name,
          id: n.id,
          type: n.type,
          placeholder: n.placeholder,
        })),
      );
    console.log(JSON.stringify(inputs));
    fs.writeFileSync(
      path.join(root, ".local/stripe-inputs.json"),
      JSON.stringify(inputs),
    );
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e.message);
  process.exitCode = 1;
});
