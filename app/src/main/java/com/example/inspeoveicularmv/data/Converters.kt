package com.example.inspecaoveicularmv.data

import androidx.room.TypeConverter
import com.example.inspecaoveicularmv.ItemVistoria
import com.example.inspecaoveicularmv.ItemVistoriaMultipla
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromItemVistoriaList(value: List<ItemVistoria>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toItemVistoriaList(value: String): List<ItemVistoria> {
        val listType = object : TypeToken<List<ItemVistoria>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromItemVistoriaMultiplaList(value: List<ItemVistoriaMultipla>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toItemVistoriaMultiplaList(value: String): List<ItemVistoriaMultipla> {
        val listType = object : TypeToken<List<ItemVistoriaMultipla>>() {}.type
        return gson.fromJson(value, listType)
    }
}
