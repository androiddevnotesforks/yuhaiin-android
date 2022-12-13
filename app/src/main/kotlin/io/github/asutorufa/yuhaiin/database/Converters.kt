package io.github.asutorufa.yuhaiin.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

object Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromString(value: String?): Set<String> {
        val listType: Type = object : TypeToken<Set<String?>?>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromSet(list: Set<String?>?): String {
        return gson.toJson(list)
    }


    @TypeConverter
    fun fromMapString(value: String?): Map<String, String> {
        val listType: Type = object : TypeToken<Map<String, String>?>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromMap(list: Map<String, String>?): String {
        return gson.toJson(list)
    }

    @TypeConverter
    fun stringToDNS(value: String?): DNS {
        val dnsType: Type = object : TypeToken<DNS?>() {}.type
        return gson.fromJson(value, dnsType)
    }

    @TypeConverter
    fun fromDNS(dns: DNS?): String {
        return gson.toJson(dns)
    }

    @TypeConverter
    fun stringToBypass(value: String?): Bypass {
        val bypassType: Type = object : TypeToken<Bypass?>() {}.type
        return gson.fromJson(value, bypassType)
    }

    @TypeConverter
    fun fromBypass(b: Bypass?): String {
        return gson.toJson(b)
    }
}
