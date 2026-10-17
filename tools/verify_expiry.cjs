const fs = require("node:fs"),
  path = require("node:path"),
  assert = require("node:assert/strict");
const root = path.resolve(__dirname, ".."),
  env = JSON.parse(
    fs.readFileSync(path.join(root, ".local/env-production.json")),
  ),
  saved = JSON.parse(
    fs.readFileSync(path.join(root, ".local/expiry-checkout.json")),
  );
const Stripe = require("stripe"),
  { Client } = require("pg");
(async () => {
  const stripe = new Stripe(env.STRIPE_SECRET_KEY),
    sessionId = new URL(saved.url).pathname.split("/").at(-1);
  const session = await stripe.checkout.sessions.retrieve(sessionId);
  assert.equal(session.livemode, false);
  assert.equal(session.status, "open");
  assert.equal(session.metadata.shop, "tram-sach-java");
  const orderId = session.metadata.order_id;
  const u = new URL(env.DB_DIRECT_URL.replace("jdbc:", "")),
    db = new Client({
      host: u.hostname,
      database: u.pathname.slice(1),
      user: env.DB_USER,
      password: env.DB_PASSWORD,
      ssl: { rejectUnauthorized: true },
    });
  await db.connect();
  try {
    const before = (
      await db.query(
        "select l.book_id,l.quantity,b.stock from order_lines l join books b on b.id=l.book_id where l.order_id=$1",
        [orderId],
      )
    ).rows;
    await stripe.checkout.sessions.expire(sessionId);
    let order;
    for (let i = 0; i < 20; i++) {
      order = (
        await db.query(
          "select status,paid,stock_released from orders where id=$1",
          [orderId],
        )
      ).rows[0];
      if (order.status === "EXPIRED") break;
      await new Promise((r) => setTimeout(r, 1000));
    }
    assert.equal(order.status, "EXPIRED");
    assert.equal(order.paid, false);
    assert.equal(order.stock_released, true);
    const after = (
      await db.query(
        "select l.book_id,l.quantity,b.stock from order_lines l join books b on b.id=l.book_id where l.order_id=$1",
        [orderId],
      )
    ).rows;
    before.forEach((b, i) =>
      assert.equal(after[i].stock, b.stock + b.quantity),
    );
    const event = (
      await stripe.events.list({ type: "checkout.session.expired", limit: 50 })
    ).data.find((e) => e.data.object.id === sessionId);
    assert.ok(event);
    assert.equal(
      (
        await db.query(
          "select count(*)::int from payment_events where event_id=$1",
          [event.id],
        )
      ).rows[0].count,
      1,
    );
    fs.writeFileSync(
      path.join(root, "artifacts/production/stripe-expiry-proof.json"),
      JSON.stringify(
        {
          orderId,
          sessionId,
          eventId: event.id,
          trigger: "Stripe sandbox expire API",
          realExpiredWebhook: true,
          status: order.status,
          paid: false,
          stockReleased: true,
          before,
          after,
          verifiedAt: new Date().toISOString(),
        },
        null,
        2,
      ),
    );
    console.log("PASS real sandbox expiration webhook restored reserved stock");
  } finally {
    await db.end();
  }
})().catch((e) => {
  console.error(e.message);
  process.exitCode = 1;
});
