package io.github.immaghzbad.aetherst

import android.app.Application
import androidx.annotation.Keep
import io.github.immaghzbad.aetherst.core.ConnectionController
import io.github.immaghzbad.aetherst.service.AetherWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

@Keep
class AetherSTApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
        observeStatusForWidgets()
        
        io.github.immaghzbad.aetherst.shared.platform.Bridge.submitLoginCode = { code ->
            ConnectionController.getInstance(this).submitLoginCode(code)
        }
    }

    private fun observeStatusForWidgets() {
        applicationScope.launch {
            ConnectionController.status.collect {
                AetherWidgetProvider.updateAllWidgets(this@AetherSTApp)
            }
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTrace = sw.toString()
                val crashLog = "Thread: ${thread.name}\n\nException: ${throwable.localizedMessage}\n\nStack Trace:\n$stackTrace"
                val file = File(cacheDir, "last_crash.log")
                file.writeText(crashLog)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
