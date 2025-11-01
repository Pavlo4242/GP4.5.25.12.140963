package com.grindrplus.commands

import android.app.AlertDialog
import android.net.Uri
import android.widget.Toast
import com.grindrplus.GrindrPlus
import com.grindrplus.GrindrPlus.packageName
import com.grindrplus.core.Config
import com.grindrplus.core.Logger
import com.grindrplus.core.TaskScheduler
import com.grindrplus.core.Utils.coordsToGeoHash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

class Location(recipient: String, sender: String) : CommandModule("Location", recipient, sender) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val scheduler = TaskScheduler(coroutineScope)

    companion object {
        private const val STATIC_TELEPORT_TASK = "static_teleport"
        private const val DEFAULT_INTERVAL_MS = 90L * 1000
        private const val STATIC_LOCATION_KEY = "static_location_coords"
        private const val STATIC_INTERVAL_KEY = "static_location_interval"
    }

    init {
        // FIXED: Use coroutine to avoid blocking initialization
        coroutineScope.launch {
            try {
                val savedLocation = Config.get(STATIC_LOCATION_KEY, "") as String
                if (savedLocation.isNotEmpty()) {
                    val interval = Config.get(STATIC_INTERVAL_KEY, DEFAULT_INTERVAL_MS) as Long
                    startStaticTeleportTask(savedLocation, interval)
                    Logger.i("Static Teleportation task re-started for $savedLocation every ${interval / 1000}s")
                }
            } catch (e: Exception) {
                Logger.e("Failed to restart static teleport: ${e.message}")
                // Clear invalid saved location to prevent future issues
                Config.put(STATIC_LOCATION_KEY, "")
            }
        }
    }

    /**
     * Helper function to manage the periodic static teleport task.
     */
    private fun startStaticTeleportTask(coords: String, interval: Long) {
        try {
            // Cancel any existing task first
            scheduler.cancelTask(STATIC_TELEPORT_TASK)

            // Split and validate coordinates
            val parts = coords.split(",")
            if (parts.size != 2) {
                Logger.e("Invalid format for static teleport: $coords")
                Config.put(STATIC_LOCATION_KEY, "")
                return
            }

            val lat = parts[0].trim().toDoubleOrNull()
            val lon = parts[1].trim().toDoubleOrNull()

            if (lat == null || lon == null) {
                Logger.e("Invalid coordinate values: $coords")
                Config.put(STATIC_LOCATION_KEY, "")
                return
            }

            // Validate coordinate ranges
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                Logger.e("Coordinates out of valid range: lat=$lat, lon=$lon")
                Config.put(STATIC_LOCATION_KEY, "")
                return
            }

            // Start the periodic task
            scheduler.periodic(STATIC_TELEPORT_TASK, interval) {
                try {
                    teleportToCoordinates(lat, lon, silent = true)
                    Logger.i("Periodic static teleport to $lat, $lon")
                } catch (e: Exception) {
                    Logger.e("Error during periodic teleport: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to start static teleport task: ${e.message}")
            Config.put(STATIC_LOCATION_KEY, "")
        }
    }

    @Command(name = "tp", aliases = ["tp"], help = "Teleport to a location")
    fun teleport(args: List<String>) {
        // Check if forced coordinates are enabled
        if (Config.get("forced_coordinates", "") as String != "") {
            GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                AlertDialog.Builder(activity)
                    .setTitle("Teleportation disabled")
                    .setMessage(
                        "GrindrPlus is currently using forced coordinates. " +
                                "Please disable it to use teleportation."
                    )
                    .setPositiveButton("OK", null)
                    .setNegativeButton("Disable") { _, _ ->
                        Config.put("forced_coordinates", "")
                        GrindrPlus.bridgeClient.deleteForcedLocation(packageName)
                        GrindrPlus.showToast(Toast.LENGTH_LONG, "Forced coordinates disabled")
                    }
                    .show()
            }
            return
        }

        // Toggle teleportation if no arguments
        if (args.isEmpty()) {
            val status = (Config.get("current_location", "") as String).isEmpty()
            if (!status) {
                Config.put("current_location", "")
                return GrindrPlus.showToast(Toast.LENGTH_LONG, "Teleportation disabled")
            }
            return GrindrPlus.showToast(Toast.LENGTH_LONG, "Please provide a location")
        }

        // Handle different location formats
        when {
            args.size == 1 && args[0] == "off" -> {
                Config.put("current_location", "")
                return GrindrPlus.showToast(Toast.LENGTH_LONG, "Teleportation disabled")
            }
            args.size == 1 && args[0].contains(",") -> {
                val parts = args[0].split(",")
                val lat = parts[0].trim().toDoubleOrNull()
                val lon = parts.getOrNull(1)?.trim()?.toDoubleOrNull()

                if (lat != null && lon != null) {
                    teleportToCoordinates(lat, lon)
                } else {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, "Invalid coordinates format")
                }
            }
            args.size == 2 && args.all { arg -> arg.toDoubleOrNull() != null } -> {
                val lat = args[0].toDouble()
                val lon = args[1].toDouble()
                teleportToCoordinates(lat, lon)
            }
            else -> {
                // Location name - resolve via saved locations or API
                coroutineScope.launch {
                    val location = getLocation(args.joinToString(" "))
                    if (location != null) {
                        teleportToCoordinates(location.first, location.second)
                    } else {
                        val apiLocation = getLocationFromNominatimAsync(args.joinToString(" "))
                        if (apiLocation != null) {
                            teleportToCoordinates(apiLocation.first, apiLocation.second)
                        } else {
                            GrindrPlus.showToast(Toast.LENGTH_LONG, "Location not found")
                        }
                    }
                }
            }
        }
    }

    @Command(name = "tpS", aliases = ["tps"], help = "Teleport to a location and periodically resend coordinates (default 90s, customize: /tpS lat,lon 60)")
    fun teleportStatic(args: List<String>) {
        // Check if forced coordinates are enabled
        if (Config.get("forced_coordinates", "") as String != "") {
            GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                AlertDialog.Builder(activity)
                    .setTitle("Static Teleportation disabled")
                    .setMessage(
                        "GrindrPlus is currently using forced coordinates. " +
                                "Please disable it to use static teleportation."
                    )
                    .setPositiveButton("OK", null)
                    .setNegativeButton("Disable") { _, _ ->
                        Config.put("forced_coordinates", "")
                        GrindrPlus.bridgeClient.deleteForcedLocation(packageName)
                        GrindrPlus.showToast(Toast.LENGTH_LONG, "Forced coordinates disabled")
                    }
                    .show()
            }
            return
        }

        // Parse arguments
        var locationString: String? = null
        var intervalMs = DEFAULT_INTERVAL_MS
        val locationArgs = mutableListOf<String>()

        // Check if last argument is an interval
        val potentialInterval = args.lastOrNull()?.toLongOrNull()
        if (potentialInterval != null && args.size > 1) {
            val restOfArgs = args.dropLast(1).joinToString(" ")
            if (!restOfArgs.equals("off", ignoreCase = true)) {
                intervalMs = potentialInterval * 1000
                locationArgs.addAll(args.dropLast(1))
            } else {
                locationArgs.addAll(args)
            }
        } else {
            locationArgs.addAll(args)
        }

        val argsJoined = locationArgs.joinToString(" ")

        when {
            // No arguments - toggle current state
            args.isEmpty() -> {
                val isRunning = scheduler.isTaskRunning(STATIC_TELEPORT_TASK)
                if (isRunning) {
                    scheduler.cancelTask(STATIC_TELEPORT_TASK)
                    Config.put(STATIC_LOCATION_KEY, "")
                    GrindrPlus.showToast(Toast.LENGTH_LONG, "Static Teleportation disabled")
                } else {
                    val savedCoords = Config.get(STATIC_LOCATION_KEY, "") as String
                    if (savedCoords.isNotEmpty()) {
                        val savedInterval = Config.get(STATIC_INTERVAL_KEY, DEFAULT_INTERVAL_MS) as Long
                        startStaticTeleportTask(savedCoords, savedInterval)
                        GrindrPlus.showToast(
                            Toast.LENGTH_LONG,
                            "Static Teleportation re-enabled (${savedInterval / 1000}s)"
                        )
                    } else {
                        GrindrPlus.showToast(
                            Toast.LENGTH_LONG,
                            "Please provide a location (e.g., /tpS 37.7749,-122.4194 60)"
                        )
                    }
                }
            }
            // Turn off static teleportation
            argsJoined.equals("off", ignoreCase = true) -> {
                scheduler.cancelTask(STATIC_TELEPORT_TASK)
                Config.put(STATIC_LOCATION_KEY, "")
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Static Teleportation disabled")
            }
            // "lat,lon" format
            locationArgs.size == 1 && locationArgs[0].contains(",") -> {
                val parts = locationArgs[0].split(",")
                val lat = parts[0].trim().toDoubleOrNull()
                val lon = parts.getOrNull(1)?.trim()?.toDoubleOrNull()

                if (lat != null && lon != null) {
                    locationString = "$lat,$lon"
                } else {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, "Invalid coordinates format")
                    return
                }
            }
            // "lat lon" format
            locationArgs.size == 2 && locationArgs.all { it.toDoubleOrNull() != null } -> {
                val lat = locationArgs[0].toDouble()
                val lon = locationArgs[1].toDouble()
                locationString = "$lat,$lon"
            }
            // Location name - needs resolution
            else -> {
                coroutineScope.launch {
                    val resolvedLocation = getLocation(argsJoined)
                        ?: getLocationFromNominatimAsync(argsJoined)

                    if (resolvedLocation != null) {
                        val coords = "${resolvedLocation.first},${resolvedLocation.second}"
                        Config.put(STATIC_LOCATION_KEY, coords)
                        Config.put(STATIC_INTERVAL_KEY, intervalMs)
                        startStaticTeleportTask(coords, intervalMs)
                        GrindrPlus.showToast(
                            Toast.LENGTH_LONG,
                            "Static Teleported to $coords (${intervalMs / 1000}s)"
                        )
                    } else {
                        GrindrPlus.showToast(Toast.LENGTH_LONG, "Location not found")
                    }
                }
                return
            }
        }

        // Save and start static teleport for coordinate formats
        if (locationString != null) {
            Config.put(STATIC_LOCATION_KEY, locationString)
            Config.put(STATIC_INTERVAL_KEY, intervalMs)
            startStaticTeleportTask(locationString, intervalMs)
            GrindrPlus.showToast(
                Toast.LENGTH_LONG,
                "Static Teleported to $locationString (${intervalMs / 1000}s)"
            )
        }
    }

    @Command(name = "save", aliases = ["sv"], help = "Save the current location")
    fun save(args: List<String>) {
        if (args.isEmpty()) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Please provide a name for the location")
            return
        }

        val name = args[0]

        coroutineScope.launch {
            val location = when {
                args.size == 1 -> Config.get("current_location", "") as String
                args.size == 2 && args[1].contains(",") -> args[1]
                args.size == 3 && args[1].toDoubleOrNull() != null && args[2].toDoubleOrNull() != null ->
                    "${args[1]},${args[2]}"
                args.size > 1 ->
                    getLocationFromNominatimAsync(args.drop(1).joinToString(" "))?.let {
                        "${it.first},${it.second}"
                    }
                else -> ""
            }

            if (!location.isNullOrEmpty()) {
                val coordinates = location.split(",")
                if (coordinates.size == 2) {
                    try {
                        val lat = coordinates[0].trim().toDouble()
                        val lon = coordinates[1].trim().toDouble()

                        val existingLocation = getLocation(name)
                        if (existingLocation != null) {
                            updateLocation(name, lat, lon)
                            GrindrPlus.showToast(Toast.LENGTH_LONG, "Successfully updated $name")
                        } else {
                            addLocation(name, lat, lon)
                            GrindrPlus.showToast(Toast.LENGTH_LONG, "Successfully saved $name")
                        }
                    } catch (e: Exception) {
                        GrindrPlus.showToast(Toast.LENGTH_LONG, "Invalid coordinates format")
                    }
                } else {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, "Invalid coordinates format")
                }
            } else {
                GrindrPlus.showToast(Toast.LENGTH_LONG, "No location provided")
            }
        }
    }

    @Command(name = "delete", aliases = ["del"], help = "Delete a saved location")
    fun delete(args: List<String>) {
        if (args.isEmpty()) {
            return GrindrPlus.showToast(Toast.LENGTH_LONG, "Please provide a location to delete")
        }

        val name = args.joinToString(" ")

        coroutineScope.launch {
            val location = getLocation(name)
            if (location == null) {
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Location not found")
                return@launch
            }

            deleteLocation(name)
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Location deleted")
        }
    }

    private suspend fun getLocation(name: String): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            val locationDao = GrindrPlus.database.teleportLocationDao()
            val entity = locationDao.getLocation(name)
            Timber.d("Location Retrieved")
            return@withContext entity?.let { Pair(it.latitude, it.longitude) }
        }

    private suspend fun addLocation(name: String, latitude: Double, longitude: Double) =
        withContext(Dispatchers.IO) {
            val locationDao = GrindrPlus.database.teleportLocationDao()
            val entity = com.grindrplus.persistence.model.TeleportLocationEntity(
                name = name,
                latitude = latitude,
                longitude = longitude
            )
            locationDao.upsertLocation(entity)
        }

    private suspend fun updateLocation(name: String, latitude: Double, longitude: Double) =
        withContext(Dispatchers.IO) {
            val locationDao = GrindrPlus.database.teleportLocationDao()
            val entity = com.grindrplus.persistence.model.TeleportLocationEntity(
                name = name,
                latitude = latitude,
                longitude = longitude
            )
            locationDao.upsertLocation(entity)
        }

    private suspend fun deleteLocation(name: String) =
        withContext(Dispatchers.IO) {
            val locationDao = GrindrPlus.database.teleportLocationDao()
            locationDao.deleteLocation(name)
        }

    private suspend fun getLocationFromNominatimAsync(location: String): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            val url = "https://nominatim.openstreetmap.org/search?q=${Uri.encode(location)}&format=json"
            val randomUserAgent = getUserAgent()
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", randomUserAgent)
                .build()

            return@withContext try {
                OkHttpClient().newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (body.isNullOrEmpty()) return@withContext null

                    val json = JSONArray(body)
                    if (json.length() == 0) return@withContext null

                    val obj = json.getJSONObject(0)
                    val lat = obj.getDouble("lat")
                    val lon = obj.getDouble("lon")
                    Pair(lat, lon)
                }
            } catch (e: Exception) {
                val message = "Error getting location: ${e.message ?: "Unknown error"}"
                withContext(Dispatchers.Main) {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, message)
                }
                Logger.apply {
                    e(message)
                    writeRaw(e.stackTraceToString())
                }
                null
            }
        }

    private suspend fun getNearestLocationNameFromCoords(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&addressdetails=1"
            val randomUserAgent = getUserAgent()
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", randomUserAgent)
                .build()

            return@withContext try {
                OkHttpClient().newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (body.isNullOrEmpty()) return@withContext null

                    val json = JSONObject(body)
                    if (json.has("error")) return@withContext null

                    // Extract the display name or specific address components
                    val displayName = json.optString("display_name", null)
                    val address = json.optJSONObject("address")

                    // Try to get specific meaningful names in priority order
                    val locationName = when {
                        address?.optString("village") != null -> address.getString("village")
                        address?.optString("town") != null -> address.getString("town")
                        address?.optString("city") != null -> address.getString("city")
                        address?.optString("municipality") != null -> address.getString("municipality")
                        address?.optString("county") != null -> address.getString("county")
                        address?.optString("state") != null -> address.getString("state")
                        address?.optString("country") != null -> address.getString("country")
                        !displayName.isNullOrEmpty() -> displayName
                        else -> null
                    }

                    locationName
                }
            } catch (e: Exception) {
                val message = "Error getting location name: ${e.message ?: "Unknown error"}"
                withContext(Dispatchers.Main) {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, message)
                }
                Logger.apply {
                    e(message)
                    writeRaw(e.stackTraceToString())
                }
                null
            }
        }

    /*private suspend fun storeLocationName(lat: Double, lon: Double) {
        val locationName = getNearestLocationNameFromCoords(lat, lon)
        locationName?.let { name ->
            // Store the name wherever you need it
            // For example: shared preferences, database, variable, etc.
            preferences.edit().putString("last_location_name", name).apply()
            println("Nearest location: $name")
        }
    }*/

    private fun teleportToCoordinates(lat: Double, lon: Double, silent: Boolean = false) {
        Config.put("current_location", "$lat,$lon")
        val geohash = coordsToGeoHash(lat, lon)

        GrindrPlus.executeAsync {
            try {
                GrindrPlus.httpClient.updateLocation(geohash)
            } catch (e: Exception) {
                Logger.e("Error sending geohash to API: ${e.message}")

            }
        }

        // Launch coroutine to get and log location name
        coroutineScope.launch {
            val locationName = getNearestLocationNameFromCoords(lat, lon)
            if (locationName != null) {
                Logger.i("Teleported to $lat, $lon - Nearest location: $locationName")
                Timber.v("Teleportated to $locationName")
            } else {
                Logger.i("Teleported to $lat, $lon - Location name not available")
            }
        }

        if (!silent) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Teleported to $lat, $lon")
            Logger.i("Teleported to $lat, $lon")
        }
    }
    private fun getUserAgent(): String {
        val chromeVersions = listOf("120.0.0.0", "119.0.0.0", "118.0.0.0", "117.0.0.0", "116.0.0.0")
        val firefoxVersions = listOf("121.0", "120.0", "119.0", "118.0", "117.0")
        val safariVersions = listOf("17.2", "17.1", "17.0", "16.6", "16.5")
        val edgeVersions = listOf("120.0.0.0", "119.0.0.0", "118.0.0.0", "117.0.0.0")
        val windowsVersions = listOf("10.0", "11.0")
        val macVersions = listOf("10_15_7", "11_7_10", "12_7_2", "13_6_3", "14_2_1")

        return when ((1..5).random()) {
            1 -> {
                val version = chromeVersions.random()
                val winVer = windowsVersions.random()
                "Mozilla/5.0 (Windows NT $winVer; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$version Safari/537.36"
            }
            2 -> {
                val version = chromeVersions.random()
                val macVer = macVersions.random()
                "Mozilla/5.0 (Macintosh; Intel Mac OS X $macVer) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$version Safari/537.36"
            }
            3 -> {
                val version = firefoxVersions.random()
                val platform = if ((1..2).random() == 1) {
                    "Windows NT ${windowsVersions.random()}; Win64; x64"
                } else {
                    "Macintosh; Intel Mac OS X ${macVersions.random().replace("_", ".")}"
                }
                "Mozilla/5.0 ($platform; rv:$version) Gecko/20100101 Firefox/$version"
            }
            4 -> {
                val safariVer = safariVersions.random()
                val macVer = macVersions.random()
                val webkitVer = "605.1.${(10..20).random()}"
                "Mozilla/5.0 (Macintosh; Intel Mac OS X $macVer) AppleWebKit/$webkitVer (KHTML, like Gecko) Version/$safariVer Safari/$webkitVer"
            }
            5 -> {
                val version = edgeVersions.random()
                val winVer = windowsVersions.random()
                "Mozilla/5.0 (Windows NT $winVer; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$version Safari/537.36 Edg/$version"
            }
            else -> chromeVersions.random()
        }
    }
}