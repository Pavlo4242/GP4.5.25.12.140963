package com.grindrplus.hooks

import android.app.Activity
import android.app.Application
import android.os.Build
import com.grindrplus.core.Logger
import com.grindrplus.core.LogSource
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import java.io.File

/**
 * Diagnostic hook to identify what detection methods are being called
 * Enable this temporarily to see what's triggering detection
 */
class DetectionDiagnostics : Hook(
    "Detection Diagnostics",
    "Logs all potential detection method calls to identify what's being checked"
) {
    private val methodCallLog = mutableListOf<String>()
    private var diagnosticsStartTime = System.currentTimeMillis()

    override fun init() {
        // FIX: Replaced Logger.xposedLog with Logger.i and added a LogSource
        Logger.i("=== DETECTION DIAGNOSTICS STARTED ===", LogSource.MODULE)
        Logger.i("Enable this hook to see what detection methods are called", LogSource.MODULE)
        Logger.i("Review logs after app closes to identify detection vectors", LogSource.MODULE)

        diagnosticsStartTime = System.currentTimeMillis()

        // Monitor all the detection vectors
        monitorApplicationLifecycle()
        monitorActivityLifecycle()
        monitorFileAccess()
        monitorBuildAccess()
        monitorReflection()
        monitorSystemProperties()
        monitorPackageManager()
        monitorNativeLibraries()
        monitorProcessChecks()

        Logger.i("=== DIAGNOSTICS HOOKS INSTALLED ===", LogSource.MODULE)
    }

    private fun logCall(category: String, details: String) {
        val elapsed = System.currentTimeMillis() - diagnosticsStartTime
        val entry = "[+${elapsed}ms] [$category] $details"
        methodCallLog.add(entry)
        // FIX: Replaced Logger.xposedLog with Logger.i and added a LogSource
        Logger.i(entry, LogSource.MODULE)

        // Also log to file for later analysis
        Logger.writeRaw(entry)
    }

    private fun getStackTraceString(): String {
        return Thread.currentThread().stackTrace
            .drop(3) // Skip getStackTrace, logCall, and caller
            .take(10) // Only first 10 frames
            .joinToString("\n  ") {
                "${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})"
            }
    }

    private fun monitorApplicationLifecycle() {
        try {
            Application::class.java.hook("onCreate", HookStage.BEFORE) { param ->
                logCall("LIFECYCLE", "Application.onCreate() - App is starting")
            }

            Application::class.java.hook("onCreate", HookStage.AFTER) { param ->
                logCall("LIFECYCLE", "Application.onCreate() completed")
            }
        } catch (e: Exception) {
            Logger.e("Failed to monitor Application lifecycle: ${e.message}", LogSource.MODULE)
        }
    }

    private fun monitorActivityLifecycle() {
        try {
            Activity::class.java.hook("onCreate", HookStage.BEFORE) { param ->
                // FIX: Changed generic thisObject<T>() to a standard cast
                val activity = param.thisObject() as Activity
                logCall("LIFECYCLE", "Activity.onCreate() - ${activity.javaClass.simpleName}")
            }

            Activity::class.java.hook("onCreate", HookStage.AFTER) { param ->
                // FIX: Changed generic thisObject<T>() to a standard cast
                val activity = param.thisObject() as Activity
                logCall("LIFECYCLE", "Activity.onCreate() completed - ${activity.javaClass.simpleName}")
            }

            Activity::class.java.hook("finish", HookStage.BEFORE) { param ->
                // FIX: Changed generic thisObject<T>() to a standard cast
                val activity = param.thisObject() as Activity
                val stack = getStackTraceString()
                logCall("LIFECYCLE", "Activity.finish() called on ${activity.javaClass.simpleName}\nStack:\n  $stack")
            }

            Activity::class.java.hook("finishAndRemoveTask", HookStage.BEFORE) { param ->
                // FIX: Changed generic thisObject<T>() to a standard cast
                val activity = param.thisObject() as Activity
                val stack = getStackTraceString()
                logCall("LIFECYCLE", "Activity.finishAndRemoveTask() on ${activity.javaClass.simpleName}\nStack:\n  $stack")
            }
        } catch (e: Exception) {
            Logger.e("Failed to monitor Activity lifecycle: ${e.message}", LogSource.MODULE)
        }
    }

    private fun monitorFileAccess() {
        try {
            File::class.java.hook("exists", HookStage.BEFORE) { param ->
                // FIX: Changed generic thisObject<T>() to a standard cast
                val file = param.thisObject() as File
                val path = file.absolutePath

                // Only log suspicious file checks
                val suspicious = listOf(
                    "su", "goldfish", "qemu", "xposed", "magisk",
                    "superuser", "genymotion", "vbox", "nox"
                )

                if (suspicious.any { path.contains(it, ignoreCase = true) }) {
                    logCall("FILE", "Checking file: $path")
                }
            }

            File::class.java.hook("canExecute", HookStage.BEFORE) { param ->
                // FIX: Changed generic thisObject<T>() to a standard cast
                val file = param.thisObject() as File
                val path = file.absolutePath

                if (path.contains("su", ignoreCase = true)) {
                    logCall("FILE", "Checking if executable: $path")
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to monitor file access: ${e.message}", LogSource.MODULE)
        }
    }

    private fun monitorBuildAccess() {
        try {
            // Monitor when app reads Build properties
            Class::class.java.hook("getField", HookStage.BEFORE) { param ->
                // FIX: Changed generic thisObject<T>() to a standard cast
                val clazz = param.thisObject() as Class<*>
                val fieldName = param.arg<String>(0)

                if (clazz.name == "android.os.Build") {
                    logCall("BUILD", "Reading Build.$fieldName")

                    // Log the actual value being read
                    try {
                        val field = clazz.getField(fieldName)
                        val value = field.get(null)
                        logCall("BUILD", "  Value: $value")
                    } catch (e: Exception) {
                        // Continue
                    }
                }
            }

            try {
                Build::class.java.hook("getRadioVersion", HookStage.BEFORE) { param ->
                    logCall("BUILD", "Build.getRadioVersion() called")
                }

                Build::class.java.hook("getRadioVersion", HookStage.AFTER) { param ->
                    val result = param.getResult()
                    logCall("BUILD", "  getRadioVersion() returned: $result")
                }
            } catch (e: Exception) {
                // Method doesn't exist on all versions
            }
        } catch (e: Exception) {
            Logger.e("Failed to monitor Build access: ${e.message}", LogSource.MODULE)
        }
    }

    private fun monitorReflection() {
        try {
            Class::class.java.hook("forName", HookStage.BEFORE) { param ->
                val className = param.arg<String>(0)

                // Only log if searching for security-related classes
                val interesting = listOf(
                    "xposed", "substrate", "magisk", "superuser",
                    "SafetyNet", "Integrity", "RootBeer"
                )

                if (interesting.any { className.contains(it, ignoreCase = true) }) {
                    logCall("REFLECTION", "Class.forName($className)")
                }
            }

            Class::class.java.hook("getDeclaredMethod", HookStage.BEFORE) { param ->
                // FIX: Changed generic thisObject<T>() to a standard cast
                val clazz = param.thisObject() as Class<*>
                val methodName = param.arg<String>(0)

                // Log suspicious method lookups
                if (clazz.name.contains("android.os.Build") ||
                    clazz.name.contains("SystemProperties")) {
                    logCall("REFLECTION", "Getting method ${clazz.name}.$methodName")
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to monitor reflection: ${e.message}", LogSource.MODULE)
        }
    }

    private fun monitorSystemProperties() {
        try {
            val systemPropsClass = findClass("android.os.SystemProperties")

            systemPropsClass.hook("get", HookStage.BEFORE) { param ->
                val key = param.arg<String>(0)

                // Only log emulator/root related properties
                val interesting = listOf(
                    "qemu", "ro.hardware", "ro.kernel", "ro.product",
                    "ro.build", "ro.secure", "ro.debuggable"
                )

                if (interesting.any { key.contains(it, ignoreCase = true) }) {
                    logCall("SYSPROP", "Reading system property: $key")
                }
            }

            systemPropsClass.hook("get", HookStage.AFTER) { param ->
                val key = param.arg<String>(0)
                val value = param.getResult() as? String

                val interesting = listOf(
                    "qemu", "ro.hardware", "ro.kernel", "ro.product"
                )

                if (interesting.any { key.contains(it, ignoreCase = true) }) {
                    logCall("SYSPROP", "  Value: $value")
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to monitor system properties: ${e.message}", LogSource.MODULE)
        }
    }

    private fun monitorPackageManager() {
        try {
            android.content.pm.PackageManager::class.java.hook(
                "getInstalledApplications",
                HookStage.BEFORE
            ) { param ->
                logCall("PKGMGR", "getInstalledApplications() - Scanning for installed apps")
            }

            android.content.pm.PackageManager::class.java.hook(
                "getPackageInfo",
                HookStage.BEFORE
            ) { param ->
                val packageName = param.arg<String>(0)
                val flags = param.argNullable<Int>(1) ?: 0

                // Check if requesting signatures
                val requestingSig = flags and android.content.pm.PackageManager.GET_SIGNATURES != 0

                if (requestingSig) {
                    logCall("PKGMGR", "getPackageInfo($packageName) with GET_SIGNATURES")
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to monitor PackageManager: ${e.message}", LogSource.MODULE)
        }
    }

    private fun monitorNativeLibraries() {
        try {
            Runtime::class.java.hook("loadLibrary", HookStage.BEFORE) { param ->
                val libName = param.arg<String>(0)
                logCall("NATIVE", "Runtime.loadLibrary($libName)")
            }

            System::class.java.hook("loadLibrary", HookStage.BEFORE) { param ->
                val libName = param.arg<String>(0)
                logCall("NATIVE", "System.loadLibrary($libName)")
            }

            System::class.java.hook("load", HookStage.BEFORE) { param ->
                val path = param.arg<String>(0)
                logCall("NATIVE", "System.load($path)")
            }
        } catch (e: Exception) {
            Logger.e("Failed to monitor native libraries: ${e.message}", LogSource.MODULE)
        }
    }

    private fun monitorProcessChecks() {
        try {
            Runtime::class.java.hook("exec", HookStage.BEFORE) { param ->
                val command = when (val arg = param.args()[0]) {
                    is String -> arg
                    is Array<*> -> (arg as? Array<String>)?.joinToString(" ") ?: "unknown"
                    else -> "unknown"
                }

                logCall("PROCESS", "Runtime.exec($command)")

                // This might be checking for su or other binaries
                if (command.contains("su") ||
                    command.contains("which") ||
                    command.contains("getprop")) {
                    logCall("PROCESS", "  SUSPICIOUS: Possible root/emulator check")
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to monitor process checks: ${e.message}", LogSource.MODULE)
        }
    }

    override fun cleanup() {
        // FIX: Replaced Logger.xposedLog with Logger.i and added a LogSource
        Logger.i("=== DETECTION DIAGNOSTICS SUMMARY ===", LogSource.MODULE)
        Logger.i("Total method calls logged: ${methodCallLog.size}", LogSource.MODULE)
        Logger.i("Check logs for detailed trace", LogSource.MODULE)
        Logger.i("=====================================", LogSource.MODULE)
    }
}