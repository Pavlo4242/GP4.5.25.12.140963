/*
package com.grindrplus.ui

data class SettingItem(val name: String, val description: String, val isEnabled: Boolean, val type: String) // type: "hook" or "task"

class SettingsViewModel(
    private val hookManager: HookManager,
    private val taskManager: TaskManager
) : ViewModel() {

    private val _settingsList = MutableStateFlow<List<SettingItem>>(emptyList())
    val settingsList: StateFlow<List<SettingItem>> = _settingsList.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val hooks = hookManager.hooks.values.map { hook ->
                SettingItem(hook.hookName, hook.hookDesc, Config.isHookEnabled(hook.hookName), "hook")
            }
            val tasks = taskManager.tasks.values.map { task ->
                SettingItem(task.id, task.description, Config.isTaskEnabled(task.id), "task")
            }
            _settingsList.value = (hooks + tasks).sortedBy { it.name }
        }
    }

    fun toggleSetting(item: SettingItem, enabled: Boolean) {
        viewModelScope.launch {
            if (item.type == "hook") {
                Config.setHookEnabled(item.name, enabled)
                hookManager.reloadHooks() // Reload to apply changes
            } else if (item.type == "task") {
                Config.setTaskEnabled(item.name, enabled)
                taskManager.toggleTask(item.name, enabled)
            }
            loadSettings() // Refresh list
        }
    }
}*/
