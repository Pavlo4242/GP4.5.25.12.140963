package com.grindrplus.persistence.converters

import androidx.room.TypeConverter
import org.json.JSONArray
import kotlin.collections.map
import kotlin.ranges.until

class ListConverter {
    @TypeConverter
    fun fromString(value: String): List<String> {
        val array = JSONArray(value)
        return (0 until array.length()).map { array.getString(it) }
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        return JSONArray(list).toString()
    }
}