import { kkiapay } from "npm:@kkiapay-org/nodejs-sdk";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const publicKey = Deno.env.get("KKIAPAY_PUBLIC_KEY")!;
const privateKey = Deno.env.get("KKIAPAY_PRIVATE_KEY")!;
const secretKey = Deno.env.get("KKIAPAY_SECRET_KEY")!;

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("Method Not Allowed", { status: 405 });
  try {
    const body = await req.json();
    const transactionId = String(body.transactionId ?? "").trim();
    const partnerId = String(body.partnerId ?? "").trim();
    if (!transactionId || !partnerId) return new Response("Missing transactionId/partnerId", { status: 400 });

    // Never trust the mobile callback/webhook amount or status by itself.
    // Verify the transaction directly with KKIAPAY using server-only credentials.
    const k = kkiapay({ privatekey: privateKey, publickey: publicKey, secretkey: secretKey, sandbox: false });
    const verified = await k.verify(transactionId);
    const verifiedAmount = Number(verified?.amount ?? 0);
    const verifiedStatus = String(verified?.status ?? "").toUpperCase();
    const success = verifiedStatus === "SUCCESS";

    const rpc = await fetch(`${supabaseUrl}/rest/v1/rpc/confirm_kkiapay_payment`, {
      method: "POST",
      headers: {
        apikey: serviceRoleKey,
        Authorization: `Bearer ${serviceRoleKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        p_payment_id: partnerId,
        p_transaction_id: transactionId,
        p_amount: verifiedAmount,
        p_success: success,
      }),
    });
    if (!rpc.ok) throw new Error(await rpc.text());
    return Response.json({ ok: true, status: verifiedStatus, transactionId });
  } catch (e) {
    console.error(e);
    return new Response("Webhook rejected", { status: 400 });
  }
});
