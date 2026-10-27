package com.bitchat.android.hotspot

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.R
import com.bitchat.android.ui.theme.BitchatFontFamily
import com.bitchat.android.ui.theme.BitchatTheme
import com.bitchat.android.util.UniversalApkManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File

/**
 * Activity for managing Wi-Fi P2P hotspot for offline APK sharing.
 * Pure Compose implementation, no fragments.
 */
class HotspotActivity : ComponentActivity() {

    companion object {
        const val EXTRA_APK_PATH = "apk_path"
        private const val TAG = "HotspotActivity"
    }

    private val viewModel: HotspotViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Stop action from the notification while no session lives in this process any more
        if (intent.action == HotspotStatus.ACTION_STOP) {
            HotspotStatus.requestStop()
            HotspotStatus.ended()
            finish()
            return
        }

        // Get APK path from intent
        val apkPath = intent.getStringExtra(EXTRA_APK_PATH)
        val apkFile = if (apkPath != null) {
            File(apkPath)
        } else {
            // Fallback: Try to get cached APK
            UniversalApkManager(this).getCachedApk()
        }

        if (apkFile == null || !apkFile.exists()) {
            // No APK available, show error and finish
            finish()
            return
        }

        // Leaving while the hotspot runs goes back to the chat and keeps it on; the chat
        // banner and the notification show it and stop it.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        lifecycleScope.launch { HotspotStatus.stopRequests.collect { finish() } }

        setContent {
            BitchatTheme {
                HotspotScreen(
                    viewModel = viewModel,
                    apkFile = apkFile,
                    onClose = { leave() }
                )
            }
        }
    }

    private fun leave() {
        if (viewModel.state.value is HotspotViewModel.HotspotState.Active) {
            startActivity(
                Intent(this, com.bitchat.android.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        } else {
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle notification action to stop hotspot
        if (intent.action == HotspotStatus.ACTION_STOP) {
            viewModel.stopHotspot()
            finish()
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotScreen(
    viewModel: HotspotViewModel,
    apkFile: File,
    onClose: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.hs_title),
                        fontFamily = BitchatFontFamily
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Crossfade(
            targetState = state,
            label = "HotspotStateCrossfade",
            modifier = Modifier.padding(padding)
        ) { currentState ->
            when (currentState) {
                is HotspotViewModel.HotspotState.Intro -> {
                    IntroScreen(
                        onStartHotspot = { viewModel.startHotspot(apkFile) }
                    )
                }
                is HotspotViewModel.HotspotState.Starting -> {
                    LoadingScreen()
                }
                is HotspotViewModel.HotspotState.ConfirmDisconnect -> {
                    ExistingGroupConfirmation(
                        onConfirm = viewModel::confirmDisconnectAndStart,
                        onCancel = viewModel::cancelDisconnect
                    )
                }
                is HotspotViewModel.HotspotState.Active -> {
                    ActiveHotspotScreen(state = currentState)
                }
                is HotspotViewModel.HotspotState.Error -> {
                    ErrorScreen(
                        message = currentState.message,
                        onRetry = { viewModel.resetToIntro() },
                        onClose = onClose
                    )
                }
            }
        }
    }
}

@Composable
private fun ExistingGroupConfirmation(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.hotspot_disconnect_title)) },
        text = { Text(stringResource(R.string.hotspot_disconnect_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.hotspot_disconnect_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun IntroScreen(onStartHotspot: () -> Unit) {
    val requiredPermissions = remember { HotspotPermissions.requiredForSdk() }
    val permissionState = rememberMultiplePermissionsState(requiredPermissions) { results ->
        if (requiredPermissions.all { results[it] == true }) {
            onStartHotspot()
        }
    }

    // chatbit: the start button stays pinned at the bottom; only the explanations scroll
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = stringResource(R.string.hs_intro_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.hs_how_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                InfoItem(stringResource(R.string.hs_how_1))
                InfoItem(stringResource(R.string.hs_how_2))
                InfoItem(stringResource(R.string.hs_how_3))
                InfoItem(stringResource(R.string.hs_how_4))
            }
        }

        // Permission rationale (if needed)
        if (!permissionState.allPermissionsGranted && permissionState.shouldShowRationale) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ℹ️ " + stringResource(R.string.install_apk_permission_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = when {
                            Build.VERSION.SDK_INT >= HotspotPermissions.ANDROID_17_API_LEVEL ->
                                stringResource(R.string.hs_perm_nearby_local)
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                                stringResource(R.string.hs_perm_nearby)
                            else ->
                                stringResource(R.string.hs_perm_location)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "⚠️ " + stringResource(R.string.hs_note_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.hs_note_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

    }

        Button(
            onClick = {
                // Check permission before starting hotspot
                if (permissionState.allPermissionsGranted) {
                    // No permission needed or already granted
                    onStartHotspot()
                } else {
                    // Request permission (auto-start handled by onPermissionResult callback)
                    permissionState.launchMultiplePermissionRequest()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                // Starting the hotspot is the user's action. Android will ask
                // for the required permission only when it has not already
                // been granted.
                text = stringResource(R.string.hs_start),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun InfoItem(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = stringResource(R.string.hs_starting),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun ActiveHotspotScreen(state: HotspotViewModel.HotspotState.Active) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Wi-Fi", stringResource(R.string.hs_tab_website))

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Status banner
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.hs_active),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.hs_connected, state.connectedPeers),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        HotspotLiveControls()

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontFamily = BitchatFontFamily,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // Tab content
        when (selectedTab) {
            0 -> WifiTabContent(
                ssid = state.ssid,
                password = state.password
            )
            1 -> WebsiteTabContent(
                ipAddress = state.ipAddress,
                port = state.port
            )
        }
    }
}

@Composable
fun WifiTabContent(ssid: String, password: String) {
    val clipboardManager = LocalClipboardManager.current
    val secureCopyContext = androidx.compose.ui.platform.LocalContext.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = stringResource(R.string.hs_step1),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.hs_step1_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        // QR Code
        val qrSize = with(LocalDensity.current) { 280.dp.toPx().toInt() }
        val wifiQr = remember(ssid, password, qrSize) {
            QrCodeGenerator.generateWifiQr(ssid, password, qrSize)
        }

        if (wifiQr != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Image(
                    bitmap = wifiQr.asImageBitmap(),
                    contentDescription = stringResource(R.string.hs_wifi_qr),
                    modifier = Modifier.size(280.dp)
                )
            }
        }

        Text(
            text = stringResource(R.string.hs_manual),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        // SSID
        CredentialCard(
            label = stringResource(R.string.hs_ssid),
            value = ssid,
            onCopy = {
                com.bitchat.android.security.SecureClipboard.copy(secureCopyContext, "ssid", ssid, sensitive = false)
            }
        )

        // Password
        CredentialCard(
            label = stringResource(R.string.hs_password),
            value = password,
            onCopy = {
                com.bitchat.android.security.SecureClipboard.copy(secureCopyContext, "password", password)
            }
        )
    }
}

@Composable
fun WebsiteTabContent(ipAddress: String, port: Int) {
    val url = "http://$ipAddress:$port"
    val clipboardManager = LocalClipboardManager.current
    val secureCopyContext = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = stringResource(R.string.hs_step2),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.hs_step2_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        // QR Code
        val qrSize = with(LocalDensity.current) { 280.dp.toPx().toInt() }
        val urlQr = remember(url, qrSize) {
            QrCodeGenerator.generateUrlQr(url, qrSize)
        }

        if (urlQr != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Image(
                    bitmap = urlQr.asImageBitmap(),
                    contentDescription = stringResource(R.string.hs_url_qr),
                    modifier = Modifier.size(280.dp)
                )
            }
        }

        Text(
            text = stringResource(R.string.hs_open_browser),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        // URL
        CredentialCard(
            label = stringResource(R.string.hs_url),
            value = url,
            onCopy = {
                com.bitchat.android.security.SecureClipboard.copy(secureCopyContext, "url", url, sensitive = false)
            }
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📱 " + stringResource(R.string.hs_instructions_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.hs_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun CredentialCard(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = BitchatFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.hs_copy),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "❌",
            fontSize = 64.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.hs_error),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.hs_try_again))
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onClose) {
            Text(stringResource(R.string.cd_close))
        }
    }
}

/** Auto-stop countdown and the opt-in mesh announcement. */
@Composable
private fun HotspotLiveControls() {
    val live by HotspotStatus.live.collectAsState()
    val announce by HotspotStatus.announce.collectAsState()
    val l = live ?: return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); now = System.currentTimeMillis() } }
    val minutesLeft = (((l.stopsAt - now).coerceAtLeast(0) + 59_999) / 60_000).toInt()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.hs_auto_stop_in, minutesLeft, l.downloads),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { HotspotStatus.extend() }) { Text(stringResource(R.string.hs_extend)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.hs_announce_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.hs_announce_desc), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = announce, onCheckedChange = { HotspotStatus.setAnnounce(it) })
        }
    }
}
