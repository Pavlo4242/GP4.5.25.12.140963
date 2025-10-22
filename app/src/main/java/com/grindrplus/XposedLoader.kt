package com.grindrplus

import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import com.grindrplus.core.Constants.GRINDR_PACKAGE_NAME
import com.grindrplus.hooks.spoofSignatures
import com.grindrplus.hooks.sslUnpinning
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

class XposedLoader : IXposedHookZygoteInit, IXposedHookLoadPackage {
    private lateinit var modulePath: String

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!lpparam.packageName.contains(GRINDR_PACKAGE_NAME)) return

        // CRITICAL: Install all anti-detection hooks FIRST, before anything else
        installCriticalHooks(lpparam)

        // Then do signature spoofing and SSL unpinning
        spoofSignatures(lpparam)
        sslUnpinning(lpparam)

        // Finally, initialize the rest of the app
        Application::class.java.hook("attach", HookStage.AFTER) { param ->
            val application = param.thisObject()
            GrindrPlus.init(
                modulePath,
                application,
                BuildConfig.TARGET_GRINDR_VERSION_CODES,
                BuildConfig.TARGET_GRINDR_VERSION_NAMES
            )
        }
    }

    /**
     * Install critical anti-detection hooks IMMEDIATELY at at package load time
     * This runs BEFORE Application.onCreate() and BEFORE any app code
     */
    private fun installCriticalHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("[GrindrPlus] Installing critical anti-detection hooks...")

        // 1. PREVENT PREMATURE ACTIVITY EXITS
        hookActivityFinish(lpparam)

        // 2. FILE SYSTEM DETECTION
        hookFileSystem(lpparam)

        // 3. BUILD PROPERTIES
        hookBuildProperties(lpparam)

        // 4. SYSTEM PROPERTIES
        hookSystemProperties(lpparam)

        // 5. PACKAGE MANAGER (ROOT/XPOSED DETECTION)
        hookPackageManager(lpparam)

        // 6. RUNTIME LIBRARY LOADING
        hookRuntimeLibraries(lpparam)

        // 7. NATIVE DETECTION (Best effort)
        hookNativeDetection(lpparam)

        // 8. PLAY INTEGRITY / SAFETYNET
        hookPlayIntegrity(lpparam)

        XposedBridge.log("[GrindrPlus] Critical hooks installed successfully")
    }

    private fun hookActivityFinish(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            Activity::class.java,
            "finish",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    val activity = param.thisObject as Activity
                    val activityName = activity.javaClass.simpleName

                    // Get the call stack to see who called finish()
                    val stack = Thread.currentThread().stackTrace
                    val caller = stack.drop(2).take(5).joinToString("\n") {
                        "  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})"
                    }

                    // Check if finish() was called from security/detection code
                    val suspiciousCallers = listOf(
                        "Security", "Integrity", "Detection", "Validator",
                        "SafetyNet", "Attestation", "RootBeer", "Sift",
                        "DeviceCheck", "AntiTamper"
                    )

                    val isSuspicious = stack.any { frame ->
                        suspiciousCallers.any { suspicious ->
                            frame.className.contains(suspicious, ignoreCase = true)
                        }
                    }

                    if (isSuspicious) {
                        XposedBridge.log("[GrindrPlus] BLOCKED finish() on $activityName")
                        XposedBridge.log("[GrindrPlus] Call stack:\n$caller")
                        param.setResult(null) // Don't execute finish()
                    }

                    // Also block if activity just launched (< 500ms ago)
                    // This catches the "immediate exit" pattern we saw in logs
                    try {
                        val startTime = activity.javaClass
                            .getDeclaredField("mStartTime")
                            .apply { isAccessible = true }
                            .getLong(activity)

                        val elapsed = System.currentTimeMillis() - startTime
                        if (elapsed < 500) {
                            XposedBridge.log("[GrindrPlus] BLOCKED early finish() on $activityName (${elapsed}ms)")
                            param.setResult(null)
                        }
                    } catch (e: Exception) {
                        // Field doesn't exist, continue normally
                    }
                }
            }
        )
    }

    private fun hookFileSystem(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook File.exists() to hide emulator and root files
        XposedHelpers.findAndHookMethod(
            File::class.java,
            "exists",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam<*>) {
                    val file = param.thisObject as File
                    val path = file.absolutePath

                    // Emulator detection files
                    val emulatorPatterns = listOf(
                        "goldfish", "qemu", "genymotion", "vbox", "ttVM",
                        "nox", "bluestacks", "andy", "droid4x", "ueventd",
                        "ranchu", "vboxsf", "memu", "pipe"
                    )

                    // Root detection files
                    val rootPaths = listOf(
                        "/system/app/Superuser.apk",
                        "/sbin/su", "/system/bin/su", "/system/xbin/su",
                        "/data/local/xbin/su", "/data/local/bin/su",
                        "/system/sd/xbin/su", "/system/bin/failsafe/su",
                        "/data/local/su", "/su/bin/su",
                        "/system/app/SuperSU", "/system/xbin/daemonsu",
                        "/system/etc/init.d/99SuperSUDaemon",
                        "/dev/com.koushikdutta.superuser.daemon/",
                        "/system/app/SuperSU.apk"
                    )

                    // Xposed/LSPatch detection
                    val xposedPaths = listOf(
                        "/data/data/de.robv.android.xposed.installer",
                        "/data/data/org.meowcat.edxposed.manager",
                        "/data/data/com.solohsu.android.edxp.manager",
                        "xposed", "lsposed", "lspatch"
                    )

                    val shouldHide = emulatorPatterns.any { path.contains(it, ignoreCase = true) } ||
                            rootPaths.any { path == it } ||
                            xposedPaths.any { path.contains(it, ignoreCase = true) }

                    if (shouldHide && param.getResult() as Boolean) {
                        XposedBridge.log("[GrindrPlus] Hidden file: $path")
                        param.setResult(false)
                    }
                }
            }
        )

        // Hook File.canRead() and File.canExecute() for the same paths
        listOf("canRead", "canExecute").forEach { methodName ->
            XposedHelpers.findAndHookMethod(
                File::class.java,
                methodName,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val file = param.thisObject as File
                        val path = file.absolutePath

                        if ((path.contains("su") || path.contains("xposed") ||
                                    path.contains("goldfish")) && param.getResult() as Boolean) {
                            param.setResult(false)
                        }
                    }
                }
            )
        }
    }

    private fun hookBuildProperties(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook getRadioVersion which returns null on emulators
        try {
            XposedHelpers.findAndHookMethod(
                Build::class.java,
                "getRadioVersion",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val result = param.getResult() as? String
                        if (result.isNullOrBlank() || result == "unknown") {
                            param.setResult("1.0.0.0")
                            XposedBridge.log("[GrindrPlus] Spoofed Build.getRadioVersion()")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            // Method doesn't exist on all Android versions
        }
    }

    private fun hookSystemProperties(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook SystemProperties.get() to hide emulator properties
        try {
            val systemPropertiesClass = XposedHelpers.findClass(
                "android.os.SystemProperties",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(
                systemPropertiesClass,
                "get",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val key = param.args[0] as String
                        val value = param.getResult() as? String ?: ""

                        // Emulator property keys
                        val emulatorProps = listOf(
                            "ro.kernel.qemu", "ro.kernel.android.qemu",
                            "ro.hardware", "ro.product.device",
                            "ro.build.product", "ro.product.board",
                            "ro.board.platform", "ro.build.fingerprint"
                        )

                        if (emulatorProps.any { key.contains(it) }) {
                            // Return real device values
                            when {
                                key.contains("qemu") -> param.setResult("0")
                                key.contains("hardware") -> param.setResult("qcom")
                                key.contains("device") || key.contains("product") ->
                                    param.setResult("OnePlus7Pro")
                                key.contains("fingerprint") ->
                                    param.setResult("OnePlus/OnePlus7Pro/OnePlus7Pro:11/RKQ1.201022.002/2103312214:user/release-keys")
                            }
                            XposedBridge.log("[GrindrPlus] Spoofed system property: $key = ${param.getResult()}")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("[GrindrPlus] Failed to hook SystemProperties: ${e.message}")
        }
    }

    private fun hookPackageManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook PackageManager to hide Xposed/root apps
        XposedHelpers.findAndHookMethod(
            PackageManager::class.java,
            "getInstalledApplications",
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam<*>) {
                    @Suppress("UNCHECKED_CAST")
                    val apps = param.getResult() as? List<*> ?: return

                    val suspiciousPackages = listOf(
                        "de.robv.android.xposed",
                        "org.meowcat.edxposed",
                        "com.solohsu.android.edxp",
                        "io.github.lsposed",
                        "com.topjohnwu.magisk",
                        "eu.chainfire.supersu",
                        "com.noshufou.android.su",
                        "com.koushikdutta.superuser",
                        "com.thirdparty.superuser",
                        "com.yellowes.su"
                    )

                    val filtered = apps.filterNot { app ->
                        try {
                            val packageName = app?.javaClass
                                ?.getField("packageName")
                                ?.get(app) as? String ?: ""
                            suspiciousPackages.any { packageName.contains(it) }
                        } catch (e: Exception) {
                            false
                        }
                    }

                    if (filtered.size < apps.size) {
                        XposedBridge.log("[GrindrPlus] Hidden ${apps.size - filtered.size} suspicious packages")
                        param.setResult(filtered)
                    }
                }
            }
        )
    }

    private fun hookRuntimeLibraries(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook Runtime.loadLibrary() to prevent detection of Xposed libraries
        XposedHelpers.findAndHookMethod(
            Runtime::class.java,
            "loadLibrary",
            String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    val libName = param.args[0] as String

                    val suspiciousLibs = listOf(
                        "xposed", "lspatch", "substrate", "edxp"
                    )

                    if (suspiciousLibs.any { libName.contains(it, ignoreCase = true) }) {
                        XposedBridge.log("[GrindrPlus] Blocked library load: $libName")
                        // Throw an exception as if the library doesn't exist
                        param.setThrowable(UnsatisfiedLinkError("Library not found: $libName"))
                    }
                }
            }
        )

        // Also hook System.loadLibrary
        XposedHelpers.findAndHookMethod(
            System::class.java,
            "loadLibrary",
            String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    val libName = param.args[0] as String

                    if (listOf("xposed", "lspatch", "substrate", "edxp")
                            .any { libName.contains(it, ignoreCase = true) }) {
                        param.setThrowable(UnsatisfiedLinkError("Library not found: $libName"))
                    }
                }
            }
        )
    }

    private fun hookNativeDetection(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Native detection is hard to hook from Java
        // Best we can do is hook the common detection methods

        // Hook /proc/self/maps reading (used to detect Xposed native libraries)
        try {
            XposedHelpers.findAndHookMethod(
                "java.io.BufferedReader",
                lpparam.classLoader,
                "readLine",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam<*>) {
                        val line = param.getResult() as? String ?: return

                        // If the line contains suspicious library paths, skip it
                        if (line.contains("xposed", ignoreCase = true) ||
                            line.contains("lspatch", ignoreCase = true) ||
                            line.contains("substrate", ignoreCase = true)) {
                            // Return next line instead
                            try {
                                val reader = param.thisObject
                                param.setResult(reader::class.java
                                    .getMethod("readLine")
                                    .invoke(reader))
                            } catch (e: Exception) {
                                param.setResult(null)
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            // Continue if hook fails
        }
    }

    private fun hookPlayIntegrity(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook SafetyNet attestation
        try {
            XposedHelpers.findAndHookMethod(
                "com.google.android.gms.safetynet.SafetyNet",
                lpparam.classLoader,
                "attest",
                ByteArray::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        XposedBridge.log("[GrindrPlus] Blocked SafetyNet attestation")
                        // Return null to prevent attestation
                        param.setResult(null)
                    }
                }
            )
        } catch (e: Exception) {
            // SafetyNet might not be directly called
        }

        // Hook Play Integrity API (newer than SafetyNet)
        try {
            val integrityManagerClass = XposedHelpers.findClassIfExists(
                "com.google.android.play.core.integrity.IntegrityManager",
                lpparam.classLoader
            )

            if (integrityManagerClass != null) {
                XposedHelpers.findAndHookMethod(
                    integrityManagerClass,
                    "requestIntegrityToken",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam<*>) {
                            XposedBridge.log("[GrindrPlus] Blocked Play Integrity check")
                            param.setResult(null)
                        }
                    }
                )
            }
        } catch (e: Exception) {
            // Play Integrity might not be used
        }
    }
}