const fs = require("node:fs");
const path = require("node:path");
const { spawnSync } = require("node:child_process");
const { randomBytes } = require("node:crypto");
const root = path.resolve(__dirname, "..");
const cli = path.join(process.env.APPDATA, "npm/node_modules/neon/dist/cli.js");
function neon(args) {
  const r = spawnSync(process.execPath, [cli, ...args, "--output", "json"], {
    cwd: root,
    encoding: "utf8",
    timeout: 120000,
  });
  if (r.status) throw Error("Neon operation failed: " + args[0]);
  try {
    return JSON.parse(r.stdout);
  } catch {
    return r.stdout.trim();
  }
}
function strings(v) {
  return typeof v === "string"
    ? [v]
    : v && typeof v === "object"
      ? Object.values(v).flatMap(strings)
      : [];
}
function envFor(branch, project) {
  const args = [
    branch,
    "--project-id",
    project,
    "--database-name",
    "tram_sach",
    "--role-name",
    "tram_sach_owner",
  ];
  const direct = strings(neon(["connection-string", ...args])).find((v) =>
    v.startsWith("postgres"),
  );
  const pooled = strings(neon(["connection-string", ...args, "--pooled"])).find(
    (v) => v.startsWith("postgres"),
  );
  const u = new URL(direct),
    p = new URL(pooled);
  const jdbc = (u) =>
    `jdbc:postgresql://${u.host}${u.pathname}?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`;
  return {
    DB_URL: jdbc(p),
    DB_DIRECT_URL: jdbc(u),
    DB_USER: decodeURIComponent(u.username),
    DB_PASSWORD: decodeURIComponent(u.password),
  };
}
fs.mkdirSync(path.join(root, ".local"), { recursive: true });
const project = JSON.parse(
  fs.readFileSync(path.join(root, "artifacts/neon-project.json")),
).project.id;
let branches = neon(["branches", "list", "--project-id", project]);
branches = branches.branches || branches;
const prod = branches.find((v) => v.default);
if (prod.name !== "production")
  neon(["branches", "rename", prod.id, "production", "--project-id", project]);
for (const name of ["development", "test"])
  if (!branches.some((b) => b.name === name))
    neon([
      "branches",
      "create",
      "--project-id",
      project,
      "--parent",
      "production",
      "--name",
      name,
      "--cu",
      "0.25",
      "--no-secrets",
    ]);
const { parse } = require("dotenv");
const stripe = parse(
  fs.readFileSync(path.join(root, "../shop-cafe/.env.local")),
).STRIPE_SECRET_KEY;
if (!stripe?.startsWith("sk_test_"))
  throw Error("Stripe sandbox key unavailable");
const secretsFile = path.join(root, ".local/bootstrap.json");
const secrets = fs.existsSync(secretsFile)
  ? JSON.parse(fs.readFileSync(secretsFile))
  : {
      ADMIN_PASSWORD: randomBytes(15).toString("base64url"),
      CUSTOMER_PASSWORD: randomBytes(12).toString("base64url"),
      STRIPE_WEBHOOK_SECRET: "whsec_" + randomBytes(24).toString("hex"),
    };
fs.writeFileSync(secretsFile, JSON.stringify(secrets));
for (const name of ["production", "development", "test"]) {
  fs.writeFileSync(
    path.join(root, `.local/env-${name}.json`),
    JSON.stringify(
      {
        ...envFor(name, project),
        ...secrets,
        ADMIN_EMAIL: "admin@tramsach.demo",
        CUSTOMER_EMAIL: "ban-doc@tramsach.demo",
        STRIPE_SECRET_KEY: stripe,
        STRIPE_CURRENCY: "vnd",
        APP_BASE_URL: "http://localhost:8080",
        PORT: "8080",
      },
      null,
      2,
    ),
  );
  console.log(name + ": isolated JDBC configuration saved");
}
fs.writeFileSync(
  path.join(root, "artifacts/neon-branches.json"),
  JSON.stringify(neon(["branches", "list", "--project-id", project]), null, 2),
);
