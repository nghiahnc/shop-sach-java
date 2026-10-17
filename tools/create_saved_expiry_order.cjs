const fs = require("node:fs"),
  path = require("node:path"),
  assert = require("node:assert/strict"),
  { request } = require("playwright");
const root = path.resolve(__dirname, ".."),
  e = JSON.parse(
    fs.readFileSync(path.join(root, ".local/env-production.json")),
  ),
  base = e.APP_BASE_URL;
function field(html, name) {
  const re = new RegExp('<input[^>]*name="' + name + '"[^>]*value="([^"]*)"');
  const match = html.match(re);
  if (!match) throw Error("Missing form field " + name);
  return match[1];
}
(async () => {
  const api = await request.newContext();
  try {
    let res = await api.get(base + "/login");
    let html = await res.text();
    let csrf = field(html, "_csrf");
    res = await api.post(base + "/login", {
      form: {
        username: "demo@tramsach.demo",
        password: "TramSachDemo2026!",
        _csrf: csrf,
      },
      maxRedirects: 0,
    });
    assert.equal(res.status(), 302);
    res = await api.get(base + "/cart");
    html = await res.text();
    csrf = field(html, "_csrf");
    res = await api.post(base + "/cart/add", {
      form: {
        bookId: "00000000-0000-0000-0000-00000000006b",
        quantity: "1",
        _csrf: csrf,
      },
      maxRedirects: 0,
    });
    assert.equal(res.status(), 302);
    res = await api.get(base + "/checkout");
    html = await res.text();
    csrf = field(html, "_csrf");
    const key = field(html, "key");
    res = await api.post(base + "/checkout", {
      form: {
        name: "Bạn đọc kiểm thử hết hạn",
        phone: "0901234567",
        address: "123 Đường Sách, Cần Thơ",
        method: "STRIPE",
        key,
        _csrf: csrf,
      },
      maxRedirects: 0,
    });
    assert.equal(res.status(), 302);
    const url = res.headers().location;
    assert.ok(url.startsWith("https://checkout.stripe.com/"));
    fs.writeFileSync(
      path.join(root, ".local/upgrade-expiry-checkout.json"),
      JSON.stringify({ url }),
    );
    console.log(
      "Sandbox expiration order created through authenticated CSRF-protected checkout",
    );
  } finally {
    await api.dispose();
  }
})().catch((e) => {
  console.error(e.message);
  process.exitCode = 1;
});
