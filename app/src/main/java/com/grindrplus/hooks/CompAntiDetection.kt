package com.grindrplus.hooks

import android.app.Activity
import android.content.Context
import android.os.Build
import com.grindrplus.core.Config
import com.grindrplus.core.Logger
import com.grindrplus.core.LogSource
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import com.grindrplus.utils.hookConstructor
import java.io.File
import com.grindrplus.XposedLoader

class ComprehensiveAntiDetection : Hook(
    "Comprehensive Anti Detection",
    "Advanced protection against root, emulator, and Xposed detection"
) {
    // Obfuscated class names - update these for your Grindr version
    private val grindrMiscClass = "mg.n" // Detection utility class
    private val devicePropertiesCollector = "siftscience.android.DevicePropertiesCollector"
    private val commonUtils = "com.google.firebase.crashlytics.internal.common.CommonUtils"
    private val osData = "com.google.firebase.crashlytics.internal.model.AutoValue_StaticSessionData_OsData"

// In CompAntiDetection.kt (ComprehensiveAntiDetection class)
// Update the init() method to check sub-hook toggles:

    override fun init() {
        Logger.i("Initializing Comprehensive Anti-Detection...", LogSource.MODULE)

        // Check each sub-hook before installing
        if (Config.get("sub_hook_comprehensive_activity_finish", true) as Boolean) {
            hookActivityLifecycle()
        }
//
//        if (com.grindrplus.core.Config.get("sub_hook_comprehensive_filesystem", true) as Boolean) {
//            hookFileSystemAccess()
//        }
//
//        if (Config.get("sub_hook_comprehensive_build_props", true) as Boolean) {
//            hookBuildProperties()
//        }
//
//        if (Config.get("sub_hook_comprehensive_pkg_mgr", true) as Boolean) {
//            hookPackageManager()
//        }
//
//        if (Config.get("sub_hook_comprehensive_native_libs", true) as Boolean) {
//            hookRuntimeLibraries()
//        }
//
//        if (Config.get("sub_hook_comprehensive_play_integrity", true) as Boolean) {
//            hookPlayIntegrity()
//        }

        // Always install these core hooks
        hookGrindrDetection()
        hookFirebaseChecks()
        hookSiftScience()
  //      hookSystemProperties()
        hookStackTraceDetection()
        hookDebugDetection()
        hookSignatureVerification()
 //       hookNativeDetection()

        Logger.s("Comprehensive Anti-Detection initialized", LogSource.MODULE)
    }

    /**
     * Hook Grindr's main detection class
     * Search for: "sdk_gphone", "emulator", "simulator", "google_sdk"
     */
    private fun hookGrindrDetection() {
        try {
            // Hook all methods that might be detection methods
            // Since obfuscation changes names, we hook multiple candidates
            val detectionClass = findClass(grindrMiscClass)

            // Common obfuscated names for detection methods
            listOf("M", "N", "O", "P", "Q", "a", "b", "c").forEach { methodName ->
                try {
                    detectionClass.hook(methodName, HookStage.AFTER) { param ->
                        val result = param.getResult()

                        // If it returns boolean and it's true, make it false
                        if (result is Boolean && result) {
                            Logger.i("Blocked detection method: $methodName returned true", LogSource.MODULE)
                            param.setResult(false)
                        }

                        // If it returns a list/collection and it's not empty, make it empty
                        if (result is Collection<*> && result.isNotEmpty()) {
                            Logger.i("Blocked detection method: $methodName returned non-empty collection", LogSource.MODULE)
                            param.setResult(emptyList<Any>())
                        }
                    }
                } catch (e: Exception) {
                    // Method doesn't exist, continue
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to hook Grindr detection: ${e.message}", LogSource.MODULE)
        }
    }

    /**
     * Hook Firebase Crashlytics detection methods
     */
    private fun hookFirebaseChecks() {
        try {
            val crashlyticsClass = findClass(commonUtils)

            crashlyticsClass.hook("isRooted", HookStage.BEFORE) { param ->
                param.setResult(false)
                Logger.i("Blocked Firebase isRooted check", LogSource.MODULE)
            }

            crashlyticsClass.hook("isEmulator", HookStage.BEFORE) { param ->
                param.setResult(false)
                Logger.i("Blocked Firebase isEmulator check", LogSource.MODULE)
            }

            crashlyticsClass.hook("isAppDebuggable", HookStage.BEFORE) { param ->
                param.setResult(false)
                Logger.i("Blocked Firebase isAppDebuggable check", LogSource.MODULE)
            }

            // Hook the OS data constructor to prevent root flag
            findClass(osData).hookConstructor(HookStage.BEFORE) { param ->
                param.setArg(2, false) // isRooted parameter
            }
        } catch (e: Exception) {
            Logger.e("Failed to hook Firebase checks: ${e.message}", LogSource.MODULE)
        }
    }

    /**
     * Hook Sift Science device fingerprinting
     */
    private fun hookSiftScience() {
        try {
            val siftClass = findClass(devicePropertiesCollector)

            siftClass.hook("existingRWPaths", HookStage.BEFORE) { param ->
                param.setResult(emptyList<String>())
            }

            siftClass.hook("existingRootFiles", HookStage.BEFORE) { param ->
                param.setResult(emptyList<String>())
            }

            siftClass.hook("existingRootPackages", HookStage.BEFORE) { param ->
                param.setResult(emptyList<String>())
            }

            siftClass.hook("existingDangerousProperties", HookStage.BEFORE) { param ->
                param.setResult(emptyList<String>())
            }

            Logger.s("Sift Science hooks installed", LogSource.MODULE)
        } catch (e: Exception) {
            Logger.e("Failed to hook Sift Science: ${e.message}", LogSource.MODULE)
        }
    }

    /**
     * Hook Activity.finish() to prevent premature exits
     * This is CRITICAL to prevent the app from closing on detection
     */
    private fun hookActivityLifecycle() {
        try {
            Activity::class.java.hook("finish", HookStage.BEFORE) { param ->
                // FIX: Corrected generic cast syntax
                val activity = param.thisObject() as Activity
                val activityName = activity.javaClass.simpleName

                // Get call stack
                val stack = Thread.currentThread().stackTrace

                // Check if called from security/detection code
                val suspiciousCallers = listOf(
                    "Security", "Integrity", "Detection", "Validator",
                    "SafetyNet", "Attestation", "Root", "Sift",
                    "DeviceCheck", "AntiTamper", "Emulator"
                )

                val suspiciousCaller = stack.firstOrNull { frame ->
                    suspiciousCallers.any { suspicious ->
                        frame.className.contains(suspicious, ignoreCase = true) ||
                                frame.methodName.contains(suspicious, ignoreCase = true)
                    }
                }

                if (suspiciousCaller != null) {
                    // FIX: Replaced xposedLog with Logger.i
                    Logger.i("[BLOCKED] finish() on $activityName from: ${suspiciousCaller.className}.${suspiciousCaller.methodName}", LogSource.MODULE)
                    param.setResult(null) // Block the finish() call
                    return@hook
                }

                // Also check if HomeActivity is finishing too quickly (< 1 second)
                if (activityName.contains("Home", ignoreCase = true)) {
                    try {
                        // Try to get activity creation time via reflection
                        val activityClass = activity.javaClass
                        val method = activityClass.getDeclaredMethod("getCreationElapsedRealtime")
                        method.isAccessible = true
                        val creationTime = method.invoke(activity) as? Long

                        if (creationTime != null) {
                            val elapsed = android.os.SystemClock.elapsedRealtime() - creationTime
                            if (elapsed < 1000) { // Less than 1 second
                                // FIX: Replaced xposedLog with Logger.i
                                Logger.i("[BLOCKED] Early finish() on $activityName after ${elapsed}ms", LogSource.MODULE)
                                param.setResult(null)
                                return@hook
                            }
                        }
                    } catch (e: Exception) {
                        // Reflection failed, continue with normal check
                    }
                }
            }

            Logger.s("Activity lifecycle hooks installed", LogSource.MODULE)
        } catch (e: Exception) {
            Logger.e("Failed to hook Activity lifecycle: ${e.message}", LogSource.MODULE)
        }
    }

    /**
     * Hook file system access to hide emulator and root indicators
     */
    private fun hookFileSystemAccess() {
        try {
            File::class.java.hook("exists", HookStage.AFTER) { param ->
                // FIX: Corrected generic cast syntax
                val file = param.thisObject() as File
                val path = file.absolutePath
                val exists = param.getResult() as? Boolean ?: false

                if (!exists) return@hook // File doesn't exist anyway

                // Emulator indicators
                val emulatorPatterns = listOf(
                    "goldfish", "qemu", "genymotion", "vbox", "ttVM",
                    "nox", "bluestacks", "andy", "droid4x", "ueventd",
                    "ranchu", "vboxsf", "memu", "pipe"
                )

                // Root indicators
                val rootPaths = listOf(
                    "/system/app/Superuser.apk",
                    "/sbin/su", "/system/bin/su", "/system/xbin/su",
                    "/data/local/xbin/su", "/data/local/bin/su",
                    "/system/sd/xbin/su", "/system/bin/failsafe/su",
                    "/data/local/su", "/su/bin/su",
                    "/system/app/SuperSU", "/system/xbin/daemonsu",
                    "/dev/com.koushikdutta.superuser.daemon/",
                    "/system/etc/init.d/99SuperSUDaemon"
                )

                // Xposed/Magisk indicators
                val hookIndicators = listOf(
                    "xposed", "lsposed", "lspatch", "edxposed",
                    "magisk", "substrate"
                )

                val shouldHide =
                    emulatorPatterns.any { path.contains(it, ignoreCase = true) } ||
                            rootPaths.any { path.equals(it, ignoreCase = true) } ||
                            hookIndicators.any { path.contains(it, ignoreCase = true) }

                if (shouldHide) {
                    Logger.i("Hidden file: $path", LogSource.MODULE)
                    param.setResult(false)
                }
            }

            // Also hook canRead and canExecute
            listOf("canRead", "canExecute").forEach { methodName ->
                File::class.java.hook(methodName, HookStage.AFTER) { param ->
                    // FIX: Corrected generic cast syntax
                    val file = param.thisObject() as File
                    val path = file.absolutePath

                    if ((path.contains("su", ignoreCase = true) ||
                                path.contains("xposed", ignoreCase = true) ||
                                path.contains("goldfish", ignoreCase = true)) &&
                        param.getResult() == true) {
                        param.setResult(false)
                    }
                }
            }

            Logger.s("File system hooks installed", LogSource.MODULE)
        } catch (e: Exception) {
            Logger.e("Failed to hook file system: ${e.message}", LogSource.MODULE)
        }
    }

    /**
     * Hook Build property access via reflection
     */
    private fun hookBuildProperties() {
        try {
            // Hook getRadioVersion() which returns null on emulators
            try {
                Build::class.java.hook("getRadioVersion", HookStage.AFTER) { param ->
                    val result = param.getResult() as? String
                    if (result.isNullOrBlank() || result == "unknown") {
                        param.setResult("1.0.0.0")
                        Logger.i("Spoofed Build.getRadioVersion()", LogSource.MODULE)
                    }
                }
            } catch (e: Exception) {
                // Method doesn't exist on all Android versions
            }

            // Hook reflection access to Build fields
            Class::class.java.hook("getDeclaredField", HookStage.AFTER) { param ->
                // FIX: Corrected generic cast syntax
                val clazz = param.thisObject() as Class<*>
                val fieldName = param.arg<String>(0)

                if (clazz.name == "android.os.Build") {
                    val suspiciousFields = listOf(
                        "HARDWARE", "PRODUCT", "DEVICE", "BOARD",
                        "BRAND", "MODEL", "FINGERPRINT", "MANUFACTURER"
                    )

                    if (fieldName in suspiciousFields) {
                        Logger.i("Detected Build.$fieldName access via reflection", LogSource.MODULE)
                    }
                }
            }

            Logger.s("Build property hooks installed", LogSource.MODULE)
        } catch (e: Exception) {
            Logger.e("Failed to hook Build properties: ${e.message}", LogSource.MODULE)
        }
    }

    /**
     * Hook stack trace analysis (used to detect Xposed)
     */
    private fun hookStackTraceDetection() {
        try {
            Throwable::class.java.hook("getStackTrace", HookStage.AFTER) { param ->
                val stackTrace = param.getResult() as? Array<StackTraceElement> ?: return@hook

                // Check if app is analyzing stack traces for Xposed
                val filtered = stackTrace.filterNot { element ->
                    element.className.contains("xposed", ignoreCase = true) ||
                            element.className.contains("lsposed", ignoreCase = true) ||
                            element.className.contains("lspatch", ignoreCase = true) ||
                            element.className.contains("edxposed", ignoreCase = true)
                }

                if (filtered.size < stackTrace.size) {
                    Logger.i("Filtered ${stackTrace.size - filtered.size} Xposed frames from stack trace", LogSource.MODULE)
                    param.setResult(filtered.toTypedArray())
                }
            }

            Logger.s("Stack trace hooks installed", LogSource.MODULE)
        } catch (e: Exception) {
            Logger.e("Failed to hook stack traces: ${e.message}", LogSource.MODULE)
        }
    }

    /**
     * Hook debug detection methods
     */
    private fun hookDebugDetection() {
        try {
            // Hook Debug.isDebuggerConnected()
            android.os.Debug::class.java.hook("isDebuggerConnected", HookStage.BEFORE) { param ->
                param.setResult(false)
            }

            // Hook ApplicationInfo flags check
            android.content.pm.ApplicationInfo::class.java.hook("getFlags", HookStage.AFTER) { param ->
                var flags = param.getResult() as? Int ?: return@hook

                // Remove FLAG_DEBUGGABLE (0x02)
                flags = flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE.inv()
                param.setResult(flags)
            }

            Logger.s("Debug detection hooks installed", LogSource.MODULE)
        } catch (e: Exception) {
            Logger.e("Failed to hook debug detection: ${e.message}", LogSource.MODULE)
        }
    }

    /**
     * Hook signature verification (redundant with spoofSignatures but adds extra layer)
     */
    private fun hookSignatureVerification() {
        try {
            android.content.pm.PackageManager::class.java.hook(
                "getPackageInfo",
                HookStage.AFTER,
                { param ->
                    val packageName = param.argNullable<String>(0)
                    val flags = param.argNullable<Int>(1) ?: 0

                    // Check if requesting signature info
                    val requestingSignatures = flags and android.content.pm.PackageManager.GET_SIGNATURES != 0 ||
                            flags and android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES != 0

                    packageName == "com.grindrapp.android" && requestingSignatures
                }
            ) { param ->
                Logger.i("Signature check detected - spoofing handled elsewhere", LogSource.MODULE)
            }

            Logger.s("Signature verification hooks installed", LogSource.MODULE)
        } catch (e: Exception) {
            Logger.e("Failed to hook signature verification: ${e.message}", LogSource.MODULE)
        }
    }

    override fun cleanup() {
        Logger.i("Cleaning up Comprehensive Anti-Detection", LogSource.MODULE)
    }
}