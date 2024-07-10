package com.huanyu.position.Dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.huanyu.position.entity.LocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(location: LocationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun inserts(locations: List<LocationEntity>)
    @Delete
    fun delete(location: LocationEntity)
    @Query("DELETE FROM locations")
    fun deleteAllLocations():Int
    @Update
    fun update(location: LocationEntity)

    @Query("SELECT * FROM locations")
    fun getAllLocations(): List<LocationEntity>

    @Query("SELECT * FROM locations WHERE area = :area")
    fun getLocationsByArea(area:String):List<LocationEntity>

    @Query("SELECT COUNT(*) FROM locations")
    fun getLocationsCount(): Int

    @Query("SELECT * FROM locations WHERE area LIKE '%' ||:searchString|| '%' OR name LIKE '%' || :searchString || " +
            "'%'OR aliases LIKE '%' || :searchString || '%'OR note LIKE '%' || :searchString || '%'")
    fun searchLocations(searchString:String):List<LocationEntity>
}