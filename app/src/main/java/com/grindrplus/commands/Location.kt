package com.grindrplus.commands

import android.app.AlertDialog
import android.net.Uri
import android.widget.Toast
import com.grindrplus.GrindrPlus
import com.grindrplus.GrindrPlus.packageName
import com.grindrplus.core.Config
import com.grindrplus.core.Logger
import com.grindrplus.core.TaskScheduler // ADDED: Import TaskScheduler
import com.grindrplus.core.Utils.coordsToGeoHash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray

class Location(recipient: String, sender: String) : CommandModule("Location", recipient, sender) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val scheduler = TaskScheduler(coroutineScope) // ADDED: Task scheduler instance

    // ADDED: Constants for the static teleport feature
    companion object {
        private const val STATIC_TELEPORT_TASK = "static_teleport"
        // Default 90 seconds in milliseconds
        private const val DEFAULT_INTERVAL_MS = 90L * 1000
        private const val STATIC_LOCATION_KEY = "static_location_coords"
        private const val STATIC_INTERVAL_KEY = "static_location_interval"
    }

    init {
        // ADDED: Restart static teleport on module initialization (app startup)
        val savedLocation = Config.get(STATIC_LOCATION_KEY, "") as String
        if (savedLocation.isNotEmpty()) {
            val interval = Config.get(STATIC_INTERVAL_KEY, DEFAULT_INTERVAL_MS) as Long
            // FIX: startStaticTeleportTask must safely handle potential exceptions from invalid saved data
            startStaticTeleportTask(savedLocation, interval)
            Logger.i("Static Teleportation task re-started for $savedLocation every ${interval / 1000}s")
        }
    }

    /**
     * Helper function to manage the periodic static teleport task.
     * FIX: Added safe coordinate parsing to prevent synchronous crash on module initialization.
     */
    private fun startStaticTeleportTask(coords: String, interval: Long) {
        // Cancel any existing task first
        scheduler.cancelTask(STATIC_TELEPORT_TASK)

        // Split and convert coordinates safely
        val parts = coords.split(",")
        if (parts.size != 2) {
            Logger.e("Invalid format stored for static teleport: $coords. Clearing saved location.")
            Config.put(STATIC_LOCATION_KEY, "") // Clear invalid saved location
            return
        }

        val lat = parts[0].toDoubleOrNull()
        val lon = parts[1].toDoubleOrNull()

        if (lat == null || lon == null) {
            Logger.e("Invalid coordinate values stored for static teleport: $coords. Clearing saved location.")
            Config.put(STATIC_LOCATION_KEY, "") // Clear invalid saved location
            return
        }

        // Start the periodic task (using TaskScheduler from TaskScheduler.kt)
        scheduler.periodic(STATIC_TELEPORT_TASK, interval) {
            // Use silent teleportation for the periodic task
            teleportToCoordinates(lat, lon, silent = true)
            Logger.i("Periodic static teleport to $lat, $lon")
        }
    }


    @Command(name = "tp", aliases = ["tp"], help = "Teleport to a location")
    fun teleport(args: List<String>) {
        /**
         * If the user is currently used forced coordinates, don't allow teleportation.
         */
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
                        GrindrPlus.showToast(
                            Toast.LENGTH_LONG,
                            "Forced coordinates disabled"
                        )
                    }
                    .show()
            }

            return;
        }

        /**
         * This command is also used to toggle the teleportation feature. If the user hasn't
         * provided any arguments, just toggle teleport.
         */
        if (args.isEmpty()) {
            val status = (Config.get("current_location", "") as String).isEmpty()
            if (!status) {
                Config.put("current_location", "")
                return GrindrPlus.showToast(Toast.LENGTH_LONG, "Teleportation disabled")
            }

            return GrindrPlus.showToast(Toast.LENGTH_LONG, "Please provide a location")
        }

        /**
         * If the user has provided arguments, try to teleport to the location. We currently support
         * different formats for the location:
         * - "lat, lon" (e.g. "37.7749, -122.4194") for latitude and longitude.
         * - "lat" "lon" (e.g. "37.7749" "-122.4194") for latitude and longitude.
         * - "lat lon" (e.g. "37.7749 -122.4194") for latitude and longitude.
         * - "city, country" (e.g. "San Francisco, United States") for city and country.
         */
        when {
            args.size == 1 && args[0] == "off" -> {
                Config.put("current_location", "")
                return GrindrPlus.showToast(Toast.LENGTH_LONG, "Teleportation disabled")
            }
            args.size == 1 && args[0].contains(",") -> {
                // Original logic is retained for /tp
                val parts = args[0].split(",")
                val lat = parts[0].toDoubleOrNull()
                val lon = parts[1].toDoubleOrNull()

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
                /**
                 * If we reached this point, the user has provided a name of a city. In this case,
                 * it could be either a saved location or an actual city.
                 */
                coroutineScope.launch {
                    val location = getLocation(args.joinToString(" "))
                    if (location != null) {
                        teleportToCoordinates(location.first, location.second)
                    } else {
                        /**
                         * No valid saved location was found, try to get the actual location. This
                         * is done by using Nominatim's API to get the latitude and longitude.
                         */
                        val apiLocation = getLocationFromNominatimAsync(args.joinToString(" "))
                        if (apiLocation != null) {
                            teleportToCoordinates(apiLocation.first, apiLocation.second)
                        } else {
                            GrindrPlus.showToast(Toast.LENGTH_LONG, "Location not found")
                        }
                    }
                }
                return
            }
        }
    }

    // ADDED: New command /tpS for static/periodic teleportation.
    @Command(name = "tpS", aliases = ["tps"], help = "Teleport to a location and periodically resend the coordinates (90s default, or customize with an argument in seconds: /tpS 12.3,4.5 60)")
    fun teleportStatic(args: List<String>) {
        /**
         * If the user is currently used forced coordinates, don't allow static teleportation.
         */
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
                        GrindrPlus.showToast(
                            Toast.LENGTH_LONG,
                            "Forced coordinates disabled"
                        )
                    }
                    .show()
            }
            return
        }

        // --- Argument Parsing ---
        var locationString: String? = null
        var intervalMs = DEFAULT_INTERVAL_MS
        val locationArgs = mutableListOf<String>()

        // Determine if the last argument is a customizable interval in seconds
        val potentialIntervalSeconds = args.lastOrNull()?.toLongOrNull()
        if (potentialIntervalSeconds != null && args.size > 1) {
            // Check if the argument before the potential interval is not 'off'
            if (!args.dropLast(1).joinToString(" ").equals("off", ignoreCase = true)) {
                intervalMs = potentialIntervalSeconds * 1000
                locationArgs.addAll(args.dropLast(1))
            } else {
                locationArgs.addAll(args)
            }
        } else {
            locationArgs.addAll(args)
        }

        val argsJoined = locationArgs.joinToString(" ")

        when {
            args.isEmpty() -> {
                // Toggle logic: If no arguments, toggle the current state.
                val isRunning = scheduler.isTaskRunning(STATIC_TELEPORT_TASK)
                if (isRunning) {
                    scheduler.cancelTask(STATIC_TELEPORT_TASK)
                    Config.put(STATIC_LOCATION_KEY, "") // Clear saved location
                    return GrindrPlus.showToast(Toast.LENGTH_LONG, "Static Teleportation disabled")
                } else {
                    // If not running, attempt to re-enable with last saved location
                    val savedCoords = Config.get(STATIC_LOCATION_KEY, "") as String
                    if (savedCoords.isNotEmpty()) {
                        val savedInterval = Config.get(STATIC_INTERVAL_KEY, DEFAULT_INTERVAL_MS) as Long
                        startStaticTeleportTask(savedCoords, savedInterval)
                        return GrindrPlus.showToast(
                            Toast.LENGTH_LONG,
                            "Static Teleportation re-enabled to $savedCoords (interval: ${savedInterval / 1000}s)"
                        )
                    } else {
                        return GrindrPlus.showToast(Toast.LENGTH_LONG, "Please provide a location and optionally an interval (e.g., /tpS 37.7749,-122.4194 60) or use /tpS off")
                    }
                }
            }
            // Check for /tpS off
            argsJoined.equals("off", ignoreCase = true) -> {
                scheduler.cancelTask(STATIC_TELEPORT_TASK)
                Config.put(STATIC_LOCATION_KEY, "")
                return GrindrPlus.showToast(Toast.LENGTH_LONG, "Static Teleportation disabled")
            }
            // Check for "lat,lon" format
            locationArgs.size == 1 && locationArgs[0].contains(",") -> {
                val parts = locationArgs[0].split(",")
                val lat = parts[0].toDoubleOrNull()
                val lon = parts.getOrNull(1)?.toDoubleOrNull() // Safely get second part

                if (lat != null && lon != null) {
                    locationString = "$lat,$lon"
                } else {
                    return GrindrPlus.showToast(Toast.LENGTH_LONG, "Invalid coordinates format: ${locationArgs[0]}")
                }
            }
            // Check for "lat lon" format
            locationArgs.size == 2 && locationArgs.all { arg -> arg.toDoubleOrNull() != null } -> {
                val lat = locationArgs[0].toDouble() // Safe to use toDouble() due to pre-check
                val lon = locationArgs[1].toDouble()
                locationString = "$lat,$lon"
            }
            // If it's not coordinates, it's a name that needs resolution
            else -> {
                coroutineScope.launch {
                    val resolvedLocation = getLocation(argsJoined)
                    val apiLocation = resolvedLocation ?: getLocationFromNominatimAsync(argsJoined)

                    if (apiLocation != null) {
                        val (lat, lon) = apiLocation
                        val coords = "$lat,$lon"
                        Config.put(STATIC_LOCATION_KEY, coords)
                        Config.put(STATIC_INTERVAL_KEY, intervalMs)
                        startStaticTeleportTask(coords, intervalMs)
                        GrindrPlus.showToast(
                            Toast.LENGTH_LONG,
                            "Static Teleported to $coords (interval: ${intervalMs / 1000}s)"
                        )
                    } else {
                        GrindrPlus.showToast(Toast.LENGTH_LONG, "Location not found")
                    }
                }
                return
            }
        }

        // Handle the case where coordinates were parsed directly (not a location name)
        if (locationString != null) {
            Config.put(STATIC_LOCATION_KEY, locationString)
            Config.put(STATIC_INTERVAL_KEY, intervalMs)
            startStaticTeleportTask(locationString, intervalMs)
            return GrindrPlus.showToast(
                Toast.LENGTH_LONG,
                "Static Teleported to $locationString (interval: ${intervalMs / 1000}s)"
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
            val location =
                when {
                    args.size == 1 -> Config.get("current_location", "") as String
                    args.size == 2 && args[1].contains(",") -> args[1]
                    args.size == 3 &&
                            args[1].toDoubleOrNull() != null &&
                            args[2].toDoubleOrNull() != null -> "${args[1]},${args[2]}"
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
                        val lat = coordinates[0].toDouble()
                        val lon = coordinates[1].toDouble()

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
            return@withContext entity?.let { Pair(it.latitude, it.longitude) }
        }

    private suspend fun addLocation(name: String, latitude: Double, longitude: Double) =
        withContext(Dispatchers.IO) {
            val locationDao = GrindrPlus.database.teleportLocationDao()
            val entity =
                com.grindrplus.persistence.model.TeleportLocationEntity(
                    name = name,
                    latitude = latitude,
                    longitude = longitude
                )
            locationDao.upsertLocation(entity)
        }

    private suspend fun updateLocation(name: String, latitude: Double, longitude: Double) =
        withContext(Dispatchers.IO) {
            val locationDao = GrindrPlus.database.teleportLocationDao()
            val entity =
                com.grindrplus.persistence.model.TeleportLocationEntity(
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
            val url =
                "https://nominatim.openstreetmap.org/search?q=${Uri.encode(location)}&format=json"

            val randomUserAgent = getUserAgent()
            val request =
                okhttp3.Request.Builder().url(url).header("User-Agent", randomUserAgent).build()

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
                val message =
                    "An error occurred while getting the location: ${e.message ?: "Unknown error"}"
                withContext(Dispatchers.Main) { GrindrPlus.showToast(Toast.LENGTH_LONG, message) }
                Logger.apply {
                    e(message)
                    writeRaw(e.stackTraceToString())
                }
                null
            }
        }

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

        if (!silent)
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Teleported to $lat, $lon")
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
