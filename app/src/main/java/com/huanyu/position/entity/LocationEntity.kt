package com.huanyu.position.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity(tableName = "locations", indices = [Index(value = ["buildId"], unique = true)])
@TypeConverters(AliasConverter::class)
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) var id: Int = 0,
    var buildId: Int,
    var name: String,
    var latitude: Double,
    var longitude: Double,
    var area: String,
    var aliases: List<String> = emptyList(),
    var note:String
)

class AliasConverter {
    @TypeConverter
    fun fromAliasList(aliases: List<String>): String {
        return aliases.joinToString(",")
    }

    @TypeConverter
    fun toAliasList(data: String): List<String> {
        return data.split(",").map { it.trim() }
    }
}
