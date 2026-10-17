const fs = require("node:fs"),
  path = require("node:path");
const pw = require("playwright");
const root = path.resolve(__dirname, ".."),
  saved = JSON.parse(
    fs.readFileSync(path.join(root, ".local/stripe-checkout.json")),
  );
(async () => {
  const browser = await pw.chromium.launch({
    channel: "msedge",
    headless: true,
  });
  try {
    const context = await browser.newContext({
      storageState: saved.storage,
      viewport: { width: 1280, height: 1000 },
    });
    const page = await context.newPage();
    page.setDefaultTimeout(60000);
    await page.goto(saved.url, { timeout: 120000 });
    await page.locator("input").first().waitFor();
    await page.locator("#email").fill("stripe-demo@tramsach.demo");
    await page.locator("#cardNumber").fill("4000000000000002");
    await page.locator("#cardExpiry").fill("1230");
    await page.locator("#cardCvc").fill("123");
    await page.locator("#billingName").fill("Tram Sach Sandbox");
    await page.getByRole("button", { name: "Pay", exact: true }).click();
    await page
      .getByText(/declined/i)
      .first()
      .waitFor();
    await page.screenshot({
      path: path.join(root, "artifacts/production/stripe-declined.png"),
      fullPage: true,
    });
    console.log("PASS Stripe sandbox declined test card");
    await page.locator("#cardNumber").fill("4242424242424242");
    await page.getByRole("button", { name: "Pay", exact: true }).click();
    await page.waitForURL(
      "https://shop-sach-java.vercel.app/account/orders/**",
      { timeout: 120000 },
    );
    fs.writeFileSync(
      path.join(root, "artifacts/production/stripe-return.json"),
      JSON.stringify({ url: page.url(), declinedThenRetried: true }, null, 2),
    );
    await page.screenshot({
      path: path.join(root, "artifacts/production/stripe-receipt.png"),
      fullPage: true,
    });
    console.log("PASS Stripe sandbox retry returned to receipt");
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e.message);
  process.exitCode = 1;
});
