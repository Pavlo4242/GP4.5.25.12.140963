package com.grindrplus.commands

import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config

class Filtering(recipient: String, sender: String) : CommandModule(
    "Filtering",
    recipient,
    sender
) {
    @Command(
        name = "filter",
        aliases = ["f"],
        help = "Toggle custom filtering on/off"
    )
    fun toggleFiltering(args: List<String>) {
        val currentState = Config.get("custom_filtering_enabled", false) as Boolean
        Config.put("custom_filtering_enabled", !currentState)
        GrindrPlus.showToast(
            android.widget.Toast.LENGTH_SHORT,
            "Custom filtering ${if (!currentState) "enabled" else "disabled"}"
        )
    }

    @Command(
        name = "filter_distance",
        aliases = ["fd"],
        help = "Set maximum distance filter in meters (0 to disable). Usage: filter_distance <meters>"
    )
    fun filterDistance(args: List<String>) {
        if (args.isEmpty()) {
            GrindrPlus.showToast(
                android.widget.Toast.LENGTH_LONG,
                "Current max distance: ${Config.get("filter_max_distance", 0)} meters"
            )
            return
        }

        val distance = args[0].toIntOrNull()
        if (distance == null || distance < 0) {
            GrindrPlus.showToast(
                android.widget.Toast.LENGTH_LONG,
                "Invalid distance. Use a positive number or 0 to disable."
            )
            return
        }

        Config.put("filter_max_distance", distance)
        GrindrPlus.showToast(
            android.widget.Toast.LENGTH_SHORT,
            "Max distance set to: ${if (distance == 0) "disabled" else "$distance meters"}"
        )
    }

    @Command(
        name = "filter_favorites",
        aliases = ["ff"],
        help = "Toggle favorites-only filter"
    )
    fun filterFavorites(args: List<String>) {
        val currentState = Config.get("filter_favorites_only", false) as Boolean
        Config.put("filter_favorites_only", !currentState)
        GrindrPlus.showToast(
            android.widget.Toast.LENGTH_SHORT,
            "Favorites filter ${if (!currentState) "enabled" else "disabled"}"
        )
    }

    @Command(
        name = "filter_gender",
        aliases = ["fg"],
        help = "Set gender filter (0=all, 1=male, 2=trans, 3=nonbinary). Usage: filter_gender <number>"
    )
    fun filterGender(args: List<String>) {
        if (args.isEmpty()) {
            val current = Config.get("filter_gender", 0) as Int
            val genderName = when (current) {
                1 -> "Male"
                2 -> "Trans"
                3 -> "Non-binary"
                else -> "All"
            }
            GrindrPlus.showToast(
                android.widget.Toast.LENGTH_LONG,
                "Current gender filter: $genderName ($current)"
            )
            return
        }

        val gender = args[0].toIntOrNull()
        if (gender == null || gender !in 0..3) {
            GrindrPlus.showToast(
                android.widget.Toast.LENGTH_LONG,
                "Invalid gender. Use 0 (all), 1 (male), 2 (trans), or 3 (non-binary)."
            )
            return
        }

        Config.put("filter_gender", gender)
        val genderName = when (gender) {
            1 -> "Male"
            2 -> "Trans"
            3 -> "Non-binary"
            else -> "All"
        }
        GrindrPlus.showToast(
            android.widget.Toast.LENGTH_SHORT,
            "Gender filter set to: $genderName"
        )
    }

    @Command(
        name = "filter_tribe",
        aliases = ["ft"],
        help = "Set tribe filter (0=all, 1-14=specific tribe). Usage: filter_tribe <number>"
    )
    fun filterTribe(args: List<String>) {
        if (args.isEmpty()) {
            val current = Config.get("filter_tribe", 0) as Int
            GrindrPlus.showToast(
                android.widget.Toast.LENGTH_LONG,
                "Current tribe filter: ${if (current == 0) "All" else "Tribe #$current"}"
            )
            return
        }

        val tribe = args[0].toIntOrNull()
        if (tribe == null || tribe !in 0..14) {
            GrindrPlus.showToast(
                android.widget.Toast.LENGTH_LONG,
                "Invalid tribe. Use 0 (all) or 1-14 for specific tribes."
            )
            return
        }

        Config.put("filter_tribe", tribe)
        GrindrPlus.showToast(
            android.widget.Toast.LENGTH_SHORT,
            "Tribe filter set to: ${if (tribe == 0) "All" else "Tribe #$tribe"}"
        )
    }

    @Command(
        name = "filter_ethnicity",
        aliases = ["fe"],
        help = "Set ethnicity filter (comma-separated, 0=all). Usage: filter_ethnicity <numbers> or filter_ethnicity 0"
    )
    fun filterEthnicity(args: List<String>) {
        if (args.isEmpty()) {
            val current = Config.get("filter_ethnicity", "0") as String
            val mode = Config.get("filter_ethnicity_mode", "include") as String
            GrindrPlus.showToast(
                android.widget.Toast.LENGTH_LONG,
                "Current ethnicity filter: ${if (current == "0") "All" else "$current ($mode)"}"
            )
            return
        }

        val input = args.joinToString(" ").replace(" ", "")

        // Validate input
        if (input == "0") {
            Config.put("filter_ethnicity", "0")
            GrindrPlus.showToast(
                android.widget.Toast.LENGTH_SHORT,
                "Ethnicity filter disabled (showing all)"
            )
            return
        }

        val ethnicities = input.split(",").mapNotNull { it.toIntOrNull() }
        if (ethnicities.isEmpty() || ethnicities.any { it < 1 || it > 12 }) {
            GrindrPlus.showToast(
                android.widget.Toast.LENGTH_LONG,
                "Invalid input. Use comma-separated numbers 1-12 (e.g., 1,3,5) or 0 for all."
            )
            return
        }

        Config.put("filter_ethnicity", ethnicities.joinToString(","))
        GrindrPlus.showToast(
            android.widget.Toast.LENGTH_SHORT,
            "Ethnicity filter set to: ${ethnicities.joinToString(", ")}"
        )
    }

    @Command(
        name = "filter_ethnicity_mode",
        aliases = ["fem"],
        help = "Set ethnicity filter mode: include (show matching) or exclude (hide matching)"
    )
    fun filterEthnicityMode(args: List<String>) {
        if (args.isEmpty()) {
            val current = Config.get("filter_ethnicity_mode", "include") as String
            GrindrPlus.showToast(
                android.widget.Toast.LENGTH_LONG,
                "Current mode: $current (use 'include' or 'exclude')"
            )
            return
        }

        val mode = args[0].lowercase()
        if (mode != "include" && mode != "exclude") {
            GrindrPlus.showToast(
                android.widget.Toast.LENGTH_LONG,
                "Invalid mode. Use 'include' or 'exclude'."
            )
            return
        }

        Config.put("filter_ethnicity_mode", mode)
        GrindrPlus.showToast(
            android.widget.Toast.LENGTH_SHORT,
            "Ethnicity filter mode set to: $mode"
        )
    }

    @Command(
        name = "filter_social",
        aliases = ["fs"],
        help = "Toggle filter for profiles with social networks only"
    )
    fun filterSocial(args: List<String>) {
        val currentState = Config.get("filter_has_social_networks", false) as Boolean
        Config.put("filter_has_social_networks", !currentState)
        GrindrPlus.showToast(
            android.widget.Toast.LENGTH_SHORT,
            "Social networks filter ${if (!currentState) "enabled" else "disabled"}"
        )
    }

    @Command(
        name = "filter_status",
        aliases = ["fst"],
        help = "Show current filter settings"
    )
    fun filterStatus(args: List<String>) {
        val enabled = Config.get("custom_filtering_enabled", false) as Boolean
        val maxDistance = Config.get("filter_max_distance", 0) as Int
        val favoritesOnly = Config.get("filter_favorites_only", false) as Boolean
        val gender = Config.get("filter_gender", 0) as Int
        val tribe = Config.get("filter_tribe", 0) as Int
        val ethnicity = Config.get("filter_ethnicity", "0") as String
        val ethnicityMode = Config.get("filter_ethnicity_mode", "include") as String
        val hasSocial = Config.get("filter_has_social_networks", false) as Boolean

        val genderName = when (gender) {
            1 -> "Male"
            2 -> "Trans"
            3 -> "Non-binary"
            else -> "All"
        }

        val status = """
            Custom Filtering: ${if (enabled) "ON" else "OFF"}
            Max Distance: ${if (maxDistance == 0) "Disabled" else "$maxDistance meters"}
            Favorites Only: ${if (favoritesOnly) "ON" else "OFF"}
            Gender: $genderName ($gender)
            Tribe: ${if (tribe == 0) "All" else "Tribe #$tribe"}
            Ethnicity: ${if (ethnicity == "0") "All" else "$ethnicity ($ethnicityMode)"}
            Social Networks: ${if (hasSocial) "ON" else "OFF"}
        """.trimIndent()

        GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, status)
    }
}