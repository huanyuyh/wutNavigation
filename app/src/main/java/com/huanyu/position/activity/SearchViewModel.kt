package com.huanyu.position.activity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.huanyu.position.Dao.LocationDao
import com.huanyu.position.LocationDatabase
import com.huanyu.position.entity.LocationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchViewModel(application: Application): AndroidViewModel(application) {
    var searchString:String = ""
    private val locationDao: LocationDao = LocationDatabase.getDatabase(application).locationDao()
    private val _allLocations = MutableLiveData<List<LocationEntity>>()
    val allLocations: LiveData<List<LocationEntity>> get() = _allLocations
    private val _area = MutableLiveData<String>("南湖")
    val area: LiveData<String> get() = _area
    fun setArea(area:String){
        _area.value = area
    }
    fun loadLocations() = viewModelScope.launch(Dispatchers.IO) {
        val locations = locationDao.getAllLocations()
        viewModelScope.launch {
            _allLocations.value = locations
        }
    }
    fun searchLocations() = viewModelScope.launch(Dispatchers.IO) {
        val locations = locationDao.searchLocations(searchString)
        viewModelScope.launch {
            _allLocations.value = locations
        }
    }
}