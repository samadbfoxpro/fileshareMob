package com.example

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.asImageBitmap
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale

class MainActivity : ComponentActivity() {

    private lateinit var repository: FileShareRepository
    private val selectTabState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FileShareRepository(applicationContext)

        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    var showSplash by remember { mutableStateOf(true) }
                    
                    if (showSplash) {
                        SplashScreen(onSplashFinished = { showSplash = false })
                    } else {
                        MainScreenLayout(
                            repository = repository,
                            selectTab = selectTabState.value,
                            onTabResolved = { selectTabState.value = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val tab = intent?.getStringExtra("select_tab")
        if (tab != null) {
            selectTabState.value = tab
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreenLayout(
    repository: FileShareRepository,
    selectTab: String? = null,
    onTabResolved: () -> Unit = {}
) {
    var activeTab by remember { mutableStateOf("dashboard") }
    var showGuideScreen by remember { mutableStateOf(false) }
    
    val pendingWebSessions by WebSessionApprovalManager.pendingRequests.collectAsState()

    LaunchedEffect(selectTab) {
        if (selectTab != null) {
            if (selectTab == "dashboard" || selectTab == "chat" || selectTab == "logs") {
                activeTab = selectTab
            }
            onTabResolved()
        }
    }

    val isKeyboardVisible = WindowInsets.isImeVisible

    val context = LocalContext.current

    BackHandler(enabled = true) {
        if (showGuideScreen) {
            showGuideScreen = false
        } else if (activeTab != "dashboard") {
            activeTab = "dashboard"
        } else {
            var currentContext = context
            var activity: Activity? = null
            while (currentContext is ContextWrapper) {
                if (currentContext is Activity) {
                    activity = currentContext
                    break
                }
                currentContext = currentContext.baseContext
            }
            activity?.moveTaskToBack(true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFF121B22),
            topBar = {
                AppHeader(onGuideClick = { showGuideScreen = true })
            },
            bottomBar = {
                if (!isKeyboardVisible || activeTab != "chat") {
                    AppBottomNavigation(
                        activeTab = activeTab,
                        onTabSelected = { activeTab = it }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF121B22))
            ) {
                when (activeTab) {
                    "dashboard" -> FileShareDashboard(
                        repository = repository,
                        modifier = Modifier.fillMaxSize()
                    )
                    "chat" -> FileShareMessages(
                        repository = repository,
                        modifier = Modifier.fillMaxSize()
                    )
                    "logs" -> FileShareLogs(
                        repository = repository,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Web Client approval request dialog
        if (pendingWebSessions.isNotEmpty()) {
            val currentRequest = pendingWebSessions.first()
            AlertDialog(
                onDismissRequest = { /* Force user to choose Approve or Deny */ },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "درخواست دسترسی مرورگر",
                            tint = Color(0xFF00A884)
                        )
                        Text(
                            text = "درخواست تایید مرورگر وب",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "یک دستگاه در شبکه محلی می‌خواهد به فایل‌ها و پیام‌های شما دسترسی داشته باشد.",
                            color = Color(0xFFECE5DD),
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFF2A3942))
                                .padding(vertical = 4.dp)
                        )
                        Row {
                            Text("نام کاربری وب: ", color = Color(0xFF8696A0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(currentRequest.nickname, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Row {
                            Text("آدرس آی‌پی: ", color = Color(0xFF8696A0), fontSize = 12.sp)
                            Text(currentRequest.ip, color = Color.White, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            WebSessionApprovalManager.approveSession(currentRequest.sessionId)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884))
                    ) {
                        Text("تایید و اجازه دسترسی", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            WebSessionApprovalManager.denySession(currentRequest.sessionId)
                        }
                    ) {
                        Text("رد درخواست", color = Color(0xFFFFB4AB), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF1F2C34),
                textContentColor = Color.White
            )
        }

        // Animated Overlay for Guide Screen
        AnimatedVisibility(
            visible = showGuideScreen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FileShareGuideScreen(
                onBack = { showGuideScreen = false },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHeader(onGuideClick: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        text = "FileShare",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "انتقال فایل در شبکه محلی وای‌فای",
                        fontSize = 10.sp,
                        color = Color(0xFF8696A0),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                // Guide/Info Button
                IconButton(
                    onClick = onGuideClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF121B22), shape = RoundedCornerShape(10.dp))
                        .border(BorderStroke(1.dp, Color(0xFF2A3942)), RoundedCornerShape(10.dp))
                        .testTag("guide_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "راهنما و قوانین",
                        tint = Color(0xFF00A884),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .background(Color(0xFF121B22), shape = RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, Color(0xFF2A3942)), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "v1.0.0",
                        fontSize = 11.sp,
                        color = Color(0xFF00A884),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF1F2C34)
        ),
        modifier = Modifier.fillMaxWidth().testTag("app_bar")
    )
}

@Composable
fun AppBottomNavigation(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color(0xFF202C33))
            .border(BorderStroke(1.dp, Color(0xFF2A3942)))
            .navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        BottomTabItem(
            isActive = activeTab == "dashboard",
            icon = Icons.Default.Home,
            onClick = { onTabSelected("dashboard") }
        )

        BottomTabItem(
            isActive = activeTab == "chat",
            icon = Icons.Default.Send,
            onClick = { onTabSelected("chat") }
        )

        BottomTabItem(
            isActive = activeTab == "logs",
            icon = Icons.Default.Share, 
            onClick = { onTabSelected("logs") }
        )
    }
}

@Composable
fun BottomTabItem(
    isActive: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isActive) Color(0xFF00A884).copy(alpha = 0.15f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) Color(0xFF00A884) else Color(0xFF8696A0),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun FileShareDashboard(
    repository: FileShareRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRunning by FileShareService.isRunning.collectAsState()
    val serverAddresses by FileShareService.serverAddresses.collectAsState()
    val serverError by FileShareService.serverError.collectAsState()

    var sharedFolderUri by remember { mutableStateOf(repository.getSharedFolderUri()) }
    var sharedFolderName by remember { mutableStateOf(repository.getSharedFolderName() ?: "") }

    // Launcher for selecting shared folder (SAF document tree)
    val sharedFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (se: SecurityException) {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                repository.setSharedFolderUri(uri.toString())
                sharedFolderUri = uri.toString()
                sharedFolderName = repository.getSharedFolderName() ?: "سرور محلی"
                Toast.makeText(context, "پوشه اشتراکی با موفقیت تنظیم شد", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "خطا در دسترسی به پوشه!", Toast.LENGTH_LONG).show()
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            FileShareService.startService(context)
        } else {
            Toast.makeText(context, "برای کارکرد سرور در پس‌زمینه، دسترسی به نوتیفیکیشن لازم است.", Toast.LENGTH_LONG).show()
            FileShareService.startService(context)
        }
    }

    val onToggleServer = {
        if (isRunning) {
            FileShareService.stopService(context)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    FileShareService.startService(context)
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                FileShareService.startService(context)
            }
        }
    }

    // Status mapping based on designer instruction
    val statusColor by animateColorAsState(
        targetValue = if (isRunning) Color(0xFFB2F2BB) else Color(0xFFF87171),
        animationSpec = tween(durationMillis = 300),
        label = "status_color"
    )

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Intro Card matching minimal aesthetics
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
            border = BorderStroke(1.dp, Color(0xFF2A3942))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "میزبانی انتقال فایل",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "بدون اینترنت، گوشی را تبدیل به سرور اشتراک‌گذاری محلی کنید.",
                        fontSize = 11.sp,
                        color = Color(0xFF8696A0),
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Server Status Card (Theme Specific)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
            border = BorderStroke(1.dp, Color(0xFF2A3942))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "وضعیت سرور محلی",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00A884),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .background(statusColor, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRunning) "در حال اجرا" else "متوقف شده",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Android M3 Switch design container clickable
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(32.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) Color(0xFF00A884) else Color(0xFF2A3942))
                            .clickable { onToggleServer() }
                            .padding(4.dp),
                        contentAlignment = if (isRunning) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(if (isRunning) Color.White else Color(0xFF8696A0), shape = CircleShape)
                        )
                    }
                }

                if (isRunning) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "آدرس‌های اتصال در مرورگر سایر دستگاه‌ها:",
                        fontSize = 11.sp,
                        color = Color(0xFF8696A0),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // Address entries with clean monospace box
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AddressBox(url = "http://localhost:8886", label = "خود گوشی (لوکال)")
                        for (ip in serverAddresses) {
                            AddressBox(url = "http://$ip:8886", label = "سیستم‌های متصل به Wi-Fi")
                        }
                        if (serverAddresses.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF202C33), shape = RoundedCornerShape(12.dp))
                                    .border(BorderStroke(1.dp, Color(0xFF2A3942)), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚠️", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "وای‌فای غیرفعال است! هات‌اسپات یا مودم را روشن کنید.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFF87171)
                                )
                            }
                        }
                    }
                }

                // Port binding failures
                AnimatedVisibility(
                    visible = serverError != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    serverError?.let {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF3B1E1E), shape = RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, Color(0xFFF87171)), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = it,
                                color = Color(0xFFF87171),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Configuration grid (2 columns)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left Box: Port Info
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
                border = BorderStroke(1.dp, Color(0xFF2A3942))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "پورت فعال",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8696A0)
                    )
                    Text(
                        text = "۸۸۸۶",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White
                    )
                }
            }

            // Right Box: Shared Folder Action Link
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
                    .clickable { sharedFolderLauncher.launch(null) },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
                border = BorderStroke(1.dp, Color(0xFF2A3942))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "پوشه اشتراکی",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8696A0)
                        )
                        if (sharedFolderUri != null) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FF5252))
                                    .clickable {
                                        repository.setSharedFolderUri(null)
                                        sharedFolderUri = null
                                        sharedFolderName = ""
                                        Toast.makeText(context, "اشتراک‌گذاری پوشه لغو شد", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✕",
                                    color = Color(0xFFFF5252),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "📂",
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (sharedFolderUri != null) sharedFolderName else "انتخاب پوشه...",
                            color = Color(0xFF00A884),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Quick Actions panel styled with divided borders
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
            border = BorderStroke(1.dp, Color(0xFF2A3942))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Action 1: Open panel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://localhost:8886"))
                            context.startActivity(openIntent)
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF005C4B), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🌐", fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "باز کردن پنل وب روی گوشی",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text("◀", fontSize = 10.sp, color = Color(0xFF8696A0))
                }

                // Divider Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF2A3942))
                )

                // Action 2: Show Upload folder path info (Non-clickable representation)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF2A3942), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📂", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "مسیر پیش‌فرض ذخیره فایل‌های آپلود شده",
                            fontSize = 13.sp,
                            fontWeight = Modifier.let { FontWeight.Medium },
                            color = Color.White
                        )
                        Text(
                            text = repository.getUploadsDirectoryPath(),
                            fontSize = 9.sp,
                            color = Color(0xFF8696A0),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // About card helper
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
            border = BorderStroke(1.dp, Color(0xFF2A3942))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF00A884),
                    modifier = Modifier
                        .size(18.dp)
                        .padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "برنامه FileShare کاملاً بومی و مستقل در شبکه محلی کار می‌کند. با روشن کردن سرور، این گوشی تبدیل به وب سرور انتقال سریع و گفتگوی بی واسطه در شبکه خانگی یا کاری شما می‌شود.",
                    fontSize = 11.sp,
                    color = Color(0xFF8696A0),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun QRCodeDisplayDialog(
    url: String,
    onDismiss: () -> Unit
) {
    val bitmap = remember(url) {
        QRCodeHelper.generateQRCode(url, 400)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "اسکن آدرس اتصال",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "با اسکن این کد در گوشی دوم، به راحتی به این سرور متصل شوید.",
                    color = Color(0xFF938F99),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "کد QR آدرس",
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .padding(8.dp)
                    )
                } else {
                    Text("خطا در ساخت QR Code", color = Color.Red, fontSize = 12.sp)
                }
                Text(
                    text = url,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD0BCFF),
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("تایید", color = Color(0xFFD0BCFF))
            }
        },
        containerColor = Color(0xFF2B2930)
    )
}

@Composable
fun AddressBox(
    url: String,
    label: String
) {
    val context = LocalContext.current
    var showQR by remember { mutableStateOf(false) }

    if (showQR) {
        QRCodeDisplayDialog(url = url, onDismiss = { showQR = false })
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1C1B1F))
            .border(BorderStroke(1.dp, Color(0xFF49454F)), RoundedCornerShape(14.dp))
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("FileShare Link", url)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "$label کپی شد!", Toast.LENGTH_SHORT).show()
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color(0xFFD0BCFF),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = url,
                fontSize = 12.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "کپی آدرَس",
                fontSize = 9.sp,
                color = Color(0xFFD0BCFF),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("FileShare Link", url)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "$label کپی شد!", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            IconButton(
                onClick = { showQR = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "QR Code",
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}


// ---------------------- TAB 2: MESSAGES (LOCAL CHAT) ----------------------
fun formatToLocalTime(isoString: String): String {
    return try {
        val dfUtc = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val date = dfUtc.parse(isoString) ?: return ""
        val dfLocal = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getDefault()
        }
        dfLocal.format(date)
    } catch (e: Exception) {
        if (isoString.length >= 16) {
            isoString.substring(11, 16)
        } else {
            ""
        }
    }
}

@Composable
fun FileShareMessages(
    repository: FileShareRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var messagesList by remember { mutableStateOf(emptyList<Message>()) }
    var inputText by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableStateOf(0) }
    var confirmClearDialog by remember { mutableStateOf(false) }
    var replyToMessage by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }

    val listState = rememberLazyListState()

    var isChatUploading by remember { mutableStateOf(false) }
    var chatUploadProgress by remember { mutableStateOf<Int?>(null) }
    var chatUploadFileName by remember { mutableStateOf("") }
    var chatUploadCanceled by remember { mutableStateOf(false) }

    // Chat Thread Navigation
    var selectedPeerId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Real-time online ping status map
    var onlineStatuses by remember { mutableStateOf(mapOf<String, Boolean>()) }
    val trustedPeers = remember(refreshTrigger) { repository.getTrustedPeers() }

    LaunchedEffect(trustedPeers) {
        while (true) {
            val updated = mutableMapOf<String, Boolean>()
            for (peer in trustedPeers) {
                val pingOk = ClientNetworkManager.pingHost(peer.ip)
                updated[peer.ip] = pingOk
            }
            onlineStatuses = updated
            delay(4000)
        }
    }

    val chatFilePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isChatUploading = true
                chatUploadProgress = 0
                val tempFile = copyUriToTempFile(context, uri)
                if (tempFile != null && tempFile.exists()) {
                    chatUploadFileName = tempFile.name
                    val bytes = tempFile.length()
                    var success = false
                    val savedFile = File(repository.getUploadsDirectoryPath(), tempFile.name)
                    
                    try {
                        withContext(Dispatchers.IO) {
                            tempFile.inputStream().use { input ->
                                savedFile.outputStream().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    var totalBytes = 0L
                                    var length: Int
                                    while (input.read(buffer).also { length = it } != -1) {
                                        if (chatUploadCanceled) {
                                            throw java.io.IOException("Canceled")
                                        }
                                        output.write(buffer, 0, length)
                                        totalBytes += length
                                        if (bytes > 0) {
                                            chatUploadProgress = ((totalBytes * 100) / bytes).toInt()
                                        }
                                    }
                                }
                            }
                            success = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (savedFile.exists()) savedFile.delete()
                    }
                    
                    if (success) {
                        Toast.makeText(context, "فایل با موفقیت در چت قرار گرفت.", Toast.LENGTH_SHORT).show()
                        val sizeStr = run {
                            val mb = savedFile.length().toDouble() / (1024.0 * 1024.0)
                            val kb = savedFile.length().toDouble() / 1024.0
                            if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%.1f KB", kb)
                        }
                        repository.addMessage(
                            from = "مدیر شبکه (گوشی)",
                            text = "📎 فایل: ${savedFile.name} ($sizeStr)",
                            senderId = "host_admin",
                            chatId = selectedPeerId
                        )
                        refreshTrigger++
                    } else {
                        if (chatUploadCanceled) {
                            Toast.makeText(context, "ارسال فایل لغو شد.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "ذخیره فایل ناموفق بود.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    tempFile.delete()
                } else {
                    Toast.makeText(context, "خطا در بارگذاری فایل.", Toast.LENGTH_SHORT).show()
                }
                isChatUploading = false
                chatUploadProgress = null
                chatUploadCanceled = false
            }
        }
    }

    // Load messages when trigger changes (providing organic syncing)
    LaunchedEffect(refreshTrigger) {
        repository.markAllMessagesAsRead()
        messagesList = repository.getAllMessages()
    }

    // Auto-refresh messages loop
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            repository.markAllMessagesAsRead()
            messagesList = repository.getAllMessages()
        }
    }

    // Auto scroll to the newest message at bottom inside active thread
    LaunchedEffect(messagesList.size, selectedPeerId) {
        if (messagesList.isNotEmpty()) {
            listState.animateScrollToItem(messagesList.size - 1)
        }
    }

    Column(
        modifier = modifier
            .background(Color(0xFF121B22))
            .padding(12.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Header Card (Sleek design with title and Delete/Clear All)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1F2C34), shape = RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "گفتگوی محلی 💬",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "گفتگوی مستقیم با تمامی کاربران متصل به برنامه",
                    fontSize = 10.sp,
                    color = Color(0xFF8696A0)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { refreshTrigger++; Toast.makeText(context, "بروزرسانی انجام شد", Toast.LENGTH_SHORT).show() }
                ) {
                    Icon(Icons.Default.Refresh, "بروزرسانی", tint = Color(0xFF00A884))
                }
                if (messagesList.isNotEmpty()) {
                    IconButton(onClick = { confirmClearDialog = true }) {
                        Icon(Icons.Default.Delete, "حذف همه", tint = Color(0xFFF87171))
                    }
                }
            }
        }

        // 2. Main Messages Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0B141A))
                .border(BorderStroke(1.dp, Color(0xFF222E35)), RoundedCornerShape(20.dp))
                .padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            if (messagesList.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("💬", fontSize = 42.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "هیچ پیامی ثبت نشده است",
                        fontSize = 13.sp,
                        color = Color(0xFF8696A0),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "کلاینت‌ها می‌توانند پس از اتصال، برای شما پیام ارسال کنند.",
                        fontSize = 11.sp,
                        color = Color(0xFF8696A0).copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messagesList) { msg ->
                        MessageBubble(
                            msg = msg,
                            repository = repository,
                            onDelete = {
                                repository.deleteMessage(msg.id)
                                refreshTrigger++
                            },
                            onReply = {
                                replyToMessage = msg
                                editingMessage = null
                                inputText = ""
                            },
                            onEdit = {
                                editingMessage = msg
                                replyToMessage = null
                                inputText = msg.text
                            }
                        )
                    }
                }
            }
        }

        // 3. Floating Upload Display (if there's any upload progress)
        if (isChatUploading) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
                border = BorderStroke(1.dp, Color(0xFF2A3942))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("⏳", fontSize = 18.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "در حال ذخیره‌سازی: $chatUploadFileName",
                            fontSize = 12.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (chatUploadProgress != null) {
                            LinearProgressIndicator(
                                progress = { chatUploadProgress!!.toFloat() / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF00A884),
                                trackColor = Color(0xFF2A3942)
                            )
                        }
                    }
                    IconButton(onClick = { chatUploadCanceled = true }) {
                        Text("❌", fontSize = 14.sp)
                    }
                }
            }
        }

        // 4. Reply / Edit Previews above Input Area
        if (replyToMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
                border = BorderStroke(1.dp, Color(0xFF2A3942))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "↩ پاسخ به " + replyToMessage!!.from,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00A884)
                        )
                        Text(
                            text = replyToMessage!!.text,
                            fontSize = 11.sp,
                            color = Color(0xFF8696A0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { replyToMessage = null }) {
                        Text("❌", fontSize = 12.sp)
                    }
                }
            }
        }

        if (editingMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
                border = BorderStroke(1.dp, Color(0xFF2A3942))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "✏️ ویرایش پیام",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00A884)
                        )
                        Text(
                            text = editingMessage!!.text,
                            fontSize = 11.sp,
                            color = Color(0xFF8696A0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { 
                        editingMessage = null 
                        inputText = ""
                    }) {
                        Text("❌", fontSize = 12.sp)
                    }
                }
            }
        }

        // 5. Input Pill Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
                border = BorderStroke(1.dp, Color(0xFF2A3942))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📎",
                        fontSize = 20.sp,
                        modifier = Modifier.padding(end = 8.dp).clickable {
                            chatFilePickerLauncher.launch("*/*")
                        }
                    )

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("تایپ پیام...", fontSize = 13.sp, color = Color(0xFF8696A0)) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = Color(0xFF00A884)
                        ),
                        singleLine = false,
                        maxLines = 4
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF00A884), shape = CircleShape)
                    .clickable {
                        if (inputText.trim().isNotEmpty()) {
                            if (editingMessage != null) {
                                repository.editMessage(editingMessage!!.id, editingMessage!!.from, inputText.trim())
                                editingMessage = null
                            } else {
                                repository.addMessage(
                                    from = "مدیر شبکه (گوشی)",
                                    text = inputText.trim(),
                                    senderId = "host_admin",
                                    replyToId = replyToMessage?.id,
                                    replyToText = replyToMessage?.text,
                                    replyToUser = replyToMessage?.from
                                )
                                replyToMessage = null
                            }
                            inputText = ""
                            refreshTrigger++
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (editingMessage != null) Icons.Default.Done else Icons.Default.Send,
                    contentDescription = if (editingMessage != null) "بروزرسانی" else "ارسال",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    // Confirmation Clear Dialog
    if (confirmClearDialog) {
        AlertDialog(
            onDismissRequest = { confirmClearDialog = false },
            title = { Text("پاک‌سازی تاریخچه گفتگوها", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("آیا مطمئن هستید که می‌خواهید تمامی پیام‌ها را به طور کلی حذف نمایید؟", fontSize = 13.sp, color = Color(0xFFCAC4D0)) },
            containerColor = Color(0xFF1F2C34),
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.deleteAllMessages()
                        refreshTrigger++
                        confirmClearDialog = false
                        Toast.makeText(context, "تمامی گفتگوها با موفقیت پاک‌سازی شدند.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("بله، پاک شود", color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDialog = false }) {
                    Text("انصراف", color = Color(0xFF00A884))
                }
            }
        )
    }
}

@Composable
fun MessageBubble(
    msg: Message,
    repository: FileShareRepository,
    onDelete: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current
    
    val isHost = msg.from.contains("مدیر") || msg.from.contains("گوشی")
    
    // Bubble colors & alignment matching WhatsApp
    val bubbleColor = if (isHost) Color(0xFF005C4B) else Color(0xFF202C33)
    val align = if (isHost) Alignment.End else Alignment.Start
    val bubbleShape = if (isHost) {
        RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 2.dp, bottomEnd = 14.dp)
    }

    // Direct color styling for incoming sender names
    val senderNameColor = remember(msg.from) {
        val hash = msg.from.hashCode()
        val colors = listOf(
            Color(0xFF34B7F1), // Light Blue
            Color(0xFF53BDEB), // Teal-ish Blue
            Color(0xFFE57C23), // Warm Orange
            Color(0xFFE8A0BF), // Rose Pink
            Color(0xFF900C3F), // Purple Red
            Color(0xFF435B66), // Muted Cyan
            Color(0xFFD3E7DE), // Pale Green
            Color(0xFFFFB000)  // Golden Yellow
        )
        colors[Math.abs(hash) % colors.size]
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = align
    ) {
        // Main Message Card
        Card(
            shape = bubbleShape,
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            border = BorderStroke(1.dp, if (isHost) Color(0xFF017A64) else Color(0xFF2B3A42)),
            modifier = Modifier
                .widthIn(max = 290.dp)
                .pointerInput(msg.text) {
                    detectTapGestures(
                        onLongPress = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(msg.text))
                            Toast.makeText(context, "پیام کپی شد", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                // Header row: Nickname & small quick actions (Copy, Delete)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isHost) {
                        Text(
                            text = "📱 " + msg.from,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = senderNameColor,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            text = "شما (مدیر)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF00A884),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Small quick copy & delete actions
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Reply pill
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp))
                                .clickable { onReply() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("↩", fontSize = 10.sp, color = Color.White)
                                Text("پاسخ", fontSize = 10.sp, color = Color.White.copy(alpha = 0.9f))
                            }
                        }

                        // Edit pill (only for host messages)
                        if (isHost) {
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp))
                                    .clickable { onEdit() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text("✏️", fontSize = 10.sp)
                                    Text("ویرایش", fontSize = 10.sp, color = Color.White.copy(alpha = 0.9f))
                                }
                            }
                        }

                        // Sleek emoji-pill based copy button
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp))
                                .clickable {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(msg.getDecryptedText()))
                                    Toast.makeText(context, "پیام کپی شد", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("📋", fontSize = 10.sp)
                                Text("کپی", fontSize = 10.sp, color = Color.White.copy(alpha = 0.9f))
                            }
                        }

                        IconButton(
                            onClick = {
                                onDelete()
                                Toast.makeText(context, "پیام حذف شد.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "حذف",
                                tint = Color(0xFFF87171).copy(alpha = 0.8f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Reply Reference Header inside message bubble
                if (msg.replyToId != null) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(
                                text = "↪ پاسخ به " + (msg.replyToUser ?: "ناشناس"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00A884)
                            )
                            Text(
                                text = msg.replyToText ?: "",
                                fontSize = 11.sp,
                                color = Color(0xFF8696A0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Message Text or File Attachment
                val decryptedText = msg.getDecryptedText()
                val hasFile = decryptedText.startsWith("📎 فایل:")
                if (hasFile) {
                    val fileName = run {
                        val prefix = "📎 فایل:"
                        val cleanText = decryptedText.substringAfter(prefix).trim()
                        if (cleanText.contains(" (") && cleanText.endsWith(")")) {
                            cleanText.substringBeforeLast(" (").trim()
                        } else {
                            cleanText
                        }
                    }
                    val sizeString = if (decryptedText.contains("(")) {
                        decryptedText.substringAfter("(").substringBefore(")")
                    } else {
                        "فایل چت"
                    }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isHost) Color(0xFF025142) else Color(0xFF182229)),
                        border = BorderStroke(1.dp, if (isHost) Color(0xFF014135) else Color(0xFF27343E))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(Color(0xFF00A884).copy(alpha = 0.2f), shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("📁", fontSize = 20.sp)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fileName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = sizeString,
                                        fontSize = 11.sp,
                                        color = Color(0xFF8696A0)
                                    )
                                }
                                
                                IconButton(
                                    onClick = {
                                        openFile(context, fileName, false, repository)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("📂", fontSize = 16.sp)
                                }
                            }

                            // Dynamic WhatsApp inline media previews
                            val fileType = getFileType(fileName)
                            val localFile = java.io.File(repository.getCustomUploadsDir(), fileName)
                            if (localFile.exists()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                when (fileType) {
                                    "image" -> ImagePreviewWidget(localFile.absolutePath, Modifier.fillMaxWidth())
                                    "audio" -> AudioPlayerWidget(localFile.absolutePath, Modifier.fillMaxWidth())
                                    "video" -> VideoPreviewWidget(localFile.absolutePath, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = decryptedText,
                        fontSize = 14.sp,
                        color = Color(0xFFE9EDEF),
                        lineHeight = 19.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Footer Row: Local Time & Ticks (RTL alignment)
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (msg.edited != null) {
                        Text(
                            text = "(ویرایش شده) ",
                            fontSize = 8.sp,
                            color = Color(0xFF8696A0).copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = formatToLocalTime(msg.created),
                        fontSize = 9.sp,
                        color = Color(0xFF8696A0)
                    )
                    
                    if (isHost) {
                        val (tickText, tickColor) = when (msg.status) {
                            "sent" -> Pair("✓", Color(0xFF8696A0))
                            "delivered" -> Pair("✓✓", Color(0xFF8696A0))
                            else -> Pair("✓✓", Color(0xFF53BDEB))
                        }
                        Text(
                            text = tickText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = tickColor
                        )
                    }
                }
            }
        }
    }
}

// ---------------------- TAB 3: LOGS & FILES (فایل‌ها و گزارش عملکرد) ----------------------
fun openFile(context: Context, fileName: String, isShared: Boolean, repository: FileShareRepository) {
    try {
        if (isShared) {
            val sharedUriStr = repository.getSharedFolderUri()
            if (!sharedUriStr.isNullOrEmpty()) {
                val treeUri = Uri.parse(sharedUriStr)
                val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
                val fileDoc = treeDoc?.findFile(fileName)
                if (fileDoc != null) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileDoc.uri, context.contentResolver.getType(fileDoc.uri) ?: "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "باز کردن با"))
                    return
                }
            }
            Toast.makeText(context, "امکان باز کردن مستقیم این فایل وجود ندارد.", Toast.LENGTH_SHORT).show()
        } else {
            val file = File(repository.getUploadsDirectoryPath(), fileName)
            if (file.exists()) {
                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, file)
                val mimeType = context.contentResolver.getType(uri) ?: "*/*"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "باز کردن با"))
            } else {
                Toast.makeText(context, "فایل یافت نشد.", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "خطا در باز کردن فایل: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun shareFile(context: Context, fileName: String, isShared: Boolean, repository: FileShareRepository) {
    try {
        if (isShared) {
            val sharedUriStr = repository.getSharedFolderUri()
            if (!sharedUriStr.isNullOrEmpty()) {
                val treeUri = Uri.parse(sharedUriStr)
                val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
                val fileDoc = treeDoc?.findFile(fileName)
                if (fileDoc != null) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = context.contentResolver.getType(fileDoc.uri) ?: "*/*"
                        putExtra(Intent.EXTRA_STREAM, fileDoc.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری با"))
                    return
                }
            }
            Toast.makeText(context, "امکان اشتراک‌گذاری این فایل وجود ندارد.", Toast.LENGTH_SHORT).show()
        } else {
            val file = File(repository.getUploadsDirectoryPath(), fileName)
            if (file.exists()) {
                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, file)
                val mimeType = context.contentResolver.getType(uri) ?: "*/*"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری با"))
            } else {
                Toast.makeText(context, "فایل یافت نشد.", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "خطا در اشتراک‌گذاری فایل: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun getUriFileName(context: Context, uri: Uri): String {
    var name = "uploaded_file"
    try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                name = cursor.getString(nameIndex)
            }
        }
        cursor?.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return name
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    val kb = bytes.toDouble() / 1024.0
    return if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%.1f KB", kb)
}

private suspend fun copyFileInChunks(
    context: Context,
    uri: Uri,
    destFile: File,
    forceRestart: Boolean,
    isCanceled: () -> Boolean = { false },
    onProgress: (Long, Long, String) -> Unit // copied, total, state
): String = withContext(Dispatchers.IO) {
    try {
        val resolver = context.contentResolver
        val totalSize = run {
            var size = 0L
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    size = cursor.getLong(sizeIndex)
                }
            }
            if (size <= 0L) destFile.length() else size
        }
        
        if (totalSize == 0L) return@withContext "failed"
        
        val offset = if (forceRestart) 0L else {
            if (destFile.exists()) destFile.length() else 0L
        }
        
        val inputStream = resolver.openInputStream(uri) ?: return@withContext "failed"
        
        try {
            val finalOffset = if (offset >= totalSize) 0L else offset
            
            inputStream.use { input ->
                var skipped = 0L
                while (skipped < finalOffset) {
                    val skip = input.skip(finalOffset - skipped)
                    if (skip <= 0L) break
                    skipped += skip
                }
                
                val chunkSize = 1024 * 1024 // 1MB buffer
                val buffer = ByteArray(chunkSize)
                var currentOffset = finalOffset
                
                val raf = java.io.RandomAccessFile(destFile, "rw")
                if (finalOffset == 0L) raf.setLength(0) // Truncate!
                raf.seek(finalOffset)
                
                raf.use { output ->
                    while (currentOffset < totalSize) {
                        if (isCanceled()) {
                            return@withContext "canceled"
                        }
                        
                        val read = input.read(buffer, 0, chunkSize)
                        if (read == -1) break
                        
                        output.write(buffer, 0, read)
                        currentOffset += read
                        onProgress(currentOffset, totalSize, "Uploading")
                    }
                }
            }
            return@withContext "success"
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "failed"
        }
    } catch (e: Exception) {
        e.printStackTrace()
        "failed"
    }
}

@Composable
fun FileShareLogs(
    repository: FileShareRepository,
    modifier: Modifier = Modifier
) {
    var filesList by remember { mutableStateOf(emptyList<FileItem>()) }
    var refreshTrigger by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Dialog States
    var fileToDelete by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Host uploading states
    var hostIsUploading by remember { mutableStateOf(false) }
    var hostUploadProgress by remember { mutableStateOf(0f) }
    var hostUploadFileName by remember { mutableStateOf("") }
    var hostUploadStateText by remember { mutableStateOf("") } // "Uploading", "Success", "Failed", "Canceled"
    var hostUploadCanceled by remember { mutableStateOf(false) }
    var hostUploadedBytes by remember { mutableStateOf(0L) }
    var hostTotalBytes by remember { mutableStateOf(0L) }
    var hostUploadJob by remember { mutableStateOf<Job?>(null) }
    
    // Duplicate handler states inside Host
    var showDuplicateDialogHost by remember { mutableStateOf(false) }
    var duplicateUriHost by remember { mutableStateOf<Uri?>(null) }
    var duplicateFileNameHost by remember { mutableStateOf("") }

    // Helper functions for host uploading
    fun performHostChunkedUpload(uri: Uri, filename: String, forceRestart: Boolean) {
        hostUploadJob?.cancel()
        hostUploadCanceled = false
        hostIsUploading = true
        hostUploadFileName = filename
        hostUploadStateText = "Uploading"
        hostUploadedBytes = 0L
        hostUploadProgress = 0f
        
        val destFile = File(repository.getUploadsDirectoryPath(), repository.sanitizeFilename(filename))
        
        hostUploadJob = coroutineScope.launch {
            val status = copyFileInChunks(
                context = context,
                uri = uri,
                destFile = destFile,
                forceRestart = forceRestart,
                isCanceled = { hostUploadCanceled },
                onProgress = { copied, total, state ->
                    hostUploadedBytes = copied
                    hostTotalBytes = total
                    hostUploadProgress = if (total > 0) copied.toFloat() / total.toFloat() else 0f
                    hostUploadStateText = state
                }
            )
            
            if (status == "success") {
                hostUploadStateText = "Success"
                Toast.makeText(context, "فایل \"$filename\" با موفقیت به بخش فایل‌ها اضافه شد.", Toast.LENGTH_SHORT).show()
                refreshTrigger++
            } else if (status == "canceled") {
                hostUploadStateText = "Canceled"
                Toast.makeText(context, "آپلود فایل لغو شد.", Toast.LENGTH_SHORT).show()
            } else {
                hostUploadStateText = "Failed"
                Toast.makeText(context, "آپلود فایل ناموفق بود.", Toast.LENGTH_SHORT).show()
            }
            
            delay(3000)
            if (hostUploadStateText != "Uploading") {
                hostIsUploading = false
            }
        }
    }

    val hostFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val filename = getUriFileName(context, uri)
            val alreadyExists = filesList.any { it.name == filename }
            if (alreadyExists) {
                duplicateUriHost = uri
                duplicateFileNameHost = filename
                showDuplicateDialogHost = true
            } else {
                performHostChunkedUpload(uri, filename, forceRestart = false)
            }
        }
    }

    LaunchedEffect(refreshTrigger) {
        filesList = repository.getFilesList()
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .background(Color(0xFF121B22))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App header inside log list
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "مدیریت فایل‌های تبادل شده",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { hostFilePickerLauncher.launch("*/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    enabled = !hostIsUploading
                ) {
                    if (hostIsUploading && hostUploadStateText == "Uploading") {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color.White)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("آپلود فایل", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                IconButton(onClick = { refreshTrigger++ }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "بروزرسانی",
                        tint = Color(0xFF00A884)
                    )
                }
            }
        }

        // Stats boxes row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatsCard(
                title = "تعداد کل فایل‌ها",
                value = "${filesList.size}",
                modifier = Modifier.weight(1f)
            )
            StatsCard(
                title = "فایل‌های آپلودی",
                value = "${filesList.count { it.source == "upload" }}",
                modifier = Modifier.weight(1f)
            )
        }

        // Real-time Host Upload Progress Card
        if (hostIsUploading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
                border = BorderStroke(1.dp, Color(0xFF2A3942))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("⏳", fontSize = 18.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hostUploadStateText == "Uploading") "در حال آپلود: $hostUploadFileName" else "آپلود $hostUploadFileName: ${if (hostUploadStateText == "Success") "موفق" else if (hostUploadStateText == "Canceled") "لغو شد" else "ناموفق"}",
                            fontSize = 12.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        LinearProgressIndicator(
                            progress = { hostUploadProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (hostUploadStateText == "Success") Color(0xFF4CAF50) else if (hostUploadStateText == "Failed") Color(0xFFEF4444) else Color(0xFF00A884),
                            trackColor = Color(0xFF2A3942)
                        )
                        
                        Text(
                            text = "${formatBytes(hostUploadedBytes)} از ${formatBytes(hostTotalBytes)}",
                            fontSize = 10.sp,
                            color = Color(0xFF8696A0),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    
                    Text(
                        text = "${(hostUploadProgress * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = Color(0xFF00A884),
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (hostUploadStateText == "Uploading") {
                        IconButton(
                            onClick = {
                                hostUploadCanceled = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("❌", fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // List representation matching the clean details of Minimal theme
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
            border = BorderStroke(1.dp, Color(0xFF2A3942))
        ) {
            if (filesList.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📁", fontSize = 42.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "هیچ فایلی یافت نشد",
                        fontSize = 13.sp,
                        color = Color(0xFF938F99),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "فایل‌های آپلود شده یا پوشه انتخابی اشتراکی در اینجا لیست می‌شوند.",
                        fontSize = 10.sp,
                        color = Color(0xFF938F99).copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    for (file in filesList) {
                        FileRowItem(
                            file = file,
                            onOpen = {
                                openFile(context, file.name, file.source == "shared", repository)
                            },
                            onShare = {
                                shareFile(context, file.name, file.source == "shared", repository)
                            },
                            onDelete = {
                                fileToDelete = file
                                showDeleteDialog = true
                            }
                        )
                        // Small border separation
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFF49454F).copy(alpha = 0.4f))
                        )
                    }
                }
            }
        }
    }

    // Duplicate File Handle Dialog for Host
    if (showDuplicateDialogHost && duplicateUriHost != null) {
        val dupUri = duplicateUriHost!!
        val dupFilename = duplicateFileNameHost
        AlertDialog(
            onDismissRequest = {
                showDuplicateDialogHost = false
                duplicateUriHost = null
            },
            title = { Text("فایل تکراری یافت شد ⚠️", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "فایلی با نام \"$dupFilename\" از قبل موجود است. چه عملیاتی می‌خواهید انجام دهید؟",
                    color = Color.White,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            showDuplicateDialogHost = false
                            performHostChunkedUpload(dupUri, dupFilename, forceRestart = false) // Resume!
                            duplicateUriHost = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ادامه آپلود فایل ناقص (Resume)", color = Color.White, fontSize = 11.sp)
                    }
                    
                    Button(
                        onClick = {
                            showDuplicateDialogHost = false
                            performHostChunkedUpload(dupUri, dupFilename, forceRestart = true) // Overwrite!
                            duplicateUriHost = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("جایگزینی مجدد فایل کاملاً جدید (Overwrite)", color = Color.White, fontSize = 11.sp)
                    }
                    
                    Button(
                        onClick = {
                            showDuplicateDialogHost = false
                            val ext = dupFilename.substringAfterLast(".", "")
                            val base = dupFilename.substringBeforeLast(".", dupFilename)
                            val newName = "${base}_${System.currentTimeMillis().toString().takeLast(4)}.${ext}"
                            performHostChunkedUpload(dupUri, newName, forceRestart = true)
                            duplicateUriHost = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005C4B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("آپلود با نام متمایز و کپی جدید", color = Color.White, fontSize = 11.sp)
                    }
                    
                    TextButton(
                        onClick = {
                            showDuplicateDialogHost = false
                            duplicateUriHost = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("انصراف", color = Color(0xFF938F99), fontSize = 12.sp)
                    }
                }
            },
            containerColor = Color(0xFF1F2C34)
        )
    }



    // Material 3 Dialog: Delete File Dialog
    if (showDeleteDialog && fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteDialog = false
                fileToDelete = null
            },
            title = { Text("حذف فایل", color = Color.White) },
            text = {
                Text(
                    text = "آیا مطمئن هستید که می‌خواهید فایل \"${fileToDelete!!.name}\" را حذف کنید؟ این عمل غیرقابل بازگشت است.",
                    color = Color.White,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val success = repository.deleteUploadFile(fileToDelete!!.name)
                        if (success) {
                            Toast.makeText(context, "فایل با موفقیت حذف شد.", Toast.LENGTH_SHORT).show()
                            refreshTrigger++
                        } else {
                            Toast.makeText(context, "حذف فایل ناموفق بود.", Toast.LENGTH_SHORT).show()
                        }
                        showDeleteDialog = false
                        fileToDelete = null
                    }
                ) {
                    Text("حذف", color = Color(0xFFF87171))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteDialog = false 
                    fileToDelete = null
                }) {
                    Text("انصراف", color = Color(0xFF938F99))
                }
            },
            containerColor = Color(0xFF2B2930)
        )
    }
}

@Composable
fun StatsCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
        border = BorderStroke(1.dp, Color(0xFF2A3942))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                color = Color(0xFF8696A0),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
                color = Color.White
            )
        }
    }
}

@Composable
fun FileRowItem(
    file: FileItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val sizeStr = remember(file.size) {
        val kb = file.size / 1024.0
        val mb = kb / 1024.0
        if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%.1f KB", kb)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (file.source == "upload") "📥" else "📂",
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (file.source == "upload") "مخزن آپلود" else "پوشه اشتراکی",
                            fontSize = 9.sp,
                            color = if (file.source == "upload") Color(0xFFB2F2BB) else Color(0xFF00A884),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(
                                    color = if (file.source == "upload") Color(0xFF2E3B2E) else Color(0xFF005C4B),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Text(
                            text = sizeStr,
                            fontSize = 10.sp,
                            color = Color(0xFF8696A0)
                        )
                    }
                }
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Open Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF005C4B))
                    .clickable { onOpen() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "باز کردن",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "باز کردن",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Share Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF202C33))
                    .border(BorderStroke(1.dp, Color(0xFF3B4A54)), RoundedCornerShape(8.dp))
                    .clickable { onShare() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "اشتراک‌گذاری",
                    tint = Color(0xFF00A884),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "اشتراک",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00A884)
                )
            }

            if (file.canDelete) {
                Spacer(modifier = Modifier.width(10.dp))
                // Delete Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x33F87171))
                        .border(BorderStroke(1.dp, Color(0x66F87171)), RoundedCornerShape(8.dp))
                        .clickable { onDelete() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف",
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "حذف",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF87171)
                    )
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }
    val sloganAlpha = remember { Animatable(0f) }
    val overallAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // Logo Animation: Scale and Alpha
        launch {
            scale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(
                    durationMillis = 800,
                    easing = FastOutSlowInEasing
                )
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(
                    durationMillis = 600,
                    easing = FastOutSlowInEasing
                )
            )
        }
        
        // Slogan Staggered Animation
        delay(300)
        sloganAlpha.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(
                durationMillis = 600,
                easing = FastOutSlowInEasing
            )
        )

        // Hold splash screen for some time (total 1600ms)
        delay(800)

        // Graceful fade out of the entire splash screen
        overallAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 400)
        )
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121B22))
            .alpha(overallAlpha.value),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxHeight()
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Animated App Logo
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(110.dp)
                    .scale(scale.value)
                    .alpha(alpha.value)
                    .clip(RoundedCornerShape(24.dp))
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Animated brand name
            Text(
                text = "FileShare",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.alpha(alpha.value)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle slogan
            Text(
                text = "انتقال فایل و پیام بدون نیاز به اینترنت",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF00A884),
                modifier = Modifier.alpha(sloganAlpha.value)
            )

            Spacer(modifier = Modifier.weight(1.2f))

            // Bottom signature (like WhatsApp's "from Facebook")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(bottom = 40.dp)
                    .alpha(sloganAlpha.value)
            ) {
                Text(
                    text = "از طرف",
                    fontSize = 11.sp,
                    color = Color(0xFF8696A0)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "AI STUDIO BUILD",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}

@Composable
fun InitialSetupScreen(
    repository: FileShareRepository,
    onSetupCompleted: () -> Unit
) {
    val context = LocalContext.current
    var nickname by remember { mutableStateOf("") }
    
    val avatarOptions = listOf("🧑‍💻", "🦊", "🦁", "🚀", "🎨", "🎮", "🦄", "🎯", "🤖", "🥑")
    var selectedAvatar by remember { mutableStateOf("🧑‍💻") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121B22))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Welcoming logo / text
        Text("💬", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "خوش آمدید به FileShare",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "لطفاً برای اولین بار نام کاربری خود را انتخاب کنید.",
            fontSize = 12.sp,
            color = Color(0xFF8696A0),
            textAlign = TextAlign.Center
        )
        Text(
            text = "این نام کاربری به عنوان شناسه ثابت شما باقی خواهد ماند.",
            fontSize = 11.sp,
            color = Color(0xFF00A884),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(30.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
            border = BorderStroke(1.dp, Color(0xFF2A3942))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name entering input field
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("نام کاربری شما (فارسی یا انگلیسی)", color = Color(0xFF8696A0), fontSize = 12.sp) },
                    placeholder = { Text("مثلاً: رضا خانی", color = Color(0xFF8696A0).copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00A884),
                        unfocusedBorderColor = Color(0xFF2A3942),
                        cursorColor = Color(0xFF00A884)
                    )
                )

                // Select Avatar option grid
                Text(
                    text = "یک آواتار مناسب انتخاب کنید:",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    avatarOptions.take(5).forEach { avatar ->
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    if (selectedAvatar == avatar) Color(0xFF00A884) else Color(0xFF121B22),
                                    CircleShape
                                )
                                .clickable { selectedAvatar = avatar },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(avatar, fontSize = 22.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    avatarOptions.takeLast(5).forEach { avatar ->
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    if (selectedAvatar == avatar) Color(0xFF00A884) else Color(0xFF121B22),
                                    CircleShape
                                )
                                .clickable { selectedAvatar = avatar },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(avatar, fontSize = 22.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val finalName = nickname.trim()
                if (finalName.length < 3) {
                    Toast.makeText(context, "نام کاربری باید حداقل ۳ کاراکتر باشد.", Toast.LENGTH_SHORT).show()
                } else if (!finalName.matches(Regex("^[a-zA-Z0-9ا-یآءئؤه‌يژپچگ\\s]+$"))) {
                    Toast.makeText(context, "نام کاربری فقط می‌تواند شامل حروف، ارقام و فاصله باشد.", Toast.LENGTH_SHORT).show()
                } else {
                    repository.setClientUserId(finalName)
                    repository.setClientAvatar(selectedAvatar)
                    repository.setSetupCompleted(true)
                    Toast.makeText(context, "ثبت نام و ایجاد شناسه موفقیت‌آمیز بود!", Toast.LENGTH_SHORT).show()
                    onSetupCompleted()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(
                text = "ورود و ثبت شناسه کاربری",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SettingsDialog(
    repository: FileShareRepository,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val currentUserId = repository.getClientUserId()
    var inputName by remember { mutableStateOf(currentUserId) }
    
    val avatarOptions = listOf("🧑‍💻", "🦊", "🦁", "🚀", "🎨", "🎮", "🦄", "🎯", "🤖", "🥑")
    var selectedAvatar by remember { mutableStateOf(repository.getClientAvatar()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("پروفایل و تنظیمات کاربری ⚙️", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) },
        containerColor = Color(0xFF1F2C34),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Persistent identity bar
                Column {
                    Text("شناسه ثابت شما:", fontSize = 11.sp, color = Color(0xFF8696A0))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF121B22), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentUserId,
                            color = Color(0xFF00A884),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("user_id", currentUserId)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "شناسه کپی شد", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("📋", fontSize = 12.sp)
                        }
                    }
                }

                // Edit name input
                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    label = { Text("ویرایش نام کاربری (شناسه اتصال غیراصلی)", color = Color(0xFF8696A0), fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00A884),
                        unfocusedBorderColor = Color(0xFF2A3942),
                        cursorColor = Color(0xFF00A884)
                    )
                )

                // Select avatar option
                Text("تغییر شکلک آواتار:", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    avatarOptions.take(5).forEach { avatar ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (selectedAvatar == avatar) Color(0xFF00A884) else Color(0xFF121B22),
                                    CircleShape
                                )
                                .clickable { selectedAvatar = avatar },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(avatar, fontSize = 16.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    avatarOptions.takeLast(5).forEach { avatar ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (selectedAvatar == avatar) Color(0xFF00A884) else Color(0xFF121B22),
                                    CircleShape
                                )
                                .clickable { selectedAvatar = avatar },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(avatar, fontSize = 16.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalName = inputName.trim()
                    if (finalName.length < 3) {
                        Toast.makeText(context, "نام کاربری نباید کمتر از ۳ کاراکتر باشد.", Toast.LENGTH_SHORT).show()
                    } else if (!finalName.matches(Regex("^[a-zA-Z0-9ا-یآءئؤه‌يژپچگ\\s]+$"))) {
                        Toast.makeText(context, "نام کاربری فقط شامل حروف، ارقام و فاصله است.", Toast.LENGTH_SHORT).show()
                    } else {
                        repository.setClientUserId(finalName)
                        repository.setClientAvatar(selectedAvatar)
                        Toast.makeText(context, "اطلاعات با موفقیت ذخیره گردید.", Toast.LENGTH_SHORT).show()
                        onSaved()
                    }
                }
            ) {
                Text("ذخیره", color = Color(0xFF00A884), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = Color(0xFF8696A0))
            }
        }
    )
}
