const {onSchedule} = require("firebase-functions/v2/scheduler");
const {onCall, onRequest, HttpsError} = require("firebase-functions/v2/https");
const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const {defineSecret, defineString} = require("firebase-functions/params");
const {initializeApp} = require("firebase-admin/app");
const {getAuth} = require("firebase-admin/auth");
const {getFirestore, FieldValue, Timestamp} = require("firebase-admin/firestore");
const {getMessaging} = require("firebase-admin/messaging");

initializeApp();
const db = getFirestore();
const SIMPLE_QUOTA = 3;
const AGENCY_QUOTA = 15;
const EXTRA_QUOTA = 10;
const LISTING_DAYS = 7;
const EXTRA_PRICE = 5000;
const CERT_PRICE = 2000;
const CinetPayApiKey = defineSecret("CINETPAY_API_KEY");
const CinetPaySiteId = defineSecret("CINETPAY_SITE_ID");
const PublicBaseUrl = defineString("PUBLIC_BASE_URL", {default: ""});

function requireAuth(request) { if (!request.auth) throw new HttpsError("unauthenticated", "Vous devez être connecté."); return request.auth.uid; }
async function userData(uid) { const s = await db.collection("users").doc(uid).get(); if (!s.exists) throw new HttpsError("failed-precondition", "Profil introuvable."); return s.data(); }
async function activeCount(uid) { const snap = await db.collection("listings").where("ownerId", "==", uid).where("active", "==", true).get(); const now = Date.now(); return snap.docs.filter(d => d.get("expiresAt")?.toDate?.().getTime() > now).length; }

exports.publishListing = onCall(async (request) => {
  const uid = requireAuth(request); const data = request.data || {}; const user = await userData(uid);
  const mode = String(data.mode || ""); const propertyType = String(data.propertyType || ""); const price = Number(data.price || 0); const depositMonths = Number(data.depositMonths || 0); const description = String(data.description || "").slice(0, 4000); const relationship = String(data.relationship || "");
  const photoUrls = Array.isArray(data.photoUrls) ? data.photoUrls.map(String).slice(0, 4) : [];
  if (!["sale", "rent"].includes(mode)) throw new HttpsError("invalid-argument", "Type de publication invalide.");
  if (!propertyType || price <= 0 || !relationship) throw new HttpsError("invalid-argument", "Informations d'annonce incomplètes.");
  const agency = user.agency === true;
  const agencyDoc = agency ? await db.collection("agencies").doc(uid).get() : null;
  const a = agencyDoc?.data() || {};
  const limit = agency ? (a.extraSlotsPaid ? 25 : AGENCY_QUOTA) : SIMPLE_QUOTA;
  const agencyCertified = agency && a.certified === true && a.certificationExpiresAt?.toMillis?.() > Date.now();
  const count = await activeCount(uid);
  if (count >= limit) throw new HttpsError("resource-exhausted", `Limite atteinte : ${limit} annonces actives.`);
  const now = Timestamp.now(); const expiresAt = Timestamp.fromMillis(Date.now() + LISTING_DAYS * 86400000); const ref = db.collection("listings").doc();
  await ref.set({ownerId: uid, ownerName: String(user.firstName || ""), ownerPhone: String(user.phone || ""), ownerCountryCode: String(user.countryCode || ""), country: String(user.country || ""), city: String(user.city || ""), mode, propertyType, photoUrls, price: Math.floor(price), depositMonths: Math.max(0, Math.floor(depositMonths)), description, relationship, active: true, createdAt: now, expiresAt, views: 0, agencyId: agency ? uid : "", ownerAgencyName: agency ? String(a.name || "") : "", agencyCertified});
  return {listingId: ref.id, quota: limit};
});

exports.relistListing = onCall(async (request) => {
  const uid = requireAuth(request); const listingId = String(request.data?.listingId || ""); if (!listingId) throw new HttpsError("invalid-argument", "Annonce invalide.");
  const ref = db.collection("listings").doc(listingId); const snap = await ref.get(); if (!snap.exists || snap.get("ownerId") !== uid) throw new HttpsError("permission-denied", "Annonce inaccessible.");
  const user = await userData(uid); const agency = user.agency === true ? (await db.collection("agencies").doc(uid).get()).data() || {} : {}; const limit = user.agency ? (agency.extraSlotsPaid ? 25 : 15) : 3; const count = (await activeCount(uid)) - (snap.get("active") ? 1 : 0); if (count >= limit) throw new HttpsError("resource-exhausted", `Votre quota de ${limit} annonces actives est atteint.`);
  await ref.update({active: true, createdAt: FieldValue.serverTimestamp(), expiresAt: Timestamp.fromMillis(Date.now() + LISTING_DAYS * 86400000), expiredAt: FieldValue.delete()}); return {ok: true};
});
exports.deleteListing = onCall(async (request) => { const uid = requireAuth(request); const id = String(request.data?.listingId || ""); const ref = db.collection("listings").doc(id); const snap = await ref.get(); if (!snap.exists || snap.get("ownerId") !== uid) throw new HttpsError("permission-denied", "Annonce inaccessible."); await ref.delete(); return {ok: true}; });

exports.createAgency = onCall(async (request) => {
  const uid = requireAuth(request); const d = request.data || {}; const name = String(d.name || "").trim(); const address = String(d.address || "").trim(); const email = String(d.email || "").trim(); const logoUrl = String(d.logoUrl || "");
  if (!name || !address || !email || !email.includes("@")) throw new HttpsError("invalid-argument", "Nom, adresse et email sont obligatoires.");
  const userRef = db.collection("users").doc(uid); const agencyRef = db.collection("agencies").doc(uid);
  await db.runTransaction(async tx => { const us = await tx.get(userRef); const as = await tx.get(agencyRef); if (!us.exists) throw new HttpsError("failed-precondition", "Profil introuvable."); if (as.exists || us.get("agency") === true) throw new HttpsError("already-exists", "Une agence existe déjà."); tx.set(agencyRef, {ownerId: uid, name, address, email, logoUrl, country: us.get("country"), city: us.get("city"), certified: false, certificationStatus: "none", extraSlotsPaid: false, extraSlots: 0, createdAt: FieldValue.serverTimestamp()}); tx.update(userRef, {agency: true, agencyId: uid}); tx.set(db.collection("publicProfiles").doc(uid), {uid, firstName: us.get("firstName") || "", country: us.get("country") || "", city: us.get("city") || "", agency: true, agencyId: uid}, {merge: true}); });
  return {ok: true, quota: AGENCY_QUOTA};
});

exports.submitAgencyCertification = onCall(async (request) => {
  const uid = requireAuth(request); const d = request.data || {}; const frontUrl = String(d.frontUrl || ""); const backUrl = String(d.backUrl || ""); if (!frontUrl || !backUrl) throw new HttpsError("invalid-argument", "Recto et verso requis.");
  const ref = db.collection("agencies").doc(uid); const snap = await ref.get(); if (!snap.exists || snap.get("ownerId") !== uid) throw new HttpsError("failed-precondition", "Agence introuvable.");
  await ref.update({certificationStatus: "pending", verificationFrontUrl: frontUrl, verificationBackUrl: backUrl, certificationRequestedAt: FieldValue.serverTimestamp(), certificationPaymentStatus: "pending"}); return {ok: true};
});

exports.approveAgencyCertification = onCall(async request => {
  const adminUid = requireAuth(request); const admin = await userData(adminUid); if (admin.admin !== true) throw new HttpsError("permission-denied", "Réservé à l'administrateur.");
  const uid = String(request.data?.agencyId || ""); const approve = request.data?.approve === true; const ref = db.collection("agencies").doc(uid); const snap = await ref.get(); if (!snap.exists) throw new HttpsError("not-found", "Agence introuvable.");
  if (!approve) { await ref.update({certified: false, certificationStatus: "rejected"}); return {ok: true}; }
  const paidUntil = snap.get("certificationPaidUntil"); if (!paidUntil) throw new HttpsError("failed-precondition", "Paiement mensuel non confirmé.");
  await ref.update({certified: true, certificationStatus: "approved", certificationExpiresAt: paidUntil, certificationApprovedAt: FieldValue.serverTimestamp(), certificationApprovedBy: adminUid}); return {ok: true};
});

exports.reportListing = onCall(async (request) => { const uid = requireAuth(request); const id = String(request.data?.listingId || ""); const reason = String(request.data?.reason || "").slice(0, 1000); if (!id || !reason) throw new HttpsError("invalid-argument", "Annonce et raison requis."); const l = await db.collection("listings").doc(id).get(); if (!l.exists || l.get("country") !== (await userData(uid)).country) throw new HttpsError("permission-denied", "Annonce inaccessible."); await db.collection("reports").add({reporterId: uid, listingId: id, reason, country: l.get("country"), createdAt: FieldValue.serverTimestamp(), status: "open"}); return {ok: true}; });

exports.createPaymentLink = onCall({secrets: [CinetPayApiKey, CinetPaySiteId]}, async (request) => {
  const uid = requireAuth(request); const kind = String(request.data?.kind || ""); if (!["agency_extra_slots", "agency_certification"].includes(kind)) throw new HttpsError("invalid-argument", "Type de paiement invalide.");
  const user = await userData(uid); if (kind.startsWith("agency_") && user.agency !== true) throw new HttpsError("failed-precondition", "Une agence est requise.");
  const amount = kind === "agency_extra_slots" ? EXTRA_PRICE : CERT_PRICE; const transactionId = `IML_${kind}_${uid}_${Date.now()}`; const base = PublicBaseUrl.value().replace(/\/$/, ""); if (!base) throw new HttpsError("failed-precondition", "PUBLIC_BASE_URL n'est pas configurée.");
  const notify = `${base}/cinetpayNotify`; const ret = `${base}/cinetpayReturn`;
  await db.collection("payments").doc(transactionId).set({transactionId, userId: uid, kind, amount, currency: "XOF", status: "PENDING", createdAt: FieldValue.serverTimestamp()});
  const payload = {amount, currency: "XOF", apikey: CinetPayApiKey.value(), site_id: CinetPaySiteId.value(), transaction_id: transactionId, description: kind === "agency_extra_slots" ? "ImmoLink 10 annonces supplementaires" : "ImmoLink certification agence mensuelle", notify_url: notify, return_url: ret, channels: "ALL", lang: "FR", metadata: JSON.stringify({uid, kind}), customer_name: user.firstName || "ImmoLink", customer_surname: user.firstName || "Client", customer_phone_number: user.phone || "", customer_country: ({Togo:"TG",Bénin:"BJ",Mali:"ML","Burkina Faso":"BF","Côte d'Ivoire":"CI"})[user.country] || "TG"};
  const response = await fetch("https://api-checkout.cinetpay.com/v2/payment", {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify(payload)}); const json = await response.json(); if (json.code !== "201") throw new HttpsError("internal", json.message || "CinetPay n'a pas créé le paiement."); await db.collection("payments").doc(transactionId).update({paymentToken: json.data.payment_token, paymentUrl: json.data.payment_url}); return {paymentUrl: json.data.payment_url, transactionId};
});

async function processPayment(transactionId) {
  const payRef = db.collection("payments").doc(transactionId); const pay = await payRef.get(); if (!pay.exists || pay.get("status") === "PAID") return;
  const r = await fetch("https://api-checkout.cinetpay.com/v2/payment/check", {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({transaction_id: transactionId, site_id: CinetPaySiteId.value(), apikey: CinetPayApiKey.value()})}); const json = await r.json(); const status = json?.data?.status; const amount = Number(json?.data?.amount || 0);
  if (json.code === "00" && status === "ACCEPTED" && amount === Number(pay.get("amount"))) {
    const uid = pay.get("userId"); const kind = pay.get("kind"); await payRef.update({status: "PAID", paidAt: FieldValue.serverTimestamp(), providerStatus: status});
    if (kind === "agency_extra_slots") await db.collection("agencies").doc(uid).update({extraSlotsPaid: true, extraSlots: EXTRA_QUOTA, extraSlotsPaidAt: FieldValue.serverTimestamp()});
    if (kind === "agency_certification") await db.collection("agencies").doc(uid).update({certificationPaymentStatus: "PAID", certificationPaidAt: FieldValue.serverTimestamp(), certificationPaidUntil: Timestamp.fromMillis(Date.now() + 30 * 86400000)});
  } else if (["REFUSED", "CANCELLED", "REFUNDED"].includes(status)) await payRef.update({status: "FAILED", providerStatus: status, updatedAt: FieldValue.serverTimestamp()});
}

exports.cinetpayNotify = onRequest({secrets: [CinetPayApiKey, CinetPaySiteId]}, async (req, res) => { try { const id = String(req.body?.cpm_trans_id || req.body?.transaction_id || ""); if (id) await processPayment(id); res.status(200).send("OK"); } catch (e) { console.error(e); res.status(200).send("OK"); } });
exports.cinetpayReturn = onRequest(async (_req, res) => res.status(200).send("Paiement reçu. Vous pouvez revenir dans ImmoLink."));

exports.syncCertificationBadges = onSchedule("every 60 minutes", async () => {
  const agencies = await db.collection("agencies").get(); const now = Date.now();
  for (const aDoc of agencies.docs) { const a = aDoc.data(); const active = a.certified === true && a.certificationExpiresAt?.toMillis?.() > now; const ls = await db.collection("listings").where("agencyId", "==", aDoc.id).where("active", "==", true).get(); if (ls.empty) continue; const batch = db.batch(); ls.docs.forEach(l => batch.update(l.ref, {agencyCertified: active})); await batch.commit(); }
  return null;
});

exports.expireListings = onSchedule("every 60 minutes", async () => { const snap = await db.collection("listings").where("active", "==", true).where("expiresAt", "<=", Timestamp.now()).get(); if (snap.empty) return null; const batch = db.batch(); snap.docs.forEach(d => batch.update(d.ref, {active: false, expiredAt: FieldValue.serverTimestamp()})); await batch.commit(); return null; });

async function notifyTokens(tokens, title, body) { if (!tokens.length) return; await getMessaging().sendEachForMulticast({tokens, notification: {title, body}}); }
exports.notifyNewMessage = onDocumentCreated("chats/{chatId}/messages/{messageId}", async event => { const m = event.data?.data(); if (!m) return; const chat = await db.collection("chats").doc(event.params.chatId).get(); const participants = chat.get("participants") || []; const recipients = participants.filter(id => id !== m.senderId); const docs = await Promise.all(recipients.map(id => db.collection("users").doc(id).get())); const tokens = docs.map(d => d.get("fcmToken")).filter(Boolean); await notifyTokens(tokens, "Nouveau message ImmoLink", m.text || "Vous avez reçu un message."); });
exports.notifyNewListing = onDocumentCreated("listings/{listingId}", async event => { const l = event.data?.data(); if (!l) return; const snap = await db.collection("savedSearches").where("country", "==", l.country).where("active", "==", true).get(); const users = new Set(); snap.docs.forEach(d => { const s = d.data(); const modeOk = s.mode === l.mode; const typeOk = !s.propertyType || s.propertyType === l.propertyType; const cityOk = !s.city || s.city === l.city; const minOk = Number(s.minPrice || 0) <= Number(l.price || 0); const maxOk = s.maxPrice == null || Number(s.maxPrice) >= Number(l.price || 0); if (modeOk && typeOk && cityOk && minOk && maxOk) users.add(s.userId); }); if (!users.size) return; const docs = await Promise.all([...users].map(id => db.collection("users").doc(id).get())); const tokens = docs.map(d => d.get("fcmToken")).filter(Boolean); await notifyTokens(tokens, "Nouvelle annonce ImmoLink", `${l.propertyType} à ${l.city} — ${l.price} FCFA`); });

exports.deleteAccount = onCall(async request => { const uid = requireAuth(request); const userRef = db.collection("users").doc(uid); const agencyRef = db.collection("agencies").doc(uid); const listings = await db.collection("listings").where("ownerId", "==", uid).get(); const chats = await db.collection("chats").where("participants", "array-contains", uid).get(); const batch = db.batch(); listings.docs.forEach(d => batch.delete(d.ref)); chats.docs.forEach(d => batch.delete(d.ref)); batch.delete(agencyRef); batch.delete(userRef); await batch.commit(); await getAuth().deleteUser(uid); return {ok: true}; });
