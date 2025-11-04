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
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

class Location(recipient: String, sender: String) :
    CommandModule("Location", recipient, sender) {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val scheduler = TaskScheduler(coroutineScope)

    companion object {
        private const val STATIC_TELEPORT_TASK = "static_teleport"
        private const val DEFAULT_INTERVAL_MS = 90L * 1000
        private const val STATIC_LOCATION_KEY = "static_location_coords"
        private const val STATIC_INTERVAL_KEY = "static_location_interval"
    }

    init {
        coroutineScope.launch {
            try {
                val saved = Config.get(STATIC_LOCATION_KEY, "") as String
                if (saved.isNotEmpty()) {
                    val interval = Config.get(STATIC_INTERVAL_KEY, DEFAULT_INTERVAL_MS) as Long
                    startStaticTeleportTask(saved, interval)
                    Logger.i("Static teleport restored: $saved every ${interval / 1000}s")
                }
            } catch (e: Exception) {
                Logger.e("Failed to restore static teleport: ${e.message}")
                Config.put(STATIC_LOCATION_KEY, "")
            }
        }
    }

    @Command(name = "tp", aliases = ["tp"], help = "Teleport to a location")
    fun teleport(args: List<String>) = teleportCommand(Mode.NORMAL, args)

    @Command(
        name = "tpS",
        aliases = ["tps"],
        help = "Static teleport (re-send every N seconds)"
    )
    fun teleportStatic(args: List<String>) = teleportCommand(Mode.STATIC, args)

    private enum class Mode { NORMAL, STATIC }

    private fun teleportCommand(mode: Mode, rawArgs: List<String>) {
        // ---------- 1. forced-coordinates guard ----------
        if (Config.get("forced_coordinates", "") as String != "") {
            GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                AlertDialog.Builder(activity)
                    .setTitle("${if (mode == Mode.STATIC) "Static " else ""}Teleport disabled")
                    .setMessage("Forced coordinates are active – disable them first.")
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

        // ---------- 2. parse interval (only for STATIC) ----------
        var intervalMs = if (mode == Mode.STATIC) DEFAULT_INTERVAL_MS else 0L
        val args = rawArgs.toMutableList()

        if (mode == Mode.STATIC && args.size > 1) {
            args.last().toLongOrNull()?.let {
                intervalMs = it * 1000
                args.removeAt(args.size - 1)
            }
        }

        val argsJoined = args.joinToString(" ")

        // ---------- 3. "off" / empty-arg handling ----------
        when {
            argsJoined.equals("off", ignoreCase = true) -> {
                if (mode == Mode.STATIC) {
                    scheduler.cancelTask(STATIC_TELEPORT_TASK)
                    Config.put(STATIC_LOCATION_KEY, "")
                    LocationCache.clear()
                    GrindrPlus.showToast(Toast.LENGTH_LONG, "Static teleport disabled")
                } else {
                    Config.put("current_location", "")
                    GrindrPlus.showToast(Toast.LENGTH_LONG, "Teleport disabled")
                }
                return
            }

            args.isEmpty() -> {
                if (mode == Mode.STATIC) {
                    val running = scheduler.isTaskRunning(STATIC_TELEPORT_TASK)
                    if (running) {
                        scheduler.cancelTask(STATIC_TELEPORT_TASK)
                        Config.put(STATIC_LOCATION_KEY, "")
                        LocationCache.clear()
                        GrindrPlus.showToast(Toast.LENGTH_LONG, "Static teleport disabled")
                    } else {
                        val saved = Config.get(STATIC_LOCATION_KEY, "") as String
                        if (saved.isNotEmpty()) {
                            val savedInt = Config.get(STATIC_INTERVAL_KEY, DEFAULT_INTERVAL_MS) as Long
                            startStaticTeleportTask(saved, savedInt)
                            GrindrPlus.showToast(
                                Toast.LENGTH_LONG,
                                "Static teleport re-enabled (${savedInt / 1000}s)"
                            )
                        } else {
                            GrindrPlus.showToast(Toast.LENGTH_LONG, "Use /tpS <location> [seconds]")
                        }
                    }
                } else {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, "Use /tp <location> or /tp off")
                }
                return
            }
        }

        // ---------- 4. direct coordinates ----------
        val directCoords: Pair<Double, Double>? = when {
            args.size == 1 && args[0].contains(",") -> {
                val p = args[0].split(",")
                val lat = p[0].trim().toDoubleOrNull()
                val lon = p.getOrNull(1)?.trim()?.toDoubleOrNull()
                if (lat != null && lon != null) lat to lon else null
            }

            args.size == 2 && args.all { it.toDoubleOrNull() != null } -> {
                args[0].toDouble() to args[1].toDouble()
            }

            else -> null
        }

        if (directCoords != null) {
            val (lat, lon) = directCoords
            if (mode == Mode.STATIC) {
                val coordStr = "$lat,$lon"
                Config.put(STATIC_LOCATION_KEY, coordStr)
                Config.put(STATIC_INTERVAL_KEY, intervalMs)
                startStaticTeleportTask(coordStr, intervalMs)
                GrindrPlus.showToast(
                    Toast.LENGTH_LONG,
                    "Static teleport to $lat,$lon (${intervalMs / 1000}s)"
                )
            } else {
                teleportToCoordinates(lat, lon)
            }
            return
        }

        // ---------- 5. resolve name → coordinates ----------
        coroutineScope.launch {
            val resolved = getLocation(argsJoined) ?: getLocationFromNominatimAsync(argsJoined)
            if (resolved == null) {
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Location not found")
                return@launch
            }

            val (lat, lon) = resolved
            val coordStr = "$lat,$lon"

            if (mode == Mode.STATIC) {
                Config.put(STATIC_LOCATION_KEY, coordStr)
                Config.put(STATIC_INTERVAL_KEY, intervalMs)
                startStaticTeleportTask(coordStr, intervalMs)
                GrindrPlus.showToast(
                    Toast.LENGTH_LONG,
                    "Static teleport to $coordStr (${intervalMs / 1000}s)"
                )
            } else {
                teleportToCoordinates(lat, lon)
            }
        }
    }

    private fun startStaticTeleportTask(coordStr: String, interval: Long) {
        scheduler.cancelTask(STATIC_TELEPORT_TASK)

        val parts = coordStr.split(",")
        if (parts.size != 2) {
            Logger.e("Bad static coord format: $coordStr")
            Config.put(STATIC_LOCATION_KEY, "")
            return
        }

        val lat = parts[0].trim().toDoubleOrNull()
        val lon = parts[1].trim().toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            Logger.e("Invalid static coordinates: $coordStr")
            Config.put(STATIC_LOCATION_KEY, "")
            return
        }

        scheduler.periodic(STATIC_TELEPORT_TASK, interval) {
            try {
                teleportToCoordinates(lat, lon, silent = true)
                Logger.i("Static teleport tick → $lat,$lon")
            } catch (e: Exception) {
                Logger.e("Static teleport tick failed: ${e.message}")
            }
        }
    }

    @Command(name = "save", aliases = ["sv"], help = "Save the current location")
    fun save(args: List<String>) {
        if (args.isEmpty()) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Provide a name")
            return
        }
        val name = args[0]

        coroutineScope.launch {
            val coordStr = when {
                args.size == 1 -> Config.get("current_location", "") as String
                args.size == 2 && args[1].contains(",") -> args[1]
                args.size == 3 && args[1].toDoubleOrNull() != null && args[2].toDoubleOrNull() != null ->
                    "${args[1]},${args[2]}"

                args.size > 1 -> getLocationFromNominatimAsync(args.drop(1).joinToString(" "))
                    ?.let { "${it.first},${it.second}" }

                else -> ""
            }

            if (coordStr.isNullOrBlank() || !coordStr.contains(",")) {
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Invalid coordinates")
                return@launch
            }

            val (lat, lon) = coordStr.split(",").let {
                it[0].trim().toDouble() to it[1].trim().toDouble()
            }

            if (getLocation(name) != null) {
                updateLocation(name, lat, lon)
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Updated $name")
            } else {
                addLocation(name, lat, lon)
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Saved $name")
            }
        }
    }

    @Command(name = "delete", aliases = ["del"], help = "Delete a saved location")
    fun delete(args: List<String>) {
        if (args.isEmpty()) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Provide a name")
            return
        }
        val name = args.joinToString(" ")
        coroutineScope.launch {
            if (getLocation(name) == null) {
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Location not found")
                return@launch
            }
            deleteLocation(name)
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Deleted $name")
        }
    }

    private suspend fun getLocation(name: String): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            val dao = GrindrPlus.database.teleportLocationDao()
            dao.getLocation(name)?.let { Pair(it.latitude, it.longitude) }
        }

    private suspend fun addLocation(name: String, lat: Double, lon: Double) =
        withContext(Dispatchers.IO) {
            val dao = GrindrPlus.database.teleportLocationDao()
            dao.upsertLocation(
                com.grindrplus.persistence.model.TeleportLocationEntity(
                    name = name, latitude = lat, longitude = lon
                )
            )
        }

    private suspend fun updateLocation(name: String, lat: Double, lon: Double) =
        withContext(Dispatchers.IO) {
            addLocation(name, lat, lon)
        }

    private suspend fun deleteLocation(name: String) = withContext(Dispatchers.IO) {
        GrindrPlus.database.teleportLocationDao().deleteLocation(name)
    }

    private suspend fun getLocationFromNominatimAsync(location: String): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            val url = "https://nominatim.openstreetmap.org/search?q=${Uri.encode(location)}&format=json"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build()

            try {
                OkHttpClient().newCall(request).execute().use { resp ->
                    val body = resp.body?.string() ?: return@withContext null
                    val json = JSONArray(body)
                    if (json.length() == 0) return@withContext null
                    val obj = json.getJSONObject(0)
                    Pair(obj.getDouble("lat"), obj.getDouble("lon"))
                }
            } catch (e: Exception) {
                val msg = "Nominatim error: ${e.message}"
                withContext(Dispatchers.Main) {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, msg)
                }
                Logger.e(msg)
                null
            }
        }

    private suspend fun getNearestLocationNameFromCoords(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&addressdetails=1"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build()

            try {
                OkHttpClient().newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string() ?: return@use null
                    val json = JSONObject(body)
                    if (json.has("error")) return@use null

                    val address = json.optJSONObject("address")
                    val display = json.optString("display_name").takeIf { it.isNotBlank() }

                    fun String?.nonEmpty() = this?.takeIf { it.isNotBlank() }

                    val name = when {
                        address?.optString("village").nonEmpty() != null -> address.optString("village")
                        address?.optString("town").nonEmpty() != null -> address.optString("town")
                        address?.optString("city").nonEmpty() != null -> address.optString("city")
                        address?.optString("municipality").nonEmpty() != null -> address.optString("municipality")
                        address?.optString("county").nonEmpty() != null -> address.optString("county")
                        address?.optString("state").nonEmpty() != null -> address.optString("state")
                        address?.optString("country").nonEmpty() != null -> address.optString("country")
                        display != null -> display
                        else -> null
                    }

                    name?.also { Logger.i("Resolved name: $it") } ?: Logger.w("No name")
                    name
                }
            } catch (e: Exception) {
                val msg = "Reverse-geocode error: ${e.message}"
                withContext(Dispatchers.Main) {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, msg)
                }
                Logger.e(msg)
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
                Logger.e("Geohash send failed: ${e.message}")
            }
        }

        coroutineScope.launch {
            val cached = LocationCache.get(lat, lon)
            if (cached != null) {
                Logger.i("Teleport to $lat,$lon – $cached")
                return@launch
            }

            val name = getNearestLocationNameFromCoords(lat, lon)
            if (name != null) {
                LocationCache.put(lat, lon, name)
                Logger.i("Teleport to $lat,$lon – $name (fresh)")
            } else {
                Logger.i("Teleport to $lat,$lon – name unavailable")
            }
        }

        if (!silent) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Teleported to $lat, $lon")
            Logger.i("Teleported to $lat, $lon")
        }
    }

    private fun getUserAgent(): String {
        val chrome = listOf("120.0.0.0", "119.0.0.0", "118.0.0.0", "117.0.0.0", "116.0.0.0")
        val firefox = listOf("121.0", "120.0", "119.0", "118.0", "117.0")
        val safari = listOf("17.2", "17.1", "17.0", "16.6", "16.5")
        val edge = listOf("120.0.0.0", "119.0.0.0", "118.0.0.0", "117.0.0.0")
        val win = listOf("10.0", "11.0")
        val mac = listOf("10_15_7", "11_7_10", "12_7_2", "13_6_3", "14_2_1")

        return when ((1..5).random()) {
            1 -> "Mozilla/5.0 (Windows NT ${win.random()}; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${chrome.random()} Safari/537.36"
            2 -> "Mozilla/5.0 (Macintosh; Intel Mac OS X ${mac.random()}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${chrome.random()} Safari/537.36"
            3 -> {
                val v = firefox.random()
                val platform = if ((1..2).random() == 1) "Windows NT ${win.random()}; Win64; x64"
                else "Macintosh; Intel Mac OS X ${mac.random().replace("_", ".")}"
                "Mozilla/5.0 ($platform; rv:$v) Gecko/20100101 Firefox/$v"
            }
            4 -> {
                val s = safari.random()
                val m = mac.random()
                val w = "605.1.${(10..20).random()}"
                "Mozilla/5.0 (Macintosh; Intel Mac OS X $m) AppleWebKit/$w (KHTML, like Gecko) Version/$s Safari/$w"
            }
            5 -> "Mozilla/5.0 (Windows NT ${win.random()}; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${edge.random()} Safari/537.36 Edg/${edge.random()}"
            else -> chrome.random()
        }
    }

    object LocationCache {
        private const val KEY_COORDS = "cached_coords"
        private const val KEY_NAME = "cached_name"
        private const val KEY_TS = "cached_ts"

        private val prefs get() = GrindrPlus.context.getSharedPreferences("grindrplus_location_cache", 0)

        fun get(lat: Double, lon: Double, maxAgeMs: Long = 24 * 60 * 60 * 1000): String? {
            val saved = prefs.getString(KEY_COORDS, null) ?: return null
            if (saved != "$lat,$lon") return null
            val ts = prefs.getLong(KEY_TS, 0)
            if ((System.currentTimeMillis() - ts) > maxAgeMs) return null
            return prefs.getString(KEY_NAME, null)
        }

        fun put(lat: Double, lon: Double, name: String) {
            prefs.edit()
                .putString(KEY_COORDS, "$lat,$lon")
                .putString(KEY_NAME, name)
                .putLong(KEY_TS, System.currentTimeMillis())
                .apply()
        }

        fun clear() {
            prefs.edit()
                .remove(KEY_COORDS)
                .remove(KEY_NAME)
                .remove(KEY_TS)
                .apply()
        }
    }
}