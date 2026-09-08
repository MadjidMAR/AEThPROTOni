package io.github.immaghzbad.aetherst

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import io.github.immaghzbad.aetherst.platform.PlatformContext
import io.github.immaghzbad.aetherst.shared.App
import io.github.immaghzbad.aetherst.shared.platform.Bridge

class MainActivity : ComponentActivity() {

    private var pendingSaveContent: String? = null
    private var saveCallback: ((Boolean) -> Unit)? = null
    private var pickCallback: ((String?) -> Unit)? = null

    private val saveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(pendingSaveContent?.toByteArray() ?: ByteArray(0))
                }
                saveCallback?.invoke(true)
            } catch (e: Exception) {
                io.github.immaghzbad.aetherst.shared.data.LogRepository.w("saveLauncher failed: ${e.message}")
                saveCallback?.invoke(false)
            }
        } else {
            saveCallback?.invoke(false)
        }
        pendingSaveContent = null
        saveCallback = null
    }

    private val pickLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                pickCallback?.invoke(content)
            } catch (e: Exception) {
                io.github.immaghzbad.aetherst.shared.data.LogRepository.w("pickLauncher failed: ${e.message}")
                pickCallback?.invoke(null)
            }
        } else {
            pickCallback?.invoke(null)
        }
        pickCallback = null
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Bridge.saveFile = { fileName, content, onResult ->
            pendingSaveContent = content
            saveCallback = onResult
            saveLauncher.launch(fileName)
        }
        
        Bridge.pickFile = { onResult ->
            pickCallback = onResult
            pickLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
        }

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            App(PlatformContext(this))
        }
    }

    override fun onDestroy() {
        saveCallback = null
        pickCallback = null
        pendingSaveContent = null
        super.onDestroy()
    }
}
