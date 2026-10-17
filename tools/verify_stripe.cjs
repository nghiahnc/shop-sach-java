const fs = require("node:fs"),
  path = require("node:path"),
  assert = require("node:assert/strict"),
  crypto = require("node:crypto");
const root = path.resolve(__dirname, ".."),
  env = JSON.parse(
    fs.readFileSync(path.join(root, ".local/env-production.json")),
  ),
  saved = JSON.parse(
    fs.readFileSync(path.join(root, ".local/stripe-checkout.json")),
  );
const Stripe = require("stripe"),
  { Client } = require("pg");
(async () => {
  const stripe = new Stripe(env.STRIPE_SECRET_KEY);
  const id = new URL(saved.url).pathname.split("/").at(-1);
  const session = await stripe.checkout.sessions.retrieve(id, {
    expand: ["payment_intent"],
  });
  console.log({
    checkout: session.status,
    payment: session.payment_status,
    intent: session.payment_intent?.status,
    lastError: session.payment_intent?.last_payment_error?.code,
  });
  const u = new URL(env.DB_DIRECT_URL.replace("jdbc:", ""));
  const db = new Client({
    host: u.hostname,
    database: u.pathname.slice(1),
    user: env.DB_USER,
    password: env.DB_PASSWORD,
    ssl: { rejectUnauthorized: true },
  });
  await db.connect();
  try {
    const orderId = session.metadata.order_id;
    const order = (
      await db.query(
        "select id,code,status,paid,session_id,charged_currency,charged_amount,stock_released from orders where id=$1",
        [orderId],
      )
    ).rows[0];
    console.log({ orderStatus: order.status, paid: order.paid });
    if (session.payment_status !== "paid") {
      fs.writeFileSync(
        path.join(root, "artifacts/production/stripe-pending.json"),
        JSON.stringify(
          {
            orderId,
            checkout: session.status,
            payment: session.payment_status,
            orderStatus: order.status,
            paid: order.paid,
            lastError: session.payment_intent?.last_payment_error?.code,
          },
          null,
          2,
        ),
      );
      return;
    }
    assert.equal(session.livemode, false);
    assert.equal(session.status, "complete");
    assert.equal(order.paid, true);
    assert.equal(order.status, "CONFIRMED");
    assert.equal(order.session_id, session.id);
    assert.equal(Number(order.charged_amount), session.amount_total);
    assert.equal(order.charged_currency, session.currency);
    assert.equal(order.stock_released, false);
    const event = (
      await stripe.events.list({
        type: "checkout.session.completed",
        limit: 50,
      })
    ).data.find((e) => e.data.object.id === session.id);
    assert.ok(event);
    assert.equal(
      (
        await db.query(
          "select count(*)::int from payment_events where event_id=$1",
          [event.id],
        )
      ).rows[0].count,
      1,
      "Real webhook must be persisted before replay",
    );
    const stock = await db.query(
      "select l.book_id,b.stock from order_lines l join books b on b.id=l.book_id where l.order_id=$1",
      [orderId],
    );
    const payload = JSON.stringify(event);
    const timestamp = Math.floor(Date.now() / 1000);
    const signature = crypto
      .createHmac("sha256", env.STRIPE_WEBHOOK_SECRET)
      .update(timestamp + "." + payload)
      .digest("hex");
    const replay = await fetch(env.APP_BASE_URL + "/payments/stripe/webhook", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Stripe-Signature": `t=${timestamp},v1=${signature}`,
      },
      body: payload,
    });
    assert.equal(replay.status, 200);
    assert.equal((await replay.json()).applied, false);
    const after = await db.query(
      "select l.book_id,b.stock from order_lines l join books b on b.id=l.book_id where l.order_id=$1",
      [orderId],
    );
    assert.deepEqual(after.rows, stock.rows);
    const proof = {
      orderId,
      code: order.code,
      sessionId: session.id,
      livemode: false,
      checkout: session.status,
      payment: session.payment_status,
      currency: session.currency,
      amount: session.amount_total,
      eventId: event.id,
      realWebhookPersisted: true,
      signedReplayApplied: false,
      inventoryUnchangedOnReplay: true,
      verifiedAt: new Date().toISOString(),
    };
    fs.writeFileSync(
      path.join(root, "artifacts/production/stripe-proof.json"),
      JSON.stringify(proof, null, 2),
    );
    console.log(
      "PASS real Stripe sandbox paid, real webhook persisted, signed duplicate ignored",
    );
  } finally {
    await db.end();
  }
})().catch((e) => {
  console.error(e.message);
  process.exitCode = 1;
});
