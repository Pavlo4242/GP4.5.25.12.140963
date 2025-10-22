package com.grindrplus.utils

import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.core.Logger
import com.grindrplus.hooks.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

class HookManager {
    private var hooks = mutableMapOf<KClass<out Hook>, Hook>()

    fun registerHooks(init: Boolean = true) {
        runBlocking(Dispatchers.IO) {
            // CRITICAL: Anti-detection hooks MUST be registered first
            // They need to be active before other hooks that might trigger detection
            val hookList = listOf(
                // PRIORITY 1: Anti-detection (MUST BE FIRST)
                ComprehensiveAntiDetection(),

                // PRIORITY 2: Core security bypasses
                AntiDetection(), // Keep old one for compatibility

                // PRIORITY 3: Diagnostic (only enable for debugging)
                // DetectionDiagnostics(), // Uncomment to debug detection

                // PRIORITY 4: Network and communication
                WebSocketAlive(),
                TimberLogging(),

                // PRIORITY 5: Account and feature management
                BanManagement(),
                FeatureGranting(),
                EnableUnlimited(),

                // PRIORITY 6: UI and behavior modifications
                StatusDialog(),
                AntiBlock(),
                NotificationAlerts(),
                DisableUpdates(),
                DisableBoosting(),
                DisableShuffle(),
                AllowScreenshots(),

                // PRIORITY 7: Chat features
                ChatIndicators(),
                ChatTerminal(),

                // PRIORITY 8: Privacy and tracking
                DisableAnalytics(),

                // PRIORITY 9: Content features
                ExpiringMedia(),
                Favorites(),
                LocalSavedPhrases(),
                LocationSpoofer(),
                OnlineIndicator(),
                UnlimitedProfiles(),
                ProfileDetails(),
                ProfileViews(),
                QuickBlock(),
                EmptyCalls(),
                UnlimitedAlbums()
            )

            // Initialize hook settings in config
            hookList.forEach { hook ->
                Config.initHookSettings(
                    hook.hookName,
                    hook.hookDesc,
                    // Anti-detection hooks should be enabled by default
                    // FIX: Changed parameter name from `defaultEnabled` to `state`
                    state = hook.hookName.contains("Anti", ignoreCase = true) ||
                            hook.hookName.contains("Detection", ignoreCase = true)
                )
            }

            if (!init) return@runBlocking

            hooks = hookList.associateBy { it::class }.toMutableMap()

            // Initialize hooks in order (critical anti-detection first)
            hooks.values.forEachIndexed { index, hook ->
                if (Config.isHookEnabled(hook.hookName)) {
                    try {
                        hook.init()
                        Logger.s("[$index] Initialized hook: ${hook.hookName}")
                    } catch (e: Exception) {
                        Logger.e("[$index] Failed to initialize ${hook.hookName}: ${e.message}")
                        Logger.writeRaw(e.stackTraceToString())
                    }
                } else {
                    Logger.i("[$index] Hook ${hook.hookName} is disabled.")
                }
            }

            Logger.s("All hooks registered and initialized")
        }
    }

    fun reloadHooks() {
        runBlocking(Dispatchers.IO) {
            Logger.i("Reloading hooks...")

            // Cleanup existing hooks
            hooks.values.forEach { hook ->
                try {
                    hook.cleanup()
                } catch (e: Exception) {
                    Logger.e("Failed to cleanup ${hook.hookName}: ${e.message}")
                }
            }
            hooks.clear()

            // Re-register
            registerHooks()
            Logger.s("Hooks reloaded successfully")
        }
    }

    fun init() {
        Logger.i("Initializing HookManager...")
        registerHooks()
    }

    /**
     * Get a specific hook instance
     */
    fun <T : Hook> getHook(hookClass: KClass<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return hooks[hookClass] as? T
    }

    /**
     * Toggle a specific hook on/off
     */
    fun toggleHook(hookName: String, enabled: Boolean) {
        val hook = hooks.values.find { it.hookName == hookName } ?: return

        Config.setHookEnabled(hookName, enabled)

        if (enabled) {
            try {
                hook.init()
                Logger.s("Enabled hook: $hookName")
            } catch (e: Exception) {
                Logger.e("Failed to enable $hookName: ${e.message}")
            }
        } else {
            try {
                hook.cleanup()
                Logger.i("Disabled hook: $hookName")
            } catch (e: Exception) {
                Logger.e("Failed to disable $hookName: ${e.message}")
            }
        }
    }

    /**
     * Get list of all registered hooks
     */
    fun getAllHooks(): List<Hook> = hooks.values.toList()

    /**
     * Check if a hook is currently active
     */
    fun isHookActive(hookName: String): Boolean {
        return hooks.values.any { it.hookName == hookName } &&
                Config.isHookEnabled(hookName)
    }
}