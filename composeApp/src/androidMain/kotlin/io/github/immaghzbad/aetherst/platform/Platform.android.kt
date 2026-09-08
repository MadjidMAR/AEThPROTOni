package io.github.immaghzbad.aetherst.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import io.github.immaghzbad.aetherst.shared.model.AppInfo
import io.github.immaghzbad.aetherst.shared.platform.Bridge
import java.io.File
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

actual class PlatformContext(val context: Context)

@Composable
actual fun AppIcon(app: AppInfo, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap = remember(app.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(app.packageName).toBitmap().asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Color.Black.copy(alpha = 0.3f))
        )
    }
}

class AndroidVpnController(private val context: Context) : VpnController {
    override fun startVpn() {
        val intent = Intent().apply {
            setClassName(context.packageName, "io.github.immaghzbad.aetherst.service.AetherVpnService")
            action = "io.github.immaghzbad.aetherst.ACTION_START"
        }
        context.startForegroundService(intent)
    }

    override fun stopVpn() {
        val intent = Intent().apply {
            setClassName(context.packageName, "io.github.immaghzbad.aetherst.service.AetherVpnService")
            action = "io.github.immaghzbad.aetherst.ACTION_STOP"
        }
        context.startService(intent)
    }

    override fun startProxy() {
        val intent = Intent().apply {
            setClassName(context.packageName, "io.github.immaghzbad.aetherst.service.AetherProxyService")
            action = "io.github.immaghzbad.aetherst.PROXY_START"
        }
        context.startForegroundService(intent)
    }

    override fun stopProxy() {
        val intent = Intent().apply {
            setClassName(context.packageName, "io.github.immaghzbad.aetherst.service.AetherProxyService")
            action = "io.github.immaghzbad.aetherst.PROXY_STOP"
        }
        context.startService(intent)
    }

    override fun restartVpn() {
        val intent = Intent().apply {
            setClassName(context.packageName, "io.github.immaghzbad.aetherst.service.AetherVpnService")
            action = "io.github.immaghzbad.aetherst.ACTION_RESTART"
        }
        context.startForegroundService(intent)
    }

    override fun restartProxy() {
        val intent = Intent().apply {
            setClassName(context.packageName, "io.github.immaghzbad.aetherst.service.AetherProxyService")
            action = "io.github.immaghzbad.aetherst.PROXY_RESTART"
        }
        context.startForegroundService(intent)
    }

    override fun submitLoginCode(code: String) {
        Bridge.submitLoginCode?.invoke(code)
    }

    override fun prepareVpn(onPermissionRequired: () -> Unit): Boolean {
        val intent = VpnService.prepare(context)
        if (intent != null) {
            val activity = context as? Activity
            if (activity != null) {
                activity.startActivityForResult(intent, 102)
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            onPermissionRequired()
            return false
        }
        return true
    }

    override fun isVpnPrepared(): Boolean {
        return VpnService.prepare(context) == null
    }
}

class AndroidTrafficProvider : TrafficProvider {
    override fun getTxBytes(): Long = TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(0)
    override fun getRxBytes(): Long = TrafficStats.getUidRxBytes(Process.myUid()).coerceAtLeast(0)
}

class AndroidAppInfoProvider(private val context: Context) : AppInfoProvider {
    override suspend fun getInstalledApps(): List<AppInfo> {
        val pm = context.packageManager
        return (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            @Suppress("DEPRECATION") pm.getInstalledApplications(PackageManager.GET_META_DATA)
        })
            .filter { it.packageName != context.packageName }
            .map { app ->
                AppInfo(
                    name = pm.getApplicationLabel(app).toString(),
                    packageName = app.packageName,
                    icon = null,
                    isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }.sortedBy { it.name.lowercase() }
    }
}

class AndroidSystemUtils(private val context: Context) : SystemUtils {
    override fun isBatteryOptimized(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }
    override fun getFilesDir(): String = context.filesDir.absolutePath
    override fun getCacheDir(): String = context.cacheDir.absolutePath
    override fun getPackageName(): String = context.packageName
    override fun getAppVersion(): String = try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, 0)
        }
        info.versionName ?: "1.6.9"
    } catch (_: Exception) { "1.6.9" }
    override fun getAppVersionCode(): Int = try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, 0)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION") info.versionCode
        }
    } catch (_: Exception) { 5 }
    override fun exitApp() { Process.killProcess(Process.myPid()) }

    override fun readLastCrashLog(): String? {
        return try {
            val file = File(context.cacheDir, "last_crash.log")
            if (file.exists()) file.readText() else null
        } catch (_: Exception) {
            null
        }
    }

    override fun clearCrashLog() {
        try {
            File(context.cacheDir, "last_crash.log").delete()
        } catch (_: Exception) {
        }
    }

    override fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AetherST", text)
        clipboard.setPrimaryClip(clip)
    }

    override fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val activity = context as? Activity
            if (activity != null) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            } else {
                val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    override fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    @SuppressLint("BatteryLife")
    override fun requestBatteryOptimization() {
        try {
            val intent = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun openVpnSettings() {
        try {
            val intent = Intent(AndroidSettings.ACTION_VPN_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    override fun exportFile(fileName: String, content: String, onResult: (Boolean) -> Unit) {
        Bridge.saveFile?.invoke(fileName, content, onResult)
    }

    override fun importFile(onResult: (String?) -> Unit) {
        Bridge.pickFile?.invoke(onResult)
    }

    override fun shareFile(fileName: String, content: String) {
        try {
            val file = File(context.cacheDir, fileName)
            file.writeText(content)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share Logs").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {}
    }

    override fun readInternalAsset(fileName: String): String? {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    override fun setSystemProxy(host: String, port: Int) {}
    override fun clearSystemProxy() {}
    override fun setSystemDns(dnsList: String) {}
    override fun clearSystemDns() {}
    override fun isAdministrator(): Boolean = true
    override fun relaunchAsAdmin() {}

    @SuppressLint("MissingPermission")
    override fun getInterfaceMtu(): Int {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val lp = cm.getLinkProperties(cm.activeNetwork)
            val ifaceName = lp?.interfaceName
            if (ifaceName != null) {
                NetworkInterface.getByName(ifaceName)?.mtu ?: 1500
            } else {
                1500
            }
        } catch (_: Exception) {
            1500
        }
    }

    @SuppressLint("MissingPermission")
    override fun isNetworkConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    override fun execPing(host: String, size: Int, timeoutMs: Int, dontFragment: Boolean): Boolean {
        val sanitized = host.trim()
        if (sanitized.isEmpty() || sanitized.length > 253 || sanitized.contains(Regex("""[^a-zA-Z0-9.\-:\[\]]"""))) {
            return false
        }
        return try {
            val timeoutSec = (timeoutMs / 1000).coerceAtLeast(1)
            val pb = if (dontFragment) {
                ProcessBuilder("ping", "-c", "1", "-s", size.toString(), "-M", "do", "-W", timeoutSec.toString(), sanitized)
            } else {
                ProcessBuilder("ping", "-c", "1", "-s", size.toString(), "-W", timeoutSec.toString(), sanitized)
            }
            pb.redirectErrorStream(true)
            val proc = pb.start()
            proc.waitFor(timeoutMs.toLong(), TimeUnit.MILLISECONDS) && proc.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }
}

actual fun getVpnController(context: PlatformContext): VpnController = AndroidVpnController(context.context)
actual fun getTrafficProvider(context: PlatformContext): TrafficProvider = AndroidTrafficProvider()
actual fun getAppInfoProvider(context: PlatformContext): AppInfoProvider = AndroidAppInfoProvider(context.context)
actual fun getSystemUtils(context: PlatformContext): SystemUtils = AndroidSystemUtils(context.context)

actual fun getCurrentTimestamp(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return formatter.format(Date())
}

actual val isDesktop: Boolean = false

actual val isWindows: Boolean = false

actual fun getDeviceModel(): String {
    val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    val model = Build.MODEL
    return if (model.startsWith(manufacturer, ignoreCase = true)) {
        model
    } else {
        "$manufacturer $model"
    }
}

actual fun getOsVersion(): String {
    return "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}
