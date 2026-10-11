package com.baharhooni.morkhesi.data

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance() }

    // Storage عمداً lazy است: اگر Storage فعال نشده باشد، اپ هنگام باز شدن کرش نمی‌کند
    // و فقط آپلود گواهی پزشکی خطا می‌دهد.
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    // شماره موبایل شناسه ورود است؛ برای جلوگیری از پیامک، به شناسه داخلی Firebase تبدیل می‌شود.
    private fun phoneLoginId(phone: String): String {
        val digits = phone.trim()
            .replace("۰", "0").replace("۱", "1").replace("۲", "2").replace("۳", "3")
            .replace("۴", "4").replace("۵", "5").replace("۶", "6").replace("۷", "7")
            .replace("۸", "8").replace("۹", "9").filter { it.isDigit() }
        require(digits.length in 10..15) { "شماره موبایل را درست وارد کنید" }
        return "$digits@baharhooni.local"
    }

    suspend fun login(usernameOrPhone: String, password: String) {
        // نام کاربری اختصاصی مدیر به حساب Firebase موجود او نگاشت می‌شود.
        // کارکنان همچنان با شماره موبایل وارد می‌شوند.
        val input = usernameOrPhone.trim()
        val loginId = if (input.equals("soleimani.reza", ignoreCase = true)) {
            "solireza71@gmail.com"
        } else {
            phoneLoginId(input)
        }
        auth.signInWithEmailAndPassword(loginId, password).await()
    }

    suspend fun changePassword(newPassword: String) {
        require(newPassword.length >= 6) { "رمز عبور باید حداقل ۶ کاراکتر باشد" }
        val user = auth.currentUser ?: error("ابتدا وارد حساب شوید")
        user.updatePassword(newPassword).await()
    }

    suspend fun isManager(): Boolean {
        val uid = currentUserId() ?: return false
        val doc = db.collection("appConfig").document("manager").get().await()
        return doc.getString("uid") == uid
    }

    suspend fun createEmployee(phone: String, password: String, name: String, employeeCode: String) {
        val data = hashMapOf("phone" to phone, "password" to password, "name" to name, "employeeCode" to employeeCode)
        functions.getHttpsCallable("createEmployee").call(data).await()
    }

    suspend fun getAllRequestsForManager(): List<LeaveRequest> {
        check(isManager()) { "دسترسی مدیر تأیید نشده است" }
        return db.collection("leaveRequests").get().await().documents
            .mapNotNull { it.toObject(LeaveRequest::class.java)?.copy(id = it.id) }
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
    }

    suspend fun decideRequest(requestId: String, approve: Boolean, note: String = "") {
        check(isManager()) { "دسترسی مدیر تأیید نشده است" }
        db.collection("leaveRequests").document(requestId).update(
            mapOf("status" to if (approve) "APPROVED" else "REJECTED", "managerNote" to note)
        ).await()
    }

    fun signOut() = auth.signOut()
    fun currentUserId(): String? = auth.currentUser?.uid

    suspend fun getUserProfile(): UserProfile? {
        val uid = currentUserId() ?: return null
        val snap = db.collection("users").document(uid).get().await()
        return snap.toObject(UserProfile::class.java)
    }

    suspend fun getBalance(): LeaveBalance {
        val uid = currentUserId() ?: return LeaveBalance()
        val snap = db.collection("leaveBalances").document(uid).get().await()
        return snap.toObject(LeaveBalance::class.java) ?: LeaveBalance()
    }

    // مرتب‌سازی در خود اپ انجام می‌شود تا نیازی به ساخت Index در Firestore نباشد.
    suspend fun getRecentRequests(limit: Int = 5): List<LeaveRequest> {
        val uid = currentUserId() ?: return emptyList()
        return db.collection("leaveRequests")
            .whereEqualTo("userId", uid)
            .get().await()
            .documents
            .mapNotNull { doc -> doc.toObject(LeaveRequest::class.java)?.copy(id = doc.id) }
            .sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
            .take(limit)
    }

    suspend fun uploadMedicalCertificate(uri: Uri): String {
        val uid = currentUserId() ?: error("کاربر وارد نشده است")
        val ref = storage.reference.child("medicalCertificates/$uid/${System.currentTimeMillis()}.pdf")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun createLeaveRequest(request: LeaveRequest, certificateUri: Uri? = null) {
        val uid = currentUserId() ?: error("کاربر وارد نشده است")
        val certificateUrl = certificateUri?.let { uploadMedicalCertificate(it) }
        val data = hashMapOf<String, Any?>(
            "userId" to uid,
            "leaveType" to request.leaveType,
            "startDate" to request.startDate,
            "endDate" to request.endDate,
            "hours" to request.hours,
            "reason" to request.reason,
            "medicalCertificateUrl" to certificateUrl,
            "status" to "PENDING",
            "managerNote" to "",
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("leaveRequests").add(data).await()
    }

    suspend fun ensureUserProfile(email: String) {
        val uid = currentUserId() ?: return
        val ref = db.collection("users").document(uid)
        ref.set(
            mapOf("email" to email, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge()
        ).await()
    }
}

data class UserProfile(
    val name: String = "",
    val employeeCode: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "employee"
)

data class LeaveBalance(
    val entitlementDays: Double = 0.0,
    val usedDays: Double = 0.0,
    val incentiveDays: Double = 0.0,
    val carriedDays: Double = 0.0
) {
    val remainingDays: Double get() = (entitlementDays - usedDays + carriedDays).coerceAtLeast(0.0)
}

data class LeaveRequest(
    val id: String = "",
    val userId: String = "",
    val leaveType: String = "استحقاقی",
    val startDate: String = "",
    val endDate: String = "",
    val hours: Double = 8.0,
    val reason: String = "",
    val medicalCertificateUrl: String? = null,
    val status: String = "PENDING",
    val managerNote: String = "",
    val createdAt: Timestamp? = null
)
