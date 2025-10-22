package com.grindrplus.manager.settings

data class HookCategory(
    val id: String,
    val name: String,
    val description: String,
    val hooks: List<HookSettingData>
)

data class HookSettingData(
    val name: String,
    val description: String,
    val isEnabled: Boolean,
    val subHooks: List<SubHookData> = emptyList()
)

data class SubHookData(
    val name: String,
    val description: String,
    val isEnabled: Boolean
)