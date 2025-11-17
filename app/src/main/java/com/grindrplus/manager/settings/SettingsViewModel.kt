package com.grindrplus.manager.settings

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grindrplus.core.Config
import com.grindrplus.manager.DATA_URL
import com.grindrplus.manager.settings.SettingsUtils.testMapsApiKey
import com.grindrplus.manager.utils.AppIconManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
class SettingsViewModel(
    private val context: Context,
) : ViewModel() {
    private val hookHideList = setOf(
        "Status Dialog",
    )

    private val _hookCategories = MutableStateFlow<List<HookCategory>>(emptyList())
    val hookCategories: StateFlow<List<HookCategory>> = _hookCategories
    private val _settingGroups = MutableStateFlow<List<SettingGroup>>(emptyList())
    val settingGroups: StateFlow<List<SettingGroup>> = _settingGroups

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _showApiKeyTestDialog = MutableStateFlow(false)
    val showApiKeyTestDialog: StateFlow<Boolean> = _showApiKeyTestDialog

    private val _apiKeyTestTitle = MutableStateFlow("")
    val apiKeyTestTitle: StateFlow<String> = _apiKeyTestTitle

    private val _apiKeyTestMessage = MutableStateFlow("")
    val apiKeyTestMessage: StateFlow<String> = _apiKeyTestMessage

    private val _apiKeyTestRawResponse = MutableStateFlow("")
    val apiKeyTestRawResponse: StateFlow<String> = _apiKeyTestRawResponse

    private val _apiKeyTestLoading = MutableStateFlow(false)
    val apiKeyTestLoading: StateFlow<Boolean> = _apiKeyTestLoading

    fun dismissApiKeyTestDialog() {
        _showApiKeyTestDialog.value = false
    }

    private fun showApiKeyTestDialog(
        isLoading: Boolean,
        title: String,
        message: String,
        rawResponse: String
    ) {
        _apiKeyTestLoading.value = isLoading
        _apiKeyTestTitle.value = title
        _apiKeyTestMessage.value = message
        _apiKeyTestRawResponse.value = rawResponse
        _showApiKeyTestDialog.value = true
    }

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
// REMOVE or comment out the block that creates hookSettings:

val hooks = Config.getHooksSettings() // Keep this line if needed elsewhere
val hookSettings = hooks
    .filterNot { (hookName, _) -> hookName in hookHideList }
    .map { (hookName, pair) ->
        SwitchSetting(
            id = hookName,
            title = hookName,
            description = pair.first,
            isChecked = pair.second,
            onCheckedChange = {
                viewModelScope.launch {
                    Config.setHookEnabled(hookName, it)
                    loadSettings() // Might want to call loadHookCategories() instead/as well if hooks are modified here
                }
            }
        )
    }

                val tasks = Config.getTasksSettings()
                val taskSettings = tasks.map { (taskId, pair) ->
                    SwitchSetting(
                        id = taskId,
                        title = taskId,
                        description = pair.first,
                        isChecked = pair.second,
                        onCheckedChange = {
                            viewModelScope.launch {
                                Config.setTaskEnabled(taskId, it)
                                loadSettings() // Reload all settings after task change
                            }
                        }
                    )
                }

    val otherSettings = mutableListOf(
        TextSetting(
            id = "command_prefix",
            title = "Command Prefix",
            description = "Change the command prefix (default: /)",
            value = Config.get("command_prefix", "/") as String,
            onValueChange = {
                viewModelScope.launch {
                    Config.put("command_prefix", it)
                    loadSettings()
                }
            },
            validator = { input ->
                when {
                    input.isBlank() -> "Invalid command prefix"
                    input.length > 1 -> "Command prefix must be a single character"
                    !input.matches(Regex("[^a-zA-Z0-9]")) -> "Command prefix must be a special character"
                    else -> null
                }
            }
        ),
        TextSetting(
            id = "date_format",
            title = "Date Format",
            description = "Format for displaying dates in the app (default: MM/dd/yyyy)",
            value = Config.get("date_format", "MM/dd/yyyy") as String,
            onValueChange = {
                viewModelScope.launch {
                    Config.put("date_format", it)
                    loadSettings()
                }
            },
            validator = { input ->
                when {
                    input.isBlank() -> "Date format cannot be empty"
                    !input.contains("MM") && !input.contains("M") -> "Format must include month (M or MM)"
                    !input.contains("dd") && !input.contains("d") -> "Format must include day (d or dd)"
                    !input.contains("yyyy") && !input.contains("yy") -> "Format must include year (yy or yyyy)"
                    else -> null
                }
            }
        ),
        TextSetting(
            id = "online_indicator",
            title = "Online indicator duration (mins)",
            description = "Control when the green dot disappears after inactivity",
            value = (Config.get("online_indicator", 5) as Number).toString(),
            onValueChange = {
                val value = it.toIntOrNull() ?: 5
                viewModelScope.launch {
                    Config.put("online_indicator", value)
                    loadSettings()
                }
            },
            keyboardType = KeyboardType.Number,
            validator = { input ->
                val value = input.toIntOrNull()
                if (value == null || value <= 0) "Duration must be a positive number" else null
            }
        ),
        SwitchSetting(
            id = "show_bmi_in_profile",
            title = "Show BMI in Profile",
            description = "Display BMI in the profile section",
            isChecked = Config.get("show_bmi_in_profile", true) as Boolean,
            onCheckedChange = {
                viewModelScope.launch {
                    Config.put("show_bmi_in_profile", it)
                    loadSettings()
                }
            }
        ),
        TextSetting(
            id = "favorites_grid_columns",
            title = "Favorites grid columns",
            description = "Number of columns in the favorites grid (default: 3)",
            value = Config.get("favorites_grid_columns", 3).toString(),
            onValueChange = {
                val value = it.toIntOrNull() ?: 3
                viewModelScope.launch {
                    Config.put("favorites_grid_columns", value)
                    loadSettings()
                }
            },
            keyboardType = KeyboardType.Number,
            validator = { input ->
                val value = input.toIntOrNull()
                if (value == null || value <= 0) "Number of columns must be a positive number" else null
            }
        ),
        TextSettingWithButtons(
            id = "android_device_id",
            title = "Android Device ID",
            description = "Change the Android Device ID",
            value = Config.get("android_device_id", "") as String,
            onValueChange = {
                viewModelScope.launch {
                    Config.put("android_device_id", it)
                    loadSettings()
                }
            },
            validator = { input ->
                when {
                    input.isBlank() -> null
                    input.length != 16 -> "Android Device ID must be 16 characters long"
                    !input.matches(Regex("[0-9a-fA-F]+")) -> "Android Device ID must be a hexadecimal string"
                    else -> null
                }
            },
            buttons = listOf(
                ButtonAction("Generate") {
                    val uuid = java.util.UUID.randomUUID()
                    val newDeviceId = uuid.toString().replace("-", "").substring(0, 16)
                    Config.put("android_device_id", newDeviceId)
                    loadSettings()
                    Toast.makeText(
                        context,
                        "New device ID generated",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

        ),
        SwitchSetting(
            id = "enable_cookie_tap",
            title = "Enable Cookie Tap",
            description = "Enable the ability to send cookie taps to other users (they'll see them)",
            isChecked = Config.get("enable_cookie_tap", false) as Boolean,
            onCheckedChange = {
                viewModelScope.launch {
                    Config.put("enable_cookie_tap", it)
                    loadSettings()
                }
            }
        ),
        SwitchSetting(
            id = "enable_vip_flag",
            title = "Enable Star Section",
            description = "Enables what looks like a recommendation section next to Browse",
            isChecked = Config.get("enable_vip_flag", false) as Boolean,
            onCheckedChange = {
                viewModelScope.launch {
                    Config.put("enable_vip_flag", it)
                    loadSettings()
                }
            }
        ),
        SwitchSetting(
            id = "enable_albums_spank_bank",
            title = "Enable Albums Spank Bank",
            description = "Enable the new Albums section",
            isChecked = Config.get("enable_albums_spank_bank", false) as Boolean,
            onCheckedChange = {
                viewModelScope.launch {
                    Config.put("enable_albums_spank_bank", it)
                    loadSettings()
                }
            }
        ),
        SwitchSetting(
            id = "enable_interest_section",
            title = "Enable Interest Section",
            description = "Show interests section on profiles",
            isChecked = Config.get("enable_interest_section", true) as Boolean,
            onCheckedChange = {
                viewModelScope.launch {
                    Config.put("enable_interest_section", it)
                    loadSettings()
                }
            }
        ),
        SwitchSetting(
            id = "disable_profile_swipe",
            title = "Disable profile swipe",
            description = "Disable profile swipe and open profile on click",
            isChecked = Config.get("disable_profile_swipe", false) as Boolean,
            onCheckedChange = {
                viewModelScope.launch {
                    Config.put("disable_profile_swipe", it)
                    loadSettings()
                }
            }
        ),
        SwitchSetting(
            id = "force_old_anti_block_behavior",
            title = "Force old AntiBlock behavior",
            description = "Use the old AntiBlock behavior (don't use this, required for testing)",
            isChecked = Config.get("force_old_anti_block_behavior", false) as Boolean,
            onCheckedChange = {
                viewModelScope.launch {
                    Config.put("force_old_anti_block_behavior", it)
                    loadSettings()
                }
            }
        ),
        SwitchSetting(
            id = "anti_block_use_toasts",
            title = "Use toasts for AntiBlock hook",
            description = "Instead of receiving Android notifications, use toasts for block/unblock notifications",
            isChecked = Config.get("anti_block_use_toasts", false) as Boolean,
            onCheckedChange = {
                viewModelScope.launch {
                    Config.put("anti_block_use_toasts", it)
                    loadSettings()
                }
            }
        ),
        SwitchSetting(
            id = "reset_database",
            title = "Reset local database on next start",
            description = "Will delete all local data on next app start",
            isChecked = Config.get("reset_database", false) as Boolean,
            onCheckedChange = {
                viewModelScope.launch {
                    Config.put("reset_database", it)
                    loadSettings()
                }
            }
        ),
        SwitchSetting(
            id = "do_gui_safety_checks",
            title = "Do GUI safety checks",
            description = "Prevent graphic glitches when applying GUI based hooks",
            isChecked = Config.get("do_gui_safety_checks", true) as Boolean,
            onCheckedChange = {
                viewModelScope.launch {
                    Config.put("do_gui_safety_checks", it)
                    loadSettings()
                }
            }
        )
    )

    val managerSettings = mutableListOf<Setting>(
        TextSettingWithButtons(
            id = "maps_api_key",
            title = "Maps API Key",
            description = "Use a custom Maps API Key when using Grindr Plus with LSPatch", //AIzaSyB // before B
            value = Config.get("maps_api_key", "BiSsM5tTzGxaXTBYMkLoVn1") as String, // Prepopulated Key // aft 1 revrbOtVEY // 
            onValueChange = {
                viewModelScope.launch {
                    Config.put("maps_api_key", it)
                    loadSettings()
                }
            },
            validator = { null },
            buttons = listOf(
                ButtonAction("Test") {
                    val apiKey = Config.get("maps_api_key", "") as String
                    if (apiKey.isBlank()) {
                        Toast.makeText(
                            context,
                            "Please enter an API key first",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        testMapsApiKey(
                            context,
                            viewModelScope,
                            apiKey,
                            ::showApiKeyTestDialog
                        )
                    }
                }
            )
        ),
        TextSetting(
            id = "custom_manifest",
            title = "Custom Manifest URL",
            description = "Use a custom manifest URL when using Grindr Plus with LSPatch",
            value = Config.get("custom_manifest", DATA_URL) as String,
            onValueChange = {
                viewModelScope.launch {
                    Config.put("custom_manifest", it)
                    loadSettings()
                }
            },
            validator = { null }
        ),
        SwitchSetting(
            id = "analytics",
            title = "Opt-in analytics",
            description = "Help improve the app by sending anonymous usage data",
            isChecked = Config.get("analytics", true) as Boolean,
            onCheckedChange = {
                viewModelScope.launch {
                    Config.put("analytics", it)
                    loadSettings()
                }
            }
        ),
        SwitchSetting(
            id = "discreet_icon",
            title = "Camouflage app",
            description = "Hide the app icon and use a different name",
            isChecked = Config.get("discreet_icon", false) as Boolean,
            onCheckedChange = {
                viewModelScope.launch {
                    Config.put("discreet_icon", it)
                    loadSettings()

                    val appIconManager = AppIconManager(context)
                    appIconManager.changeAppIcon(if (it) AppIconManager.DISCREET_ICON else AppIconManager.DEFAULT_ICON)

                    Toast.makeText(
                        context,
                        "App icon changed. It may take a moment to update.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        ),
        SwitchSetting(
            id = "disable_permission_checks",
            title = "Disable permission checks",
            description = "Disable permission checks on startup (not recommended)",
            isChecked = Config.get("disable_permission_checks", false) as Boolean,
            onCheckedChange = {
                viewModelScope.launch {
                    Config.put("disable_permission_checks", it)
                    loadSettings()
                }
            }
        )
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        managerSettings += SwitchSetting(
            id = "material_you",
            title = "Enable dynamic colors",
            description = "Use Material You colors for the app\nRestart the app to apply changes",
            isChecked = Config.get("material_you", false) as Boolean,
            onCheckedChange = {
                viewModelScope.launch {
                    Config.put("material_you", it)
                    loadSettings()
                }
            }
        )
    }
                _settingGroups.value = listOfNotNull(
                    // REMOVE the group for hooks if it exists, or ensure it uses an empty list
                    /*
                    SettingGroup(
                        id = "hooks",
                        title = "Manage Hooks", // Or keep the title but pass emptyList()
                        settings = hookSettings // Pass emptyList() or remove this group entirely
                    ),
                     */
                    // Only include groups that are NOT hooks
                    SettingGroup(
                        id = "tasks",
                        title = "Manage Tasks",
                        settings = taskSettings
                    ), // Example: Update existing group if needed
                    SettingGroup(
                        id = "other",
                        title = "Other Settings",
                        settings = otherSettings
                    ),

                    SettingGroup(
                        id = "manager",
                        title = "Manager Settings",
                        settings = managerSettings
                    ),
                    // Or if creating groups directly:
                    // SettingGroup(id = "tasks", title = "Manage Tasks", settings = taskSettings),
                    // SettingGroup(id = "other", title = "Other Settings", settings = otherSettings),
                    // SettingGroup(id = "manager", title = "Manager Settings", settings = managerSettings)
                ).filter { it.settings.isNotEmpty() } // Ensure empty groups aren't shown

            } finally {
                _isLoading.value = false
            }
        }
    }

// IMPORTANT: Ensure loadHookCategories() is called when navigating to the HooksSettingsScreen
// Usually done in the NavHost graph or within HooksSettingsScreen's LaunchedEffect.
/*    _settingGroups.value = listOf(
        SettingGroup(
            id = "hooks",
            title = "Manage Hooks",
            settings = hookSettings
        ),
        SettingGroup(
            id = "tasks",
            title = "Manage Tasks",
            settings = taskSettings
        ),
        SettingGroup(
            id = "other",
            title = "Other Settings",
            settings = otherSettings
        ),
        SettingGroup(
            id = "manager",
            title = "Manager Settings",
            settings = managerSettings
        ),
    )
} finally {
    _isLoading.value = false
}
}
}*/


// Add this new function to load hook categories:
fun loadHookCategories() {
viewModelScope.launch {
_isLoading.value = true

try {
    val hooks = Config.getHooksSettings()

    // Define categories
    _hookCategories.value = listOf(
        HookCategory(
            id = "anti_detection",
            name = "🛡️ Anti-Detection & Security",
            description = "Critical hooks for hiding root/Xposed and preventing detection",
            hooks = listOf(
                "Comprehensive Anti Detection",
                "Anti Detection",
                "Detection Diagnostics"
            ).mapNotNull { hookName ->
                hooks[hookName]?.let { (desc, enabled) ->
                    HookSettingData(
                        name = hookName,
                        description = desc,
                        isEnabled = enabled,
                        subHooks = getSubHooksFor(hookName)
                    )
                }
            }
        ),

        HookCategory(
            id = "network",
            name = "🌐 Network & Communication",
            description = "WebSocket, logging, and network-related hooks",
            hooks = listOf(
                "Background WebSocket Keep Alive",
                "Timber Logging",
                "Chat indicators"
            ).mapNotNull { hookName ->
                hooks[hookName]?.let { (desc, enabled) ->
                    HookSettingData(
                        name = hookName,
                        description = desc,
                        isEnabled = enabled,
                        subHooks = getSubHooksFor(hookName)
                    )
                }
            }
        ),

        HookCategory(
            id = "features",
            name = "✨ Premium Features",
            description = "Enable Unlimited and premium features",
            hooks = listOf(
                "Enable unlimited",
                "Feature granting",
                "Unlimited profiles",
                "Unlimited albums",
                "Video calls"
            ).mapNotNull { hookName ->
                hooks[hookName]?.let { (desc, enabled) ->
                    HookSettingData(
                        name = hookName,
                        description = desc,
                        isEnabled = enabled,
                        subHooks = getSubHooksFor(hookName)
                    )
                }
            }
        ),

        HookCategory(
            id = "privacy",
            name = "🔒 Privacy & Tracking",
            description = "Disable analytics and profile tracking",
            hooks = listOf(
                "Profile views",
                "Disable analytics",
                "Expiring media",
                "Local saved phrases"
            ).mapNotNull { hookName ->
                hooks[hookName]?.let { (desc, enabled) ->
                    HookSettingData(
                        name = hookName,
                        description = desc,
                        isEnabled = enabled,
                        subHooks = getSubHooksFor(hookName)
                    )
                }
            }
        ),

        HookCategory(
            id = "ui_behavior",
            name = "🎨 UI & Behavior",
            description = "Customize app appearance and behavior",
            hooks = listOf(
                "Status Dialog",
                "Disable boosting",
                "Disable shuffle",
                "Allow screenshots",
                "Notification Alerts",
                "Favorites",
                "Profile details",
                "Online indicator",
                "Reverse radar tabs"
            ).mapNotNull { hookName ->
                hooks[hookName]?.let { (desc, enabled) ->
                    HookSettingData(
                        name = hookName,
                        description = desc,
                        isEnabled = enabled,
                        subHooks = getSubHooksFor(hookName)
                    )
                }
            }
        ),

        HookCategory(
            id = "location",
            name = "📍 Location & Teleport",
            description = "Spoof and customize your location",
            hooks = listOf(
                "Location spoofer"
            ).mapNotNull { hookName ->
                hooks[hookName]?.let { (desc, enabled) ->
                    HookSettingData(
                        name = hookName,
                        description = desc,
                        isEnabled = enabled,
                        subHooks = getSubHooksFor(hookName)
                    )
                }
            }
        ),

        HookCategory(
            id = "chat",
            name = "💬 Chat Features",
            description = "Chat enhancements and terminal",
            hooks = listOf(
                "Chat terminal",
                "Anti Block",
                "Quick block"
            ).mapNotNull { hookName ->
                hooks[hookName]?.let { (desc, enabled) ->
                    HookSettingData(
                        name = hookName,
                        description = desc,
                        isEnabled = enabled,
                        subHooks = getSubHooksFor(hookName)
                    )
                }
            }
        ),

        HookCategory(
            id = "updates",
            name = "🔄 Updates & Bans",
            description = "Manage app updates and ban detection",
            hooks = listOf(
                "Disable updates",
                "Ban management"
            ).mapNotNull { hookName ->
                hooks[hookName]?.let { (desc, enabled) ->
                    HookSettingData(
                        name = hookName,
                        description = desc,
                        isEnabled = enabled,
                        subHooks = getSubHooksFor(hookName)
                    )
                }
            }
        )
    ).filter { it.hooks.isNotEmpty() }

} finally {
    _isLoading.value = false
}
}
}

// Add function to get sub-hooks for specific hooks
private fun getSubHooksFor(hookName: String): List<SubHookData> {
return when (hookName) {
"Comprehensive Anti Detection" -> listOf(
    SubHookData(
        name = "Activity Finish Protection",
        description = "Prevent premature activity exits from detection",
        isEnabled = Config.get(
            "sub_hook_comprehensive_activity_finish",
            true
        ) as Boolean
    ),
    SubHookData(
        name = "File System Hiding",
        description = "Hide root/emulator files from detection",
        isEnabled = Config.get("sub_hook_comprehensive_filesystem", true) as Boolean
    ),
    SubHookData(
        name = "Build Properties Spoofing",
        description = "Spoof device build properties",
        isEnabled = Config.get("sub_hook_comprehensive_build_props", true) as Boolean
    ),
    SubHookData(
        name = "Package Manager Hiding",
        description = "Hide Xposed/root packages",
        isEnabled = Config.get("sub_hook_comprehensive_pkg_mgr", true) as Boolean
    ),
    SubHookData(
        name = "Native Library Blocking",
        description = "Block loading of detection libraries",
        isEnabled = Config.get("sub_hook_comprehensive_native_libs", true) as Boolean
    ),
    SubHookData(
        name = "Play Integrity Blocking",
        description = "Block SafetyNet/Play Integrity checks",
        isEnabled = Config.get("sub_hook_comprehensive_play_integrity", true) as Boolean
    )
)

"Anti Detection" -> listOf(
    SubHookData(
        name = "Root Detection",
        description = "Hide root from basic checks",
        isEnabled = Config.get("sub_hook_anti_detection_root", true) as Boolean
    ),
    SubHookData(
        name = "Emulator Detection",
        description = "Hide emulator from basic checks",
        isEnabled = Config.get("sub_hook_anti_detection_emulator", true) as Boolean
    ),
    SubHookData(
        name = "Sift Science",
        description = "Block Sift Science fingerprinting",
        isEnabled = Config.get("sub_hook_anti_detection_sift", true) as Boolean
    )
)

"Background WebSocket Keep Alive" -> listOf(
    SubHookData(
        name = "SafeDK Background Detection",
        description = "Spoof SafeDK background state",
        isEnabled = Config.get("sub_hook_websocket_safedk", true) as Boolean
    ),
    SubHookData(
        name = "WebSocket Auto-Reconnect",
        description = "Automatically reconnect disconnected WebSockets",
        isEnabled = Config.get("sub_hook_websocket_reconnect", true) as Boolean
    )
)

else -> emptyList()
}
}

// Add function to toggle hook
fun toggleHook(hookName: String, enabled: Boolean) {
viewModelScope.launch {
Config.setHookEnabled(hookName, enabled)
loadHookCategories()
}
}

// Add function to toggle sub-hook
fun toggleSubHook(hookName: String, subHookName: String, enabled: Boolean) {
viewModelScope.launch {
val configKey = when (hookName) {
    "Comprehensive Anti Detection" -> when (subHookName) {
        "Activity Finish Protection" -> "sub_hook_comprehensive_activity_finish"
        "File System Hiding" -> "sub_hook_comprehensive_filesystem"
        "Build Properties Spoofing" -> "sub_hook_comprehensive_build_props"
        "Package Manager Hiding" -> "sub_hook_comprehensive_pkg_mgr"
        "Native Library Blocking" -> "sub_hook_comprehensive_native_libs"
        "Play Integrity Blocking" -> "sub_hook_comprehensive_play_integrity"
        else -> null
    }

    "Anti Detection" -> when (subHookName) {
        "Root Detection" -> "sub_hook_anti_detection_root"
        "Emulator Detection" -> "sub_hook_anti_detection_emulator"
        "Sift Science" -> "sub_hook_anti_detection_sift"
        else -> null
    }

    "Background WebSocket Keep Alive" -> when (subHookName) {
        "SafeDK Background Detection" -> "sub_hook_websocket_safedk"
        "WebSocket Auto-Reconnect" -> "sub_hook_websocket_reconnect"
        else -> null
    }

    else -> null
}

configKey?.let {
    Config.put(it, enabled)
    loadHookCategories()
}
}
}

class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
@Suppress("UNCHECKED_CAST")
override fun <T : ViewModel> create(modelClass: Class<T>): T {
if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
    return SettingsViewModel(context) as T
}
throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
}
}

@Composable
fun rememberViewModel(): SettingsViewModel {
val context = LocalContext.current
val factory = remember(context) { SettingsViewModelFactory(context) }
return viewModel(factory = factory)
}
}
