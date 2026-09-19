package com.glmwebshell.core.data.db.converter

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val strList = ListSerializer(String.serializer())

    @TypeConverter fun tagsToString(value: List<String>): String =
        json.encodeToString(strList, value)

    @TypeConverter fun tagsFromString(value: String): List<String> =
        if (value.isBlank()) emptyList() else json.decodeFromString(strList, value)
}
