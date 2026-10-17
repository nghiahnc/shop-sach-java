const fs = require("node:fs"),
  path = require("node:path"),
  { spawnSync } = require("node:child_process");
const root = path.resolve(__dirname, "..");
const { projectId } = JSON.parse(
  fs.readFileSync(path.join(root, ".vercel/project.json")),
);
const env = JSON.parse(
  fs.readFileSync(path.join(root, ".local/env-production.json")),
);
const Stripe = require("stripe");
const cache = path.join(process.env.LOCALAPPDATA, "npm-cache/_npx");
const cli = fs
  .readdirSync(cache)
  .map((d) => path.join(cache, d, "node_modules/vercel/dist/index.js"))
  .find(
    (p) =>
      fs.existsSync(p) &&
      JSON.parse(fs.readFileSync(path.resolve(p, "../../package.json")))
        .version === "64.3.1",
  );
function api(endpoint, method, input) {
  const r = spawnSync(
    process.execPath,
    [
      cli,
      "api",
      endpoint,
      "--method",
      method,
      "--input",
      input,
      "--header",
      "Content-Type: application/json",
      "--silent",
    ],
    { cwd: root, encoding: "utf8", timeout: 120000, windowsHide: true },
  );
  if (r.status) {
    let detail = r.stderr;
    for (const value of Object.values(env))
      if (String(value).length > 5)
        detail = detail.replaceAll(String(value), "[redacted]");
    throw Error("Vercel configuration failed: " + detail);
  }
}
(async () => {
  const origin = "https://shop-sach-java.vercel.app";
  env.APP_BASE_URL = origin;
  env.PORT = "8080";
  env.APP_PROFILE = "default";
  const marker = path.join(root, "artifacts/stripe-webhook.json");
  if (!fs.existsSync(marker)) {
    const stripe = new Stripe(env.STRIPE_SECRET_KEY);
    const hook = await stripe.webhookEndpoints.create({
      url: origin + "/payments/stripe/webhook",
      description: "Trạm Sách Java portfolio SANDBOX",
      enabled_events: [
        "checkout.session.completed",
        "checkout.session.expired",
        "checkout.session.async_payment_succeeded",
        "checkout.session.async_payment_failed",
      ],
      metadata: { shop: "tram-sach-java" },
    });
    env.STRIPE_WEBHOOK_SECRET = hook.secret;
    fs.writeFileSync(
      marker,
      JSON.stringify(
        { id: hook.id, url: hook.url, livemode: hook.livemode },
        null,
        2,
      ),
    );
  } else {
    const old = JSON.parse(
      fs.readFileSync(path.join(root, ".local/env-production.json")),
    );
    env.STRIPE_WEBHOOK_SECRET = old.STRIPE_WEBHOOK_SECRET;
  }
  fs.writeFileSync(
    path.join(root, ".local/env-production.json"),
    JSON.stringify(env, null, 2),
  );
  const body = path.join(root, ".local/vercel-env.json");
  for (const [key, value] of Object.entries(env)) {
    const result = spawnSync(
      process.execPath,
      [cli, "env", "add", key, "production", "--force"],
      {
        cwd: root,
        input: String(value),
        encoding: "utf8",
        timeout: 60000,
        windowsHide: true,
      },
    );
    if (result.status) throw Error("Cannot set production variable " + key);
  }
  const settings = path.join(root, ".local/vercel-settings.json");
  fs.writeFileSync(
    settings,
    JSON.stringify({ ssoProtection: null, framework: "container" }),
  );
  api(`/v9/projects/${projectId}`, "PATCH", settings);
  console.log(
    "Production environment configured; separate Stripe TEST webhook; public storefront with protected Spring admin.",
  );
})().catch((e) => {
  console.error(e.message);
  process.exitCode = 1;
});
