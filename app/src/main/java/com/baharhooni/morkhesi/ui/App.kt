@file:OptIn(ExperimentalMaterial3Api::class)

package com.baharhooni.morkhesi.ui

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baharhooni.morkhesi.R
import com.baharhooni.morkhesi.data.FirebaseRepository
import com.baharhooni.morkhesi.data.LeaveBalance
import com.baharhooni.morkhesi.data.LeaveRequest
import com.baharhooni.morkhesi.data.UserProfile
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Green = Color(0xFF176B57)
private val LightGreen = Color(0xFFE8F3EF)
private val Orange = Color(0xFFE49A3A)

@Composable
fun App() {
    val repo = remember { FirebaseRepository() }
    var screen by remember { mutableStateOf(if (repo.currentUserId() == null) "splash" else "loadingRole") }

    LaunchedEffect(Unit) {
        if (screen == "splash") {
            delay(1000)
            screen = "login"
        }
    }

    LaunchedEffect(screen) {
        if (screen == "loadingRole") {
            screen = try { if (repo.isManager()) "manager" else "dashboard" } catch (_: Exception) { "dashboard" }
        }
    }
    // دکمه Back گوشی در صفحه درخواست، به داشبورد برمی‌گردد
    BackHandler(enabled = screen == "request") { screen = "dashboard" }

    // کل اپ فارسی است، پس چیدمان راست‌به‌چپ اجباری می‌شود
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (screen) {
                    "splash" -> SplashScreen()
                    "login" -> LoginScreen(repo) { screen = "loadingRole" }
                    "loadingRole" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    "manager" -> ManagerScreen(repo, onLogout = { repo.signOut(); screen = "login" })
                    "request" -> LeaveRequestScreen(
                        repo,
                        onDone = { screen = "dashboard" },
                        onBack = { screen = "dashboard" }
                    )
                    else -> DashboardScreen(
                        repo,
                        onNewRequest = { screen = "request" },
                        onLogout = { repo.signOut(); screen = "login" }
                    )
                }
            }
        }
    }
}

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Green), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🌿", fontSize = 72.sp)
            Spacer(Modifier.height(18.dp))
            Text("مرخصی بهار هونی", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("سامانه مدیریت مرخصی کارکنان", color = Color.White.copy(alpha = .82f), fontSize = 15.sp)
        }
    }
}

@Composable
fun LoginScreen(repo: FirebaseRepository, onLogin: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ltr = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painter = painterResource(id = R.drawable.baharhooni_company_logo), contentDescription = "لوگوی شرکت بهار هونی",
            modifier = Modifier.fillMaxWidth().height(96.dp).padding(bottom = 12.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        Text("مرخصی بهار هونی", color = Green, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("ورود مدیر و کارکنان", color = Color.Gray, modifier = Modifier.padding(top = 8.dp, bottom = 30.dp))
        OutlinedTextField(value = phone, onValueChange = { phone = it; error = "" }, label = { Text("نام کاربری مدیر یا شماره موبایل کارمند") },
            placeholder = { Text("مدیر: soleimani.reza | کارمند: 09123456789") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = ltr,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(value = pass, onValueChange = { pass = it; error = "" }, label = { Text("رمز عبور") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = ltr, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            loading = true; error = ""
            scope.launch {
                try { repo.login(phone, pass); onLogin() }
                catch (e: Exception) { error = friendlyError(e, "ورود ناموفق بود. شماره موبایل و رمز عبور را بررسی کنید.") }
                loading = false
            }
        }, enabled = !loading && phone.isNotBlank() && pass.isNotBlank(), modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Green)) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White) else Text("ورود", fontSize = 17.sp)
        }
        Text("ساخت حساب فقط توسط مدیر انجام می‌شود. پیامک تأیید لازم نیست.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp))
    }
}

@Composable
fun ManagerScreen(repo: FirebaseRepository, onLogout: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var requests by remember { mutableStateOf<List<LeaveRequest>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try { requests = repo.getAllRequestsForManager() }
        catch (e: Exception) { message = friendlyError(e, "دریافت درخواست‌ها ناموفق بود") }
        loading = false
    }
    Scaffold(topBar = { TopAppBar(title = { Text("پنل مدیریت") }, actions = { IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, "خروج") } }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("تعریف کارمند جدید", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it }, label = { Text("نام و نام خانوادگی") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(code, { code = it }, label = { Text("کد پرسنلی") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(phone, { phone = it }, label = { Text("شماره موبایل") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            OutlinedTextField(password, { password = it }, label = { Text("رمز اولیه (حداقل ۶ کاراکتر)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            Button(enabled = name.isNotBlank() && code.isNotBlank() && phone.isNotBlank() && password.length >= 6, onClick = {
                scope.launch { try { repo.createEmployee(phone, password, name, code); message = "حساب کارمند ساخته شد."; phone = ""; password = ""; name = ""; code = "" }
                    catch (e: Exception) { message = friendlyError(e, "ساخت حساب ناموفق بود. تنظیمات سمت سرور را بررسی کنید.") } }
            }, modifier = Modifier.fillMaxWidth()) { Text("ایجاد حساب کارمند") }
            if (message.isNotBlank()) Text(message, color = if (message.contains("ساخته شد")) Green else MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp)); Text("درخواست‌های مرخصی", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (loading) CircularProgressIndicator()
            if (!loading && requests.isEmpty()) Text("درخواستی ثبت نشده است", color = Color.Gray)
            requests.forEach { r ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${r.leaveType} | ${r.startDate} تا ${r.endDate}", fontWeight = FontWeight.Bold)
                        Text("وضعیت: ${statusText(r.status)}")
                        if (r.reason.isNotBlank()) Text("توضیحات: ${r.reason}")
                        if (r.status == "PENDING") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { scope.launch { try { repo.decideRequest(r.id, true); requests = repo.getAllRequestsForManager(); message = "درخواست تأیید شد" } catch (e: Exception) { message = friendlyError(e, "تغییر وضعیت ناموفق بود") } } }) { Text("تأیید") }
                            OutlinedButton(onClick = { scope.launch { try { repo.decideRequest(r.id, false); requests = repo.getAllRequestsForManager(); message = "درخواست رد شد" } catch (e: Exception) { message = friendlyError(e, "تغییر وضعیت ناموفق بود") } } }) { Text("رد") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(repo: FirebaseRepository, onNewRequest: () -> Unit, onLogout: () -> Unit) {
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var balance by remember { mutableStateOf(LeaveBalance()) }
    var requests by remember { mutableStateOf<List<LeaveRequest>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var passwordMessage by remember { mutableStateOf("") }
    val passwordScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            profile = repo.getUserProfile()
            balance = repo.getBalance()
            requests = repo.getRecentRequests()
        } catch (e: Exception) {
            loadError = friendlyError(e, "دریافت اطلاعات ناموفق بود")
        }
        loading = false
    }

    val displayName = profile?.name?.takeIf { it.isNotBlank() } ?: "خوش آمدید"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("داشبورد من", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, "خروج") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)
        ) {
            Text("سلام 👋", fontSize = 16.sp, color = Color.Gray)
            Text(displayName, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Green),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text("مانده مرخصی استحقاقی", color = Color.White.copy(alpha = .85f))
                    Text(
                        if (loading) "…" else "%.1f روز".format(balance.remainingDays),
                        color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold
                    )
                    Text("از سهمیه استحقاقی", color = Color.White.copy(alpha = .8f))
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard("مصرف‌شده", "%.1f روز".format(balance.usedDays), Modifier.weight(1f))
                InfoCard("تشویقی", "%.1f روز".format(balance.incentiveDays), Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onNewRequest,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("درخواست مرخصی جدید", fontSize = 16.sp)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { showPasswordDialog = true; newPassword = ""; passwordMessage = "" }, modifier = Modifier.fillMaxWidth()) {
                Text("تغییر رمز عبور")
            }
            Spacer(Modifier.height(22.dp))
            Text("آخرین درخواست‌ها", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Spacer(Modifier.height(10.dp))
            if (loadError.isNotBlank()) {
                Text(loadError, color = MaterialTheme.colorScheme.error)
            } else if (!loading && requests.isEmpty()) {
                Text("هنوز درخواستی ثبت نشده است", color = Color.Gray)
            }
            requests.forEach { r ->
                val dates = if (r.endDate.isNotBlank()) "${r.startDate} تا ${r.endDate}" else r.startDate
                RequestRow(r.leaveType, dates, statusText(r.status), statusColor(r.status))
            }
        }
    }
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("تغییر رمز عبور") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newPassword, onValueChange = { newPassword = it; passwordMessage = "" },
                        label = { Text("رمز جدید (حداقل ۶ کاراکتر)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                    if (passwordMessage.isNotBlank()) Text(passwordMessage, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = {
                passwordScope.launch {
                    try { repo.changePassword(newPassword); passwordMessage = "رمز عبور تغییر کرد"; showPasswordDialog = false }
                    catch (e: Exception) { passwordMessage = friendlyError(e, "تغییر رمز ناموفق بود؛ ممکن است لازم باشد خارج شوید و دوباره وارد شوید.") }
                }
            }, enabled = newPassword.length >= 6) { Text("ذخیره") } },
            dismissButton = { TextButton(onClick = { showPasswordDialog = false }) { Text("انصراف") } }
        )
    }
}

@Composable
fun LeaveRequestScreen(repo: FirebaseRepository, onDone: () -> Unit, onBack: () -> Unit) {
    var type by remember { mutableStateOf("استحقاقی") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("8") }
    var reason by remember { mutableStateOf("") }
    var certificate by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { certificate = it }
    val ltr = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("درخواست مرخصی") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("نوع مرخصی")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("استحقاقی", "استعلاجی", "بدون‌حقوق").forEach { item ->
                    FilterChip(selected = type == item, onClick = { type = item }, label = { Text(item) })
                }
            }
            OutlinedTextField(
                value = start, onValueChange = { start = it },
                label = { Text("تاریخ شروع") }, placeholder = { Text("مثال: 1405/06/28") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = ltr
            )
            OutlinedTextField(
                value = end, onValueChange = { end = it },
                label = { Text("تاریخ پایان") }, placeholder = { Text("مثال: 1405/06/29") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = ltr
            )
            OutlinedTextField(
                value = hours, onValueChange = { hours = it },
                label = { Text("تعداد ساعت") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = ltr,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = reason, onValueChange = { reason = it },
                label = { Text("توضیحات") },
                modifier = Modifier.fillMaxWidth(), minLines = 3
            )
            if (type == "استعلاجی") {
                OutlinedButton(onClick = { picker.launch("application/pdf") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AttachFile, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (certificate == null) "انتخاب گواهی پزشکی (PDF)" else "گواهی انتخاب شد")
                }
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = !saving,
                onClick = {
                    val hoursValue = normalizeDigits(hours).toDoubleOrNull()
                    val normalizedStart = normalizeDigits(start.trim())
                    val normalizedEnd = normalizeDigits(end.trim())
                    if (start.isBlank() || end.isBlank()) {
                        message = "لطفاً تاریخ شروع و پایان را وارد کنید"
                    } else if (!Regex("\\d{4}/\\d{2}/\\d{2}").matches(normalizedStart) ||
                        !Regex("\\d{4}/\\d{2}/\\d{2}").matches(normalizedEnd)) {
                        message = "تاریخ را به شکل 1405/06/28 وارد کنید"
                    } else if (normalizedStart > normalizedEnd) {
                        message = "تاریخ شروع نمی‌تواند بعد از تاریخ پایان باشد"
                    } else if (type == "استعلاجی" && certificate == null) {
                        message = "برای مرخصی استعلاجی، فایل گواهی پزشکی PDF را انتخاب کنید"
                    } else if (hoursValue == null || hoursValue <= 0.0 || hoursValue > 24.0) {
                        message = "تعداد ساعت باید بیشتر از صفر و حداکثر ۲۴ باشد"
                    } else {
                        saving = true
                        message = ""
                        scope.launch {
                            try {
                                repo.createLeaveRequest(
                                    LeaveRequest(
                                        leaveType = type,
                                        startDate = normalizedStart,
                                        endDate = normalizedEnd,
                                        hours = hoursValue,
                                        reason = reason.trim()
                                    ),
                                    if (type == "استعلاجی") certificate else null
                                )
                                onDone()
                            } catch (e: Exception) {
                                message = friendlyError(e, "ثبت درخواست ناموفق بود")
                            }
                            saving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green)
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White)
                else Text("ثبت درخواست")
            }
        }
    }
}

private fun statusText(status: String) = when (status) {
    "APPROVED" -> "تأیید شده"
    "REJECTED" -> "رد شده"
    else -> "در انتظار"
}

// این تابع از MaterialTheme استفاده می‌کند، پس باید @Composable باشد (نسخه قبلی خطای Build می‌داد)
@Composable
private fun statusColor(status: String): Color = when (status) {
    "APPROVED" -> Green
    "REJECTED" -> MaterialTheme.colorScheme.error
    else -> Orange
}

// تبدیل ارقام فارسی/عربی به انگلیسی تا تعداد ساعت و تاریخ درست ذخیره شود
private fun normalizeDigits(s: String): String = buildString {
    for (c in s) {
        append(
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                '٫', '،', ',' -> '.'
                else -> c
            }
        )
    }
}

private fun friendlyError(e: Throwable, fallback: String): String = when (e) {
    is FirebaseAuthUserCollisionException -> "این ایمیل قبلاً ثبت‌نام شده است؛ از گزینه ورود استفاده کنید"
    is FirebaseAuthWeakPasswordException -> "رمز عبور باید حداقل ۶ کاراکتر باشد"
    is FirebaseAuthInvalidUserException,
    is FirebaseAuthInvalidCredentialsException -> "ایمیل یا رمز عبور اشتباه است"
    is FirebaseNetworkException -> "اتصال اینترنت برقرار نیست"
    is FirebaseFirestoreException -> when (e.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "دسترسی مجاز نیست؛ قوانین امنیتی (Rules) Firestore را بررسی کنید"
        FirebaseFirestoreException.Code.UNAVAILABLE -> "سرور در دسترس نیست؛ اینترنت را بررسی کنید"
        else -> fallback
    }
    is StorageException -> "آپلود گواهی ناموفق بود؛ فعال بودن Storage و پلن Blaze را بررسی کنید"
    else -> e.message ?: fallback
}

@Composable
fun InfoCard(title: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.Gray)
            Text(value, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Green)
        }
    }
}

@Composable
fun RequestRow(title: String, date: String, status: String, statusColor: Color) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.padding(15.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Event, null, tint = Green)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(date, color = Color.Gray, fontSize = 13.sp)
            }
            Text(status, color = statusColor, fontWeight = FontWeight.Bold)
        }
    }
}
