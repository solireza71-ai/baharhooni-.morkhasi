const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
initializeApp();
const db = getFirestore();
function normalizePhone(phone) {
  const digits = String(phone || "").replace(/[۰-۹]/g, d => "۰۱۲۳۴۵۶۷۸۹".indexOf(d)).replace(/[^0-9]/g, "");
  if (digits.length < 10 || digits.length > 15) throw new HttpsError("invalid-argument", "شماره موبایل معتبر نیست");
  return digits;
}
async function requireManager(uid) {
  const snap = await db.doc("appConfig/manager").get();
  if (!snap.exists || snap.get("uid") !== uid) throw new HttpsError("permission-denied", "فقط مدیر مجاز به تعریف کارمند است");
}
exports.createEmployee = onCall(async request => {
  if (!request.auth) throw new HttpsError("unauthenticated", "ابتدا وارد شوید");
  await requireManager(request.auth.uid);
  const { phone, password, name, employeeCode } = request.data || {};
  const digits = normalizePhone(phone);
  if (typeof password !== "string" || password.length < 6) throw new HttpsError("invalid-argument", "رمز عبور باید حداقل ۶ کاراکتر باشد");
  if (!String(name || "").trim() || !String(employeeCode || "").trim()) throw new HttpsError("invalid-argument", "نام و کد پرسنلی الزامی است");
  const email = `${digits}@baharhooni.local`;
  let user;
  try { user = await getAuth().createUser({ email, password, displayName: String(name).trim() }); }
  catch (e) { if (e.code === "auth/email-already-exists") throw new HttpsError("already-exists", "برای این شماره قبلاً حساب ساخته شده است"); throw new HttpsError("internal", "ساخت حساب کارمند ناموفق بود"); }
  const batch = db.batch();
  batch.set(db.doc(`users/${user.uid}`), { name: String(name).trim(), employeeCode: String(employeeCode).trim(), phone: digits, email, role: "employee", updatedAt: FieldValue.serverTimestamp() });
  batch.set(db.doc(`leaveBalances/${user.uid}`), { entitlementDays: 0, usedDays: 0, incentiveDays: 0, carriedDays: 0 });
  await batch.commit();
  return { uid: user.uid, phone: digits };
});
