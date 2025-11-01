package com.grindrplus.manager.settings

data class SwitchSetting(
    override val id: String,
    override val title: String,
    val description: String? = null,
    val isChecked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
) : Setting(id, title)

sealed class Setting(open val id: String, open val title: String)

data class TextSetting(
    override val id: String,
    override val title: String,
    val description: String? = null,
    val value: String,
    val onValueChange: (String) -> Unit,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val validator: ((String) -> String?)? = null,
) : Setting(id, title)

data class ButtonAction(
    val name: String,
    val action: () -> Unit
)

data class LocationData(val name: String, val lat: String, val lon: String)

data class LocationListSetting(
    override val id: String,
    override val title: String,
    val description: String? = null,
    val locations: List<LocationData>,
    val onLocationsChange: (List<LocationData>) -> Unit
) : Setting(id, title)


data class TextSettingWithButtons(
    override val id: String,
    override val title: String,
    val description: String? = null,
    val value: String,
    val onValueChange: (String) -> Unit,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val validator: ((String) -> String?)? = null,
    val buttons: List<ButtonAction> = emptyList()
) : Setting(id, title)

data class ButtonSetting(
    override val id: String,
    override val title: String,
    val onClick: () -> Unit,
) : Setting(id, title)

data class SettingGroup(
    val id: String,
    val title: String,
    val settings: List<Setting>,
)

enum class KeyboardType {
    Text, Number, Email, Password, Phone
}