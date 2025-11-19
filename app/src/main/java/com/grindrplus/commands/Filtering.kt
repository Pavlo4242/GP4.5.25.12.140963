package com.grindrplus.commands

import android.app.AlertDialog
import android.widget.Toast
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.ui.Utils.copyToClipboard

private const val ENABLE_LOGGING = false

private fun logOutput(tag: String, message: String) {
    if (ENABLE_LOGGING) {
        android.util.Log.d("GrindrPlusFilter", "$tag: $message")
    }
}

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
        val newState = !currentState
        val message = "Custom filtering ${if (newState) "enabled" else "disabled"}"

        logOutput("FILTER_TOGGLE", message)
        GrindrPlus.showToast(android.widget.Toast.LENGTH_SHORT, message)
    }

    @Command(
        name = "favorites_columns",
        aliases = ["fc"],
        help = "Set favorites grid columns. Usage: fc <2-6>"
    )
    fun setFavoritesColumns(args: List<String>) {
        val cols = args.getOrNull(0)?.toIntOrNull()
        if (cols == null || cols !in 2..6) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Invalid column count. Use 2-6.")
            return
        }
        Config.put("favorites_grid_columns", cols)
        GrindrPlus.showToast(Toast.LENGTH_SHORT, "Favorites grid set to $cols columns.")
    }

    @Command(
        name = "grid_columns",
        aliases = ["gc"],
        help = "Set main cascade grid columns. Usage: gc <2-6>"
    )
    fun setGridColumns(args: List<String>) {
        val cols = args.getOrNull(0)?.toIntOrNull()
        if (cols == null || cols !in 2..6) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Invalid column count. Use 2-6.")
            return
        }
        Config.put("cascade_grid_columns", cols)
        GrindrPlus.showToast(Toast.LENGTH_SHORT, "Main grid set to $cols columns.")
    }


    @Command(
        name = "filter_distance",
        aliases = ["fd"],
        help = "Set maximum distance filter in meters (0 to disable). Usage: filter_distance <meters>"
    )
    fun filterDistance(args: List<String>) {
        if (args.isEmpty()) {
            val current = Config.get("filter_max_distance", 0) as Int
            val message = "Current max distance: $current meters"

            logOutput("FILTER_DISTANCE_QUERY", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
            return
        }

        val distance = args[0].toIntOrNull()
        if (distance == null || distance < 0) {
            val message = "Invalid distance. Use a positive number or 0 to disable."

            logOutput("FILTER_DISTANCE_ERROR", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
            return
        }

        Config.put("filter_max_distance", distance)
        val message =
            "Max distance set to: ${if (distance == 0) "disabled" else "$distance meters"}"

        logOutput("FILTER_DISTANCE_SET", message)
        GrindrPlus.showToast(android.widget.Toast.LENGTH_SHORT, message)
    }

    @Command(
        name = "filter_favorites",
        aliases = ["ff"],
        help = "Toggle favorites-only filter"
    )
    fun filterFavorites(args: List<String>) {
        val currentState = Config.get("filter_favorites_only", false) as Boolean
        Config.put("filter_favorites_only", !currentState)
        val newState = !currentState
        val message = "Favorites filter ${if (newState) "enabled" else "disabled"}"

        logOutput("FILTER_FAVORITES", message)
        GrindrPlus.showToast(android.widget.Toast.LENGTH_SHORT, message)
    }

    @Command(
        name = "filter_gender",
        aliases = ["fg"],
        help = "Set gender filter (0=all, 1=male, 2=trans, 3=nonbinary, 4=cis woman, 5=transwoman, 6=cis man, 7=transman). Usage: filter_gender <number>"
    )
    fun filterGender(args: List<String>) {
        if (args.isEmpty()) {
            val current = Config.get("filter_gender", 0) as Int
            val genderName = when (current) {
                1 -> "Man"
                2 -> "Trans"
                3 -> "Non-binary"
                4 -> "Cis Woman"
                5 -> "Trans Woman"
                6 -> "Cis Man"
                7 -> "Trans Man"
                else -> "All"
            }
            val message = "Current gender filter: $genderName ($current)"

            logOutput("FILTER_GENDER_QUERY", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
            return
        }

        val gender = args[0].toIntOrNull()
        if (gender == null || gender !in 0..7) {
            val message = "Invalid gender. Use 0 (all) or 1-7 for specific genders."

            logOutput("FILTER_GENDER_ERROR", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
            return
        }

        Config.put("filter_gender", gender)
        val genderName = when (gender) {
            1 -> "Man"
            2 -> "Trans"
            3 -> "Non-binary"
            4 -> "Cis Woman"
            5 -> "Trans Woman"
            6 -> "Cis Man"
            7 -> "Trans Man"
            else -> "All"
        }
        val message = "Gender filter set to: $genderName"

        logOutput("FILTER_GENDER_SET", message)
        GrindrPlus.showToast(android.widget.Toast.LENGTH_SHORT, message)
    }

    @Command(
        name = "filter_tribe",
        aliases = ["ft"],
        help = "Set tribe filter (0=all, 1-14=specific tribe). Usage: filter_tribe <number>"
    )
    fun filterTribe(args: List<String>) {
        if (args.isEmpty()) {
            val current = Config.get("filter_tribe", 0) as Int
            val message = "Current tribe filter: ${if (current == 0) "All" else "Tribe #$current"}"

            logOutput("FILTER_TRIBE_QUERY", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
            return
        }

        val tribe = args[0].toIntOrNull()
        if (tribe == null || tribe !in 0..14) {
            val message = "Invalid tribe. Use 0 (all) or 1-14 for specific tribes."

            logOutput("FILTER_TRIBE_ERROR", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
            return
        }

        Config.put("filter_tribe", tribe)
        val message = "Tribe filter set to: ${if (tribe == 0) "All" else "Tribe #$tribe"}"

        logOutput("FILTER_TRIBE_SET", message)
        GrindrPlus.showToast(android.widget.Toast.LENGTH_SHORT, message)
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
            val message =
                "Current ethnicity filter: ${if (current == "0") "All" else "$current ($mode)"}"

            logOutput("FILTER_ETHNICITY_QUERY", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
            return
        }

        val input = args.joinToString(" ").replace(" ", "")

        if (input == "0") {
            Config.put("filter_ethnicity", "0")
            val message = "Ethnicity filter disabled (showing all)"

            logOutput("FILTER_ETHNICITY_SET", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_SHORT, message)
            return
        }

        val ethnicities = input.split(",").mapNotNull { it.toIntOrNull() }
        if (ethnicities.isEmpty() || ethnicities.any { it < 1 || it > 9 }) {
            val message =
                "Invalid input. Use comma-separated numbers 1-9 (e.g., 1,3,5) or 0 for all."

            logOutput("FILTER_ETHNICITY_ERROR", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
            return
        }

        Config.put("filter_ethnicity", ethnicities.joinToString(","))
        val message = "Ethnicity filter set to: ${ethnicities.joinToString(", ")}"

        logOutput("FILTER_ETHNICITY_SET", message)
        GrindrPlus.showToast(android.widget.Toast.LENGTH_SHORT, message)
    }

    @Command(
        name = "filter_ethnicity_mode",
        aliases = ["fem"],
        help = "Set ethnicity filter mode: include (show matching) or exclude (hide matching)"
    )
    fun filterEthnicityMode(args: List<String>) {
        if (args.isEmpty()) {
            val current = Config.get("filter_ethnicity_mode", "include") as String
            val message = "Current mode: $current (use 'include' or 'exclude')"

            logOutput("FILTER_ETHNICITY_MODE_QUERY", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
            return
        }

        val mode = args[0].lowercase()
        if (mode != "include" && mode != "exclude") {
            val message = "Invalid mode. Use 'include' or 'exclude'."

            logOutput("FILTER_ETHNICITY_MODE_ERROR", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
            return
        }

        Config.put("filter_ethnicity_mode", mode)
        val message = "Ethnicity filter mode set to: $mode"

        logOutput("FILTER_ETHNICITY_MODE_SET", message)
        GrindrPlus.showToast(android.widget.Toast.LENGTH_SHORT, message)
    }

    @Command(
        name = "filter_social",
        aliases = ["fs"],
        help = "Toggle filter for profiles with social networks only"
    )
    fun filterSocial(args: List<String>) {
        val currentState = Config.get("filter_has_social_networks", false) as Boolean
        Config.put("filter_has_social_networks", !currentState)
        val newState = !currentState
        val message = "Social networks filter ${if (newState) "enabled" else "disabled"}"

        logOutput("FILTER_SOCIAL", message)
        GrindrPlus.showToast(android.widget.Toast.LENGTH_SHORT, message)
    }

    @Command(
        name = "filter_status",
        aliases = ["fst"],
        help = "Show current filter settings in dialog"
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
        val aboutText = Config.get("filter_about_text", "") as String
        val aboutMode = Config.get("filter_about_mode", "include") as String
        val tags = Config.get("filter_tags", "") as String
        val tagsMode = Config.get("filter_tags_mode", "include") as String
        val ageMin = Config.get("filter_age_min", 0) as Int
        val ageMax = Config.get("filter_age_max", 0) as Int
        val includeNoAge = Config.get("filter_age_include_no_age", true) as Boolean

        val genderName = when (gender) {
            1 -> "Man"
            2 -> "Trans"
            3 -> "Non-binary"
            4 -> "Cis Woman"
            5 -> "Trans Woman"
            6 -> "Cis Man"
            7 -> "Trans Man"
            else -> "All"
        }

        val ageFilterText = when {
            ageMin == 0 && ageMax == 0 -> "None"
            ageMin == ageMax -> "$ageMin years"
            else -> "$ageMin-$ageMax years (no age: ${if (includeNoAge) "include" else "exclude"})"
        }

        val status = """
Filter Status:
• Filtering: ${if (enabled) "ON" else "OFF"}
• Max Distance: ${if (maxDistance == 0) "Off" else "${maxDistance}m"}
• Favorites Only: ${if (favoritesOnly) "ON" else "OFF"}
• Gender: $genderName
• Tribe: ${if (tribe == 0) "All" else "#$tribe"}
• Ethnicity: ${if (ethnicity == "0") "All" else "$ethnicity ($ethnicityMode)"}
• Social: ${if (hasSocial) "ON" else "OFF"}
• About Text: ${if (aboutText.isEmpty()) "None" else "\"$aboutText\" ($aboutMode)"}
• Tags: ${if (tags.isEmpty()) "None" else "$tags ($tagsMode)"}
• Age: $ageFilterText
    """.trimIndent()

        logOutput("FILTER_STATUS", status)

        // Use the same pattern as CommandHandler.help
        GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
            AlertDialog.Builder(activity)
                .setTitle("Filter Status")
                .setMessage(status)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .setNegativeButton("Copy") { _, _ ->
                    copyToClipboard("Filter Status", status)
                }
                .create()
                .show()
        }
    }

    @Command(
        name = "filter_help",
        aliases = ["fh"],
        help = "Show filter commands help with copy option"
    )
    fun filterHelp(args: List<String>) {
        val helpText = """
Filtering Commands:

/filter (f) - Toggle custom filtering on/off
/favorites_columns (fc) <2-6> - Set columns for Favorites grid
/grid_columns (gc) <2-6> - Set columns for Main grid
/filter_distance (fd) <meters> - Set max distance filter (0 to disable)
/filter_favorites (ff) - Toggle favorites-only filter
/filter_gender (fg) <0-7> - Set gender filter (0=all, 1-7=specific)
/filter_tribe (ft) <0-14> - Set tribe filter (0=all, 1-14=specific)
/filter_ethnicity (fe) <numbers> - Set ethnicity filter (comma-separated, 0=all)
/filter_ethnicity_mode (fem) <include/exclude> - Set ethnicity filter mode
/filter_social (fs) - Toggle social networks filter
/filter_about (fa) <text> - Set about me text filter (0 to clear)
/filter_about_mode (fam) <include/exclude> - Set about text filter mode
/filter_tags (fta) <tags> - Set tag filter (comma-separated, 0 to clear)
/filter_tags_mode (ftm) <include/exclude> - Set tag filter mode
/filter_age (fage) <age> - Set age filter (25 or 20-30, 0 to clear)
/filter_age_noage (fana) - Toggle including profiles with no age
/filter_status (fst) - Show current filter settings in dialog
/filter_reset (fr) - Reset all filters to default
/filter_help (fh) - Show this help message

Ethnicity values: 1=Asian, 2=Black, 3=Latino, 4=Middle Eastern, 5=Mixed, 6=Native American, 7=South Asian, 8=White, 9=Other
Gender values: 1=Man, 2=Trans, 3=Non-binary, 4=Cis Woman, 5=Trans Woman, 6=Cis Man, 7=Trans Man
    """.trimIndent()

        logOutput("FILTER_HELP", "Displayed filter help")

        // Use the same pattern as CommandHandler.help
        GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
            AlertDialog.Builder(activity)
                .setTitle("Filter Commands Help")
                .setMessage(helpText)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .setNegativeButton("Copy") { _, _ ->
                    copyToClipboard("Filter Commands Help", helpText)
                }
                .create()
                .show()
        }
    }

    @Command(
        name = "filter_reset",
        aliases = ["fr"],
        help = "Reset all filters to default (disabled)"
    )
    fun filterReset(args: List<String>) {
        Config.put("custom_filtering_enabled", false)
        Config.put("filter_max_distance", 0)
        Config.put("filter_favorites_only", false)
        Config.put("filter_gender", 0)
        Config.put("filter_tribe", 0)
        Config.put("filter_ethnicity", "0")
        Config.put("filter_ethnicity_mode", "include")
        Config.put("filter_has_social_networks", false)
        Config.put("filter_about_text", "")
        Config.put("filter_about_mode", "include")
        Config.put("filter_tags", "")
        Config.put("filter_tags_mode", "include")
        Config.put("filter_age_min", 0)
        Config.put("filter_age_max", 0)
        Config.put("filter_age_include_no_age", true)

        val message = "All filters reset to default"
        logOutput("FILTER_RESET", message)
        GrindrPlus.showToast(android.widget.Toast.LENGTH_SHORT, message)
    }

    private fun convertUnicodeEscapes(input: String): String {
        val pattern = "\\\\u([0-9a-fA-F]{4})".toRegex()
        return pattern.replace(input) { matchResult ->
            val hex = matchResult.groupValues[1]
            val codePoint = hex.toInt(16)
            String(Character.toChars(codePoint))
        }
    }

    @Command(
        name = "filter_about",
        aliases = ["fa"],
        help = "Set about me text filter (supports Unicode escapes like \\uD83E\\uDD76). Usage: filter_about <text> or filter_about 0 to clear"
    )
    fun filterAbout(args: List<String>) {
        if (args.isEmpty()) {
            val current = Config.get("filter_about_text", "") as String
            val mode = Config.get("filter_about_mode", "include") as String
            val message =
                "Current about filter: ${if (current.isEmpty()) "None" else "\"$current\" ($mode)"}"

            logOutput("FILTER_ABOUT_QUERY", message)
            GrindrPlus.showToast(Toast.LENGTH_LONG, message)
            return
        }

        val input = args.joinToString(" ").trim()

        if (input == "0") {
            Config.put("filter_about_text", "")
            val message = "About text filter cleared"

            logOutput("FILTER_ABOUT_SET", message)
            GrindrPlus.showToast(Toast.LENGTH_SHORT, message)
            return
        }

        // Convert Unicode escape sequences
        val convertedInput = convertUnicodeEscapes(input)
        Config.put("filter_about_text", convertedInput)
        val message = "About text filter set to: \"$convertedInput\""

        logOutput("FILTER_ABOUT_SET", message)
        GrindrPlus.showToast(Toast.LENGTH_SHORT, message)
    }


    @Command(
        name = "filter_about_mode",
        aliases = ["fam"],
        help = "Set about text filter mode: include (show matching) or exclude (hide matching)"
    )
    fun filterAboutMode(args: List<String>) {
        if (args.isEmpty()) {
            val current = Config.get("filter_about_mode", "include") as String
            val message = "Current about mode: $current (use 'include' or 'exclude')"

            logOutput("FILTER_ABOUT_MODE_QUERY", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
            return
        }

        val mode = args[0].lowercase()
        if (mode != "include" && mode != "exclude") {
            val message = "Invalid mode. Use 'include' or 'exclude'."

            logOutput("FILTER_ABOUT_MODE_ERROR", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
            return
        }

        Config.put("filter_about_mode", mode)
        val message = "About text filter mode set to: $mode"

        logOutput("FILTER_ABOUT_MODE_SET", message)
        GrindrPlus.showToast(android.widget.Toast.LENGTH_SHORT, message)
    }


    @Command(
        name = "filter_age",
        aliases = ["fage"],
        help = "Set age filter. Usage: filter_age <min>-<max> or filter_age <exact> or filter_age 0 to clear"
    )
    fun filterAge(args: List<String>) {
        if (args.isEmpty()) {
            val current = Config.get("filter_age_min", 0) as Int
            val currentMax = Config.get("filter_age_max", 0) as Int
            val includeNoAge = Config.get("filter_age_include_no_age", true) as Boolean

            val message = when {
                current == 0 && currentMax == 0 -> "Current age filter: None"
                current == currentMax -> "Current age filter: $current years old"
                else -> "Current age filter: $current-$currentMax years (include no age: ${if (includeNoAge) "yes" else "no"})"
            }

            logOutput("FILTER_AGE_QUERY", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
            return
        }

        val input = args[0].trim()

        if (input == "0") {
            Config.put("filter_age_min", 0)
            Config.put("filter_age_max", 0)
            val message = "Age filter cleared"

            logOutput("FILTER_AGE_SET", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_SHORT, message)
            return
        }

        // Parse age input - can be single number or range
        val ageRange = input.split("-")
        val minAge: Int
        val maxAge: Int

        when {
            ageRange.size == 1 -> {
                // Single age
                minAge = ageRange[0].toIntOrNull() ?: 0
                maxAge = minAge
                if (minAge == 0) {
                    val message =
                        "Invalid age format. Use: filter_age 25 or filter_age 20-30 or filter_age 0 to clear"
                    logOutput("FILTER_AGE_ERROR", message)
                    GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
                    return
                }
            }

            ageRange.size == 2 -> {
                // Age range
                minAge = ageRange[0].toIntOrNull() ?: 0
                maxAge = ageRange[1].toIntOrNull() ?: 0
                if (minAge == 0 || maxAge == 0 || minAge > maxAge) {
                    val message = "Invalid age range. Use format: filter_age 20-30"
                    logOutput("FILTER_AGE_ERROR", message)
                    GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
                    return
                }
            }

            else -> {
                val message =
                    "Invalid age format. Use: filter_age 25 or filter_age 20-30 or filter_age 0 to clear"
                logOutput("FILTER_AGE_ERROR", message)
                GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
                return
            }
        }

        Config.put("filter_age_min", minAge)
        Config.put("filter_age_max", maxAge)

        val message = if (minAge == maxAge) {
            "Age filter set to: $minAge years old"
        } else {
            "Age filter set to: $minAge-$maxAge years"
        }

        logOutput("FILTER_AGE_SET", message)
        GrindrPlus.showToast(android.widget.Toast.LENGTH_SHORT, message)
    }

    @Command(
        name = "filter_age_noage",
        aliases = ["fana"],
        help = "Toggle whether to include profiles with no age set in age filter results"
    )
    fun filterAgeNoAge(args: List<String>) {
        val currentState = Config.get("filter_age_include_no_age", true) as Boolean
        Config.put("filter_age_include_no_age", !currentState)
        val newState = !currentState
        val message = "Include profiles with no age: ${if (newState) "YES" else "NO"}"

        logOutput("FILTER_AGE_NOAGE", message)
        GrindrPlus.showToast(android.widget.Toast.LENGTH_SHORT, message)
    }

    @Command(
        name = "filter_tags",
        aliases = ["fta"],
        help = "Set tag filter (comma-separated tags). Usage: filter_tags <tags> or filter_tags 0 to clear"
    )
    fun filterTags(args: List<String>) {
        if (args.isEmpty()) {
            val current = Config.get("filter_tags", "") as String
            val mode = Config.get("filter_tags_mode", "any") as String // Changed default to "any"
            val message =
                "Current tag filter: ${if (current.isEmpty()) "None" else "$current ($mode)"}"

            logOutput("FILTER_TAGS_QUERY", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
            return
        }

        val input = args.joinToString(" ").trim()

        if (input == "0") {
            Config.put("filter_tags", "")
            val message = "Tag filter cleared"

            logOutput("FILTER_TAGS_SET", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_SHORT, message)
            return
        }

        Config.put("filter_tags", input)
        val message = "Tag filter set to: $input"

        logOutput("FILTER_TAGS_SET", message)
        GrindrPlus.showToast(android.widget.Toast.LENGTH_SHORT, message)
    }

    @Command(
        name = "filter_tags_mode",
        aliases = ["ftm"],
        help = "Set tag filter mode: any (show if ANY tag matches) or all (show if ALL tags match) or exclude (hide matching)"
    )
    fun filterTagsMode(args: List<String>) {
        if (args.isEmpty()) {
            val current = Config.get("filter_tags_mode", "any") as String
            val message = "Current tag mode: $current (use 'any', 'all', or 'exclude')"

            logOutput("FILTER_TAGS_MODE_QUERY", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
            return
        }

        val mode = args[0].lowercase()
        if (mode != "any" && mode != "all" && mode != "exclude") {
            val message = "Invalid mode. Use 'any', 'all', or 'exclude'."

            logOutput("FILTER_TAGS_MODE_ERROR", message)
            GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, message)
            return
        }

        Config.put("filter_tags_mode", mode)
        val message = "Tag filter mode set to: $mode"

        logOutput("FILTER_TAGS_MODE_SET", message)
        GrindrPlus.showToast(android.widget.Toast.LENGTH_SHORT, message)
    }
}