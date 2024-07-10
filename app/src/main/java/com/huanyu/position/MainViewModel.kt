package com.huanyu.position

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.LocationSource
import com.amap.api.maps.LocationSource.OnLocationChangedListener
import com.amap.api.maps.MapView
import com.amap.api.maps.UiSettings
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.Poi

import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.GeocodeSearch.OnGeocodeSearchListener
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.weather.LocalWeatherForecastResult
import com.amap.api.services.weather.LocalWeatherLiveResult
import com.amap.api.services.weather.WeatherSearch
import com.amap.api.services.weather.WeatherSearchQuery
import com.huanyu.position.Dao.LocationDao
import com.huanyu.position.entity.LocationEntity
import com.huanyu.position.util.MapUtil.Companion.convertToLatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.amap.api.navi.AmapNaviPage
import com.amap.api.navi.AmapNaviParams
import com.amap.api.navi.AmapNaviType
import com.amap.api.navi.AmapPageType


class MainViewModel(application: Application):AndroidViewModel(application) {
    @SuppressLint("StaticFieldLeak")
    private val context = getApplication<Application>().applicationContext

    //声明AMapLocationClient类对象
    var mLocationClient: AMapLocationClient? = null
    //声明AMapLocationClientOption对象
    private var mLocationOption: AMapLocationClientOption? = null
    //定位信息
    private var address:MutableLiveData<String> = MutableLiveData("")
    private var city:String = "武汉"
    private var weather:MutableLiveData<String> = MutableLiveData("")
    //地图控制器
    var aMap: AMap? = null

    //位置更改监听
    private var mListener: OnLocationChangedListener? = null

    //定义一个UiSettings对象
    private var mUiSettings: UiSettings? = null

    //地理编码搜索
    val geocodeSearch: GeocodeSearch = GeocodeSearch(context)

    //解析成功标识码
    private val PARSE_SUCCESS_CODE = 1000

    private var isFirstLocation = true
    private var isRealTimeLocation = false
    //是否标点
    private var isShowMarker = false
    //标点列表
    private val markerList: ArrayList<Marker> = ArrayList()
    private var latLng:LatLng = LatLng(30.507919,114.332415)
    lateinit var myMapLocation:AMapLocation
    private val locationDao: LocationDao = LocationDatabase.getDatabase(application).locationDao()
    private val _allLocations = MutableLiveData<List<LocationEntity>>()
    val allLocations: LiveData<List<LocationEntity>> get() = _allLocations

    fun loadLocations() = viewModelScope.launch(Dispatchers.IO) {
        val locations = locationDao.getAllLocations()
        viewModelScope.launch {
            _allLocations.value = locations
        }
    }
    fun showLocations( callback:(List<LocationEntity>) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val location = locationDao.getAllLocations()
        viewModelScope.launch {
            callback(location)
        }
    }

    fun isDatabaseEmpty(callback: (Int) -> Unit) {
        viewModelScope.launch (Dispatchers.IO){
            val count = locationDao.getLocationsCount()
            // 表是否为空
            callback(count)
        }
    }

    fun insertLocation(location: LocationEntity) = viewModelScope.launch(Dispatchers.IO) {
        locationDao.insert(location)
        loadLocations() // Reload locations after insertion
    }
    fun insertLocations(locations: List<LocationEntity>) = viewModelScope.launch(Dispatchers.IO) {
        locationDao.inserts(locations)
        loadLocations() // Reload locations after insertion
    }
    fun deleteAllLocations() = viewModelScope.launch(Dispatchers.IO) {
        locationDao.deleteAllLocations()
        loadLocations() // Reload locations after insertion
    }

    fun deleteLocation(location: LocationEntity) = viewModelScope.launch(Dispatchers.IO) {
        locationDao.delete(location)
        loadLocations() // Reload locations after insertion
    }
    fun getAddress():LiveData<String>{

        return address
    }
    fun getWeather():LiveData<String>{

        return weather
    }

    fun initWeather(){
        Log.d("weather",city)
        var weatherSearchQuery = WeatherSearchQuery("洪山区",WeatherSearchQuery.WEATHER_TYPE_LIVE)
        var mweathersearch=WeatherSearch(context)
        mweathersearch.setOnWeatherSearchListener(object : WeatherSearch.OnWeatherSearchListener {
            override fun onWeatherLiveSearched(weatherLiveResult: LocalWeatherLiveResult?, rCode: Int) {
                if (rCode == 1000) {
                    if (weatherLiveResult != null&&weatherLiveResult.getLiveResult() != null) {
                        var weatherlive = weatherLiveResult.getLiveResult();
                        Log.d("weather",weatherlive.getWeather())
                        weather.value = "${weatherlive.getWeather()} ${weatherlive.getTemperature()}° ${weatherlive.getWindDirection()}风 ${weatherlive.getWindPower()}级"
//                        reporttime1.setText(weatherlive.getReportTime()+"发布");
//                        weather.setText(weatherlive.getWeather());
//                        Temperature.setText(weatherlive.getTemperature()+"°");
//                        wind.setText(weatherlive.getWindDirection()+"风     "+weatherlive.getWindPower()+"级");
//                        humidity.setText("湿度         "+weatherlive.getHumidity()+"%");
                    }else {
                       showMsg("no_result")
                    }
                }else {

                    showMsg( rCode.toString());
                }
            }

            override fun onWeatherForecastSearched(p0: LocalWeatherForecastResult?, p1: Int) {

            }

        })
        mweathersearch.setQuery(weatherSearchQuery);
        mweathersearch.searchWeatherAsyn(); //异步搜索
    }
    /**
     * 初始化地图
     */
    fun initMap(mapView: MapView,context: Context) {
        //初始化地图控制器对象
        aMap = mapView.map

        // 设置定位监听
        aMap?.setLocationSource(object :LocationSource{
            /**
             * 激活定位
             */
            override fun activate(onLocationChangedListener: OnLocationChangedListener) {
                mListener = onLocationChangedListener
                mLocationClient?.startLocation() //启动定位

            }

            /**
             * 停止定位
             */
            override fun deactivate() {
                mListener = null
                mLocationClient?.stopLocation()
                mLocationClient?.onDestroy()

                mLocationClient = null
            }

        })
        // 设置为true表示显示定位层并可触发定位，false表示隐藏定位层并不可触发定位，默认是false
        aMap?.isMyLocationEnabled = true
        // 设置定位样式
        val myLocationStyle = MyLocationStyle()
        myLocationStyle.interval(1000) // 设置定位间隔
        //连续定位、蓝点不会移动到地图中心点，定位点依照设备方向旋转，并且蓝点会跟随设备移动。
        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)

        myLocationStyle.showMyLocation(true)
        aMap?.myLocationStyle = myLocationStyle
        //设置最小缩放等级为16 ，缩放级别范围为[3, 20]
        aMap?.minZoomLevel = 12F
        //开启室内地图
        aMap?.showIndoorMap(true)
        aMap?.isTouchPoiEnable = true
        aMap?.setOnMarkerClickListener {
            showCustomDialog(it,context)
//            val end = Poi(null, it.position, null)
//            // 组件参数配置
//            val params = AmapNaviParams(null, null, end, AmapNaviType.WALK, AmapPageType.ROUTE)
//            params.setMultipleRouteNaviMode(true)
//            params.setShowVoiceSetings(true)
//            params.setTrafficEnabled(true)
//            val aMapNavi = AmapNaviPage.getInstance()
//            // 启动组件
//            aMapNavi.showRouteActivity(context, params, null)
            true
        }

        aMap?.setOnMapClickListener {
            /**
             * 地图单击事件
             */
            showMsg("点击了地图，经度："+it.longitude+"，纬度："+it.latitude);
            Log.d("Mapclick","经度："+it.longitude+"，纬度："+it.latitude)
//            aMap?.addMarker(MarkerOptions().position(it).snippet("武汉理工大学南湖校区"))
        }
        // 设置地图类型为卫星图
        aMap?.mapType = AMap.MAP_TYPE_NORMAL
        //实例化UiSettings类对象
        mUiSettings = aMap?.uiSettings
        //隐藏缩放按钮
        mUiSettings?.isZoomControlsEnabled = false
        //显示比例尺 默认不显示
        mUiSettings?.isScaleControlsEnabled = true
        mUiSettings?.isCompassEnabled = true


        geocodeSearch.setOnGeocodeSearchListener(object :OnGeocodeSearchListener{
            /**
             * 坐标转地址
             * @param regeocodeResult
             * @param rCode
             */
            override fun onRegeocodeSearched(regeocodeResult: RegeocodeResult?, rCode: Int) {
                //解析result获取地址描述信息
                if (rCode === PARSE_SUCCESS_CODE) {
                    val regeocodeAddress = regeocodeResult!!.regeocodeAddress
                    //显示解析后的地址
                    showMsg("地址：" + regeocodeAddress.formatAddress)
                } else {
                    showMsg("获取地址失败")
                }
            }

            /**
             * 地址转坐标
             * @param geocodeResult
             * @param rCode
             */
            override fun onGeocodeSearched(geocodeResult: GeocodeResult?, rCode: Int) {
                if (rCode === PARSE_SUCCESS_CODE) {
                    val geocodeAddressList = geocodeResult!!.geocodeAddressList
                    if (geocodeAddressList != null && geocodeAddressList.size > 0) {
                        val latLonPoint = geocodeAddressList[0].latLonPoint

                        //显示解析后的坐标
                        showMsg("坐标：" + latLonPoint.longitude + "，" + latLonPoint.latitude)
                        val latLng = convertToLatLng(latLonPoint)
                        aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                        val markerOptions = MarkerOptions().position(latLng).snippet(geocodeAddressList[0].formatAddress)
                        var marker = aMap?.addMarker(markerOptions)
                        marker?.let {
                            markerList.add(it)
                        }
                    }
                } else {
                    showMsg("获取坐标失败")
                }
            }
        })
        // 将地图视图移动到标记点
        aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
    }
    private fun showCustomDialog(marker: Marker,context: Context) {
        // 创建AlertDialog
        val builder = AlertDialog.Builder(context)

        // 获取自定义对话框布局
        val inflater: LayoutInflater = LayoutInflater.from(context)
        val dialogView: View = inflater.inflate(R.layout.pop_position, null)
        builder.setView(dialogView)

        // 获取TextView和Button
        val textViewTitle = dialogView.findViewById<TextView>(R.id.text_view_title)
        val textViewDesc = dialogView.findViewById<TextView>(R.id.text_view_desc)
        val buttonGo = dialogView.findViewById<Button>(R.id.button_go)
        val buttonClose = dialogView.findViewById<Button>(R.id.button_close)

        // 设置TextView的文本
        textViewTitle.text = marker.title
        textViewDesc.text = marker.snippet

        // 显示对话框
        val dialog = builder.create()
        // 设置Button的点击事件
        buttonGo.setOnClickListener {
            val end = Poi(null, marker.position, null)
            // 组件参数配置
            val params = AmapNaviParams(null, null, end, AmapNaviType.WALK, AmapPageType.ROUTE)
            params.setMultipleRouteNaviMode(true)
            params.setShowVoiceSetings(true)
            params.setTrafficEnabled(true)
            val aMapNavi = AmapNaviPage.getInstance()
            // 启动组件
            aMapNavi.showRouteActivity(context, params, null)
            dialog.dismiss()
        }
        buttonClose.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showMsg(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
    /**
     * 初始化定位
     */
    fun initLocation() {
        //初始化定位
        mLocationClient = AMapLocationClient(context)
        //设置定位回调监听
        mLocationClient!!.setLocationListener(object :AMapLocationListener {
            override fun onLocationChanged(aMapLocation: AMapLocation?) {
                if (aMapLocation != null) {
                    if (aMapLocation.errorCode == 0) {
                        //地址

                        city = aMapLocation.aoiName

                        address.value = aMapLocation.aoiName
                        Log.d("getAddress",aMapLocation.address)
                        // 通知地图新的定位结果
                        mListener?.onLocationChanged(aMapLocation)
                        latLng = LatLng(aMapLocation.latitude, aMapLocation.longitude)
                        // 只在第一次定位时移动地图中心
                        if (isFirstLocation) {
                            isFirstLocation = false
//                            mLocationClient?.stopLocation()
                            initWeather()
                            aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                        }
                        myMapLocation = aMapLocation

                    } else {
                        //定位失败时，可通过ErrCode(错误码)信息来确定失败的原因，errInfo是错误信息，详见错误码表。
                        Log.e(
                            "AmapError", ("location Error, ErrCode:"
                                    + aMapLocation.errorCode) + ", errInfo:"
                                    + aMapLocation.errorInfo
                        )
                    }
                }
            }
        })

        //初始化AMapLocationClientOption对象
        mLocationOption = AMapLocationClientOption()
        //设置定位模式为AMapLocationMode.Hight_Accuracy，高精度模式。
        mLocationOption!!.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy)
        //获取最近3s内精度最高的一次定位结果：
        //设置setOnceLocationLatest(boolean b)接口为true，启动定位时SDK会返回最近3s内精度最高的一次定位结果。如果设置其为true，setOnceLocation(boolean b)接口也会被设置为true，反之不会，默认为false。
        mLocationOption!!.setOnceLocationLatest(true)
        //设置是否返回地址信息(默认返回地址信息)
        mLocationOption!!.setNeedAddress(true)
        //设置定位请求超时时间，单位是毫秒，默认30000毫秒，建议超时时间不要低于8000毫秒。
        mLocationOption!!.setHttpTimeOut(20000)
        //关闭缓存机制，高精度定位会产生缓存。
        mLocationOption!!.setLocationCacheEnable(false)
        //给定位客户端对象设置定位参数
        mLocationClient!!.setLocationOption(mLocationOption)
    }



    /**
     * 通过经纬度获取地址
     * @param latLng
     */
    private fun latlonToAddress(latLng: LatLng) {
        //位置点  通过经纬度进行构建
        val latLonPoint: LatLonPoint = LatLonPoint(latLng.latitude, latLng.longitude)
        //逆编码查询  第一个参数表示一个Latlng，第二参数表示范围多少米，第三个参数表示是火系坐标系还是GPS原生坐标系
        val query = RegeocodeQuery(latLonPoint, 20f, GeocodeSearch.AMAP)
        //异步获取地址信息
        geocodeSearch.getFromLocationAsyn(query)
    }
    fun showWutMarker(){
        if(isShowMarker){
            if (markerList.size > 0) {
                for (markerItem in markerList) {
                    markerItem.remove()
                }
            }
            isShowMarker = false
        }else{
            showLocations{ locations->
                locations.forEach{ location ->
                    Log.d("locationInfo",location.toString())
                    val latLng = LatLng(location.latitude,location.longitude)
                    val markerOptions = MarkerOptions().position(latLng).title(location.area).snippet(location.name+"\n"+location.aliases.toString())
                    var marker = aMap?.addMarker(markerOptions)
//                    marker?.showInfoWindow()
                    marker?.let {itmarker->
                        markerList.add(itmarker)
                    }
                }
            }
//            markerList.forEach {
//                it.showInfoWindow()
//            }
            isShowMarker = true
        }

//        val latLng = LatLng(30.507919,114.332415)
//        val markerOptions = MarkerOptions().position(latLng).title("南湖").snippet("武汉理工大学南湖校区")
//        //添加标点
//        var marker = aMap?.addMarker(markerOptions)




        // 将地图视图移动到标记点
//        aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
    }

    fun downloadListDate(){
        viewModelScope.launch (Dispatchers.IO){

        }
    }


    fun prepareDate(){
        isDatabaseEmpty { locationCount->

            if(locationCount==0){
                var locations = listOf(
//                    LocationEntity(0, buildId = 1001,name = "武汉理工大学南湖校区",latitude = 30.507919, longitude = 114.332415,
//                        area = "南湖", aliases = listOf("武汉理工大学马房山校区南院"), note = "校区"),
//                    LocationEntity(0, buildId = 1002,name = "文化大厦",latitude = 30.504123, longitude = 114.328289,
//                        area = "南湖", aliases = listOf(""), note = "建筑"),
//                    LocationEntity(0, buildId = 1003,name = "南湖体育场",latitude = 30.505613, longitude = 114.32837,
//                        area = "南湖", aliases = listOf(""), note = "体育"),
//                    LocationEntity(0, buildId = 1004,name = "南湖体育馆",latitude = 30.505534, longitude = 114.330803,
//                        area = "南湖", aliases = listOf(""), note = "体育"),
//                    LocationEntity(0, buildId = 1005,name = "篮球场",latitude = 30.506881, longitude = 114.32926,
//                        area = "南湖", aliases = listOf("南湖校区南院篮球场"), note = "体育"),
//                    LocationEntity(0, buildId = 1006,name = "排球场",latitude = 30.507083, longitude = 114.328534,
//                        area = "南湖", aliases = listOf(""), note = "体育"),
//                    LocationEntity(0, buildId = 1007,name = "网球场",latitude = 30.508152, longitude = 114.328867,
//                        area = "南湖", aliases = listOf(""), note = "体育"),
//                    LocationEntity(0, buildId = 1008,name = "停车场",latitude = 30.504506, longitude = 114.331744,
//                        area = "南湖", aliases = listOf(""), note = "停车场"),
//                    LocationEntity(0, buildId = 1009,name = "南湖游泳馆",latitude = 30.504157, longitude = 114.330428,
//                        area = "南湖", aliases = listOf(""), note = "体育"),
//                    LocationEntity(0, buildId = 1010,name = "南院南门(卓越门)",latitude = 30.503166, longitude = 114.331847,
//                        area = "南湖", aliases = listOf(""), note = "校门"),
//                    LocationEntity(0, buildId = 1011,name = "卓园5舍",latitude = 30.503585, longitude = 114.334807,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1012,name = "卓园4舍",latitude = 30.503993, longitude = 114.334962,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1013,name = "卓园3舍",latitude = 30.504358, longitude = 114.335065,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1014,name = "卓园2舍",latitude = 30.504801, longitude = 114.335156,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1015,name = "卓园1舍",latitude = 30.505215, longitude = 114.335282,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1016,name = "越园7舍",latitude = 30.505604, longitude = 114.335373,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1017,name = "越园6舍",latitude = 30.505988, longitude = 114.335487,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1018,name = "越园5舍",latitude = 30.506362, longitude = 114.33563,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1019,name = "越园4舍",latitude = 30.506776, longitude = 114.33571,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1020,name = "越园3舍",latitude = 30.50716, longitude = 114.335785,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1021,name = "越园2舍",latitude = 30.507564, longitude = 114.335859,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1022,name = "越园1舍",latitude = 30.507943, longitude = 114.335979,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1023,name = "文荟东门",latitude = 30.508091, longitude = 114.337682,
//                        area = "南湖", aliases = listOf(""), note = "校门"),
//                    LocationEntity(0, buildId = 1024,name = "慧园6舍(南湖南6栋)",latitude = 30.506864, longitude = 114.327823,
//                        area = "南湖", aliases = listOf("教育超市-体育场店"), note = "宿舍,超市"),
//                    LocationEntity(0, buildId = 1025,name = "慧园5舍(南湖南5栋)",latitude = 30.507229, longitude = 114.32776,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1026,name = "慧园4舍(南湖南4栋)",latitude = 30.507618, longitude = 114.327903,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1027,name = "慧园3舍(南湖南3栋)",latitude = 30.50797, longitude = 114.328023,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1028,name = "慧园2舍(南湖南2栋)",latitude = 30.508384, longitude = 114.328163,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1029,name = "慧园1舍(南湖南1栋)",latitude = 30.50864, longitude = 114.328357,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1030,name = "篮球场",latitude = 30.511287, longitude = 114.330231,
//                        area = "南湖", aliases = listOf("北篮球场"), note = "体育"),
//                    LocationEntity(0, buildId = 1031,name = "南湖校区南院体育场",latitude = 30.508318, longitude = 114.329586,
//                        area = "南湖", aliases = listOf("南湖校区南院足球场"), note = "体育"),
//                    LocationEntity(0, buildId = 1032,name = "理工大道",latitude = 30.509338, longitude = 114.330271,
//                        area = "南湖", aliases = listOf(""), note = "道路"),
//                    LocationEntity(0, buildId = 1033,name = "武汉理工教育超市",latitude = 30.509981, longitude = 114.328815,
//                        area = "南湖", aliases = listOf("教育超市-南湖店","菜鸟驿站（南湖校区南院）"), note = "生活,超市,快递"),
//                    LocationEntity(0, buildId = 1034,name = "智园7舍(南湖北1栋)",latitude = 30.510191, longitude = 114.32891,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1035,name = "智园6舍(南湖北2栋)",latitude = 30.510543, longitude = 114.32884,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1036,name = "智园5舍(南湖北3栋)",latitude = 30.51088, longitude = 114.328695,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1037,name = "智园4舍(南湖北4栋)",latitude = 30.511175, longitude = 114.328625,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1038,name = "智园3舍(南湖北5栋)",latitude = 30.51153, longitude = 114.32836,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "智园2舍(南湖北6栋)",latitude = 30.511855, longitude = 114.328311,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "智园1舍(南湖北8栋)",latitude = 30.512199, longitude = 114.32817,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "智园11舍",latitude = 30.511506, longitude = 114.329044,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "智园10舍",latitude = 30.511828, longitude = 114.32928,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "智园8舍(南湖北7栋)",latitude = 30.512202, longitude = 114.328751,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "南湖校区服务楼",latitude = 30.512484, longitude = 114.32878,
//                        area = "南湖", aliases = listOf("圆通快递（南湖校区南院）",""), note = "建筑，快递"),
//                    LocationEntity(0, buildId = 1001,name = "学生公寓附2栋",latitude = 30.512488, longitude = 114.32798,
//                        area = "南湖", aliases = listOf("申通快递（南湖校区南院）",""), note = "建筑，快递"),
//                    LocationEntity(0, buildId = 1001,name = "南湖超市",latitude = 30.512442, longitude = 114.329079,
//                        area = "南湖", aliases = listOf("教育超市-井冈店"), note = "生活,超市"),
//                    LocationEntity(0, buildId = 1001,name = "智园9舍",latitude = 30.512174, longitude = 114.329608,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "智苑食堂",latitude = 30.51106, longitude = 114.329841,
//                        area = "南湖", aliases = listOf("中国工商银行ATM（武汉南湖自助银行）"), note = "生活,餐饮，银行"),
//                    LocationEntity(0, buildId = 1001,name = "迎新指定停车区域",latitude = 30.510227, longitude = 114.329636,
//                        area = "南湖", aliases = listOf(""), note = "停车"),
//                    LocationEntity(0, buildId = 1001,name = "智园12舍",latitude = 30.510627, longitude = 114.330686,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "智园13舍",latitude = 30.509931, longitude = 114.330595,
//                        area = "南湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "博学西楼",latitude = 30.51199, longitude = 114.332665,
//                        area = "南湖", aliases = listOf("新3教学楼","自习室：新3-308,新3-310,新3-314,新3-316,新3-408,新3-410,新3-508,新3-510"), note = "教学楼,自习室"),
//                    LocationEntity(0, buildId = 1001,name = "博学北楼",latitude = 30.512493, longitude = 114.334131,
//                        area = "南湖", aliases = listOf(""), note = "教学楼"),
//                    LocationEntity(0, buildId = 1001,name = "博学主楼",latitude = 30.512021, longitude = 114.334101,
//                        area = "南湖", aliases = listOf(""), note = "教学楼"),
//                    LocationEntity(0, buildId = 1001,name = "博学东楼",latitude = 30.511498, longitude = 114.335513,
//                        area = "南湖", aliases = listOf(""), note = "教学楼"),
//                    LocationEntity(0, buildId = 1001,name = "经管楼群（管理学院楼）",latitude = 30.509557, longitude = 114.331551,
//                        area = "南湖", aliases = listOf("管理学院"), note = "教学楼,学院"),
//                    LocationEntity(0, buildId = 1001,name = "南湖校区北侧综合配电房",latitude = 30.513535, longitude = 114.333892,
//                        area = "南湖", aliases = listOf("变电站"), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "博学广场",latitude = 30.510681, longitude = 114.333743,
//                        area = "南湖", aliases = listOf(""), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "心至楼",latitude = 30.509218, longitude = 114.333368,
//                        area = "南湖", aliases = listOf("南湖图书馆","艺术馆（南湖校区心至楼东一门十楼）","校史馆（南湖校区心至楼六楼）","图书馆（南湖校区）"), note = "建筑,虚拟场馆，单位"),
//                    LocationEntity(0, buildId = 1001,name = "雄楚门",latitude = 30.51236, longitude = 114.33845,
//                        area = "南湖", aliases = listOf(""), note = "校门"),
//                    LocationEntity(0, buildId = 1001,name = "理学院楼群（办公楼）",latitude = 30.509942, longitude = 114.335817,
//                        area = "南湖", aliases = listOf("理学院"), note = "教学楼,学院"),
//                    LocationEntity(0, buildId = 1001,name = "理学院楼群（数学楼）",latitude = 30.509711, longitude = 114.335596,
//                        area = "南湖", aliases = listOf(""), note = "教学楼"),
//                    LocationEntity(0, buildId = 1001,name = "理学院楼群（力学楼）",latitude = 30.509388, longitude = 114.335024,
//                        area = "南湖", aliases = listOf(""), note = "教学楼"),
//                    LocationEntity(0, buildId = 1001,name = "理学院楼群（物理楼）",latitude = 30.508941, longitude = 114.335024,
//                        area = "南湖", aliases = listOf(""), note = "教学楼"),
//                    LocationEntity(0, buildId = 1001,name = "工科综合实验楼",latitude = 30.510219, longitude = 114.336973,
//                        area = "南湖", aliases = listOf(""), note = "教学楼"),
//                    LocationEntity(0, buildId = 1001,name = "化生学院楼1",latitude = 30.50929, longitude = 114.336853,
//                        area = "南湖", aliases = listOf("化学化工与生命科学学院"), note = "教学楼，学院"),
//                    LocationEntity(0, buildId = 1001,name = "化生学院楼2",latitude = 30.508654, longitude = 114.336567,
//                        area = "南湖", aliases = listOf(""), note = "教学楼"),
//                    LocationEntity(0, buildId = 1001,name = "大创园（立心楼）",latitude = 30.51063, longitude = 114.338522,
//                        area = "南湖", aliases = listOf("艺术与设计学院"), note = "建筑，学院"),
//                    LocationEntity(0, buildId = 1001,name = "大创园（立行楼）",latitude = 30.510081, longitude = 114.338408,
//                        area = "南湖", aliases = listOf(""), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "大创园（立言楼）",latitude = 30.509778, longitude = 114.338295,
//                        area = "南湖", aliases = listOf(""), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "大创园（立功楼）",latitude = 30.509593, longitude = 114.338093,
//                        area = "南湖", aliases = listOf(""), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "大创园（立德楼）",latitude = 30.509439, longitude = 114.337765,
//                        area = "南湖", aliases = listOf("南湖医务室","学生工作部（处）、武装部"), note = "建筑,医疗,部门"),
//                    LocationEntity(0, buildId = 1001,name = "越苑食堂",latitude = 30.508782, longitude = 114.33764,
//                        area = "南湖", aliases = listOf(""), note = "生活,餐饮"),

                    LocationEntity(0, buildId=1001, name="武汉理工大学南湖校区", latitude=30.507919, longitude=114.332415, area="南湖", aliases=listOf("武汉理工大学马房山校区南院"), note="校区"),
                    LocationEntity(0, buildId=1002, name="文化大厦", latitude=30.504123, longitude=114.328289, area="南湖", aliases=listOf(""), note="建筑"),
                    LocationEntity(0, buildId=1003, name="南湖体育场", latitude=30.505613, longitude=114.32837, area="南湖", aliases=listOf(""), note="体育"),
                    LocationEntity(0, buildId=1004, name="南湖体育馆", latitude=30.505534, longitude=114.330803, area="南湖", aliases=listOf(""), note="体育"),
                    LocationEntity(0, buildId=1005, name="篮球场", latitude=30.506881, longitude=114.32926, area="南湖", aliases=listOf("南湖校区南院篮球场"), note="体育"),
                    LocationEntity(0, buildId=1006, name="排球场", latitude=30.507083, longitude=114.328534, area="南湖", aliases=listOf(""), note="体育"),
                    LocationEntity(0, buildId=1007, name="网球场", latitude=30.508152, longitude=114.328867, area="南湖", aliases=listOf(""), note="体育"),
                    LocationEntity(0, buildId=1008, name="停车场", latitude=30.504506, longitude=114.331744, area="南湖", aliases=listOf(""), note="停车场"),
                    LocationEntity(0, buildId=1009, name="南湖游泳馆", latitude=30.504157, longitude=114.330428, area="南湖", aliases=listOf(""), note="体育"),
                    LocationEntity(0, buildId=1010, name="南院南门(卓越门)", latitude=30.503166, longitude=114.331847, area="南湖", aliases=listOf(""), note="校门"),
                    LocationEntity(0, buildId=1011, name="卓园5舍", latitude=30.503585, longitude=114.334807, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1012, name="卓园4舍", latitude=30.503993, longitude=114.334962, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1013, name="卓园3舍", latitude=30.504358, longitude=114.335065, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1014, name="卓园2舍", latitude=30.504801, longitude=114.335156, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1015, name="卓园1舍", latitude=30.505215, longitude=114.335282, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1016, name="越园7舍", latitude=30.505604, longitude=114.335373, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1017, name="越园6舍", latitude=30.505988, longitude=114.335487, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1018, name="越园5舍", latitude=30.506362, longitude=114.33563, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1019, name="越园4舍", latitude=30.506776, longitude=114.33571, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1020, name="越园3舍", latitude=30.50716, longitude=114.335785, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1021, name="越园2舍", latitude=30.507564, longitude=114.335859, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1022, name="越园1舍", latitude=30.507943, longitude=114.335979, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1023, name="文荟东门", latitude=30.508091, longitude=114.337682, area="南湖", aliases=listOf(""), note="校门"),
                    LocationEntity(0, buildId=1024, name="慧园6舍(南湖南6栋)", latitude=30.506864, longitude=114.327823, area="南湖", aliases=listOf("教育超市-体育场店"), note="宿舍,超市"),
                    LocationEntity(0, buildId=1025, name="慧园5舍(南湖南5栋)", latitude=30.507229, longitude=114.32776, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1026, name="慧园4舍(南湖南4栋)", latitude=30.507618, longitude=114.327903, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1027, name="慧园3舍(南湖南3栋)", latitude=30.50797, longitude=114.328023, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1028, name="慧园2舍(南湖南2栋)", latitude=30.508384, longitude=114.328163, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1029, name="慧园1舍(南湖南1栋)", latitude=30.50864, longitude=114.328357, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1030, name="篮球场", latitude=30.511287, longitude=114.330231, area="南湖", aliases=listOf("北篮球场"), note="体育"),
                    LocationEntity(0, buildId=1031, name="南湖校区南院体育场", latitude=30.508318, longitude=114.329586, area="南湖", aliases=listOf("南湖校区南院足球场"), note="体育"),
                    LocationEntity(0, buildId=1032, name="理工大道", latitude=30.509338, longitude=114.330271, area="南湖", aliases=listOf(""), note="道路"),
                    LocationEntity(0, buildId=1033, name="武汉理工教育超市", latitude=30.509981, longitude=114.328815, area="南湖", aliases=listOf("教育超市-南湖店", "菜鸟驿站（南湖校区南院）"), note="生活,超市,快递"),
                    LocationEntity(0, buildId=1034, name="智园7舍(南湖北1栋)", latitude=30.510191, longitude=114.32891, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1035, name="智园6舍(南湖北2栋)", latitude=30.510543, longitude=114.32884, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1036, name="智园5舍(南湖北3栋)", latitude=30.51088, longitude=114.328695, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1037, name="智园4舍(南湖北4栋)", latitude=30.511175, longitude=114.328625, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1038, name="智园3舍(南湖北5栋)", latitude=30.51153, longitude=114.32836, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1039, name="智园2舍(南湖北6栋)", latitude=30.511855, longitude=114.328311, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1040, name="智园1舍(南湖北8栋)", latitude=30.512199, longitude=114.32817, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1041, name="智园11舍", latitude=30.511506, longitude=114.329044, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1042, name="智园10舍", latitude=30.511828, longitude=114.32928, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1043, name="智园8舍(南湖北7栋)", latitude=30.512202, longitude=114.328751, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1044, name="南湖校区服务楼", latitude=30.512484, longitude=114.32878, area="南湖", aliases=listOf("圆通快递（南湖校区南院）", ""), note="建筑，快递"),
                    LocationEntity(0, buildId=1045, name="学生公寓附2栋", latitude=30.512488, longitude=114.32798, area="南湖", aliases=listOf("申通快递（南湖校区南院）", ""), note="建筑，快递"),
                    LocationEntity(0, buildId=1046, name="南湖超市", latitude=30.512442, longitude=114.329079, area="南湖", aliases=listOf("教育超市-井冈店"), note="生活,超市"),
                    LocationEntity(0, buildId=1047, name="智园9舍", latitude=30.512174, longitude=114.329608, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1048, name="智苑食堂", latitude=30.51106, longitude=114.329841, area="南湖", aliases=listOf("中国工商银行ATM（武汉南湖自助银行）"), note="生活,餐饮，银行"),
                    LocationEntity(0, buildId=1049, name="迎新指定停车区域", latitude=30.510227, longitude=114.329636, area="南湖", aliases=listOf(""), note="停车"),
                    LocationEntity(0, buildId=1050, name="智园12舍", latitude=30.510627, longitude=114.330686, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1051, name="智园13舍", latitude=30.509931, longitude=114.330595, area="南湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=1052, name="博学西楼", latitude=30.51199, longitude=114.332665, area="南湖", aliases=listOf("新3教学楼", "自习室：新3-308,新3-310,新3-314,新3-316,新3-408,新3-410,新3-508,新3-510"), note="教学楼,自习室"),
                    LocationEntity(0, buildId=1053, name="博学北楼", latitude=30.512493, longitude=114.334131, area="南湖", aliases=listOf(""), note="教学楼"),
                    LocationEntity(0, buildId=1054, name="博学主楼", latitude=30.512021, longitude=114.334101, area="南湖", aliases=listOf(""), note="教学楼"),
                    LocationEntity(0, buildId=1055, name="博学东楼", latitude=30.511498, longitude=114.335513, area="南湖", aliases=listOf(""), note="教学楼"),
                    LocationEntity(0, buildId=1056, name="经管楼群（管理学院楼）", latitude=30.509557, longitude=114.331551, area="南湖", aliases=listOf("管理学院"), note="教学楼,学院"),
                    LocationEntity(0, buildId=1057, name="南湖校区北侧综合配电房", latitude=30.513535, longitude=114.333892, area="南湖", aliases=listOf("变电站"), note="建筑"),
                    LocationEntity(0, buildId=1058, name="博学广场", latitude=30.510681, longitude=114.333743, area="南湖", aliases=listOf(""), note="建筑"),
                    LocationEntity(0, buildId=1059, name="心至楼", latitude=30.509218, longitude=114.333368, area="南湖", aliases=listOf("南湖图书馆", "艺术馆（南湖校区心至楼东一门十楼）", "校史馆（南湖校区心至楼六楼）", "图书馆（南湖校区）"), note="建筑,虚拟场馆，单位"),
                    LocationEntity(0, buildId=1060, name="雄楚门", latitude=30.51236, longitude=114.33845, area="南湖", aliases=listOf(""), note="校门"),
                    LocationEntity(0, buildId=1061, name="理学院楼群（办公楼）", latitude=30.509942, longitude=114.335817, area="南湖", aliases=listOf("理学院"), note="教学楼,学院"),
                    LocationEntity(0, buildId=1062, name="理学院楼群（数学楼）", latitude=30.509711, longitude=114.335596, area="南湖", aliases=listOf(""), note="教学楼"),
                    LocationEntity(0, buildId=1063, name="理学院楼群（力学楼）", latitude=30.509388, longitude=114.335024, area="南湖", aliases=listOf(""), note="教学楼"),
                    LocationEntity(0, buildId=1064, name="理学院楼群（物理楼）", latitude=30.508941, longitude=114.335024, area="南湖", aliases=listOf(""), note="教学楼"),
                    LocationEntity(0, buildId=1065, name="工科综合实验楼", latitude=30.510219, longitude=114.336973, area="南湖", aliases=listOf(""), note="教学楼"),
                    LocationEntity(0, buildId=1066, name="化生学院楼1", latitude=30.50929, longitude=114.336853, area="南湖", aliases=listOf("化学化工与生命科学学院"), note="教学楼，学院"),
                    LocationEntity(0, buildId=1067, name="化生学院楼2", latitude=30.508654, longitude=114.336567, area="南湖", aliases=listOf(""), note="教学楼"),
                    LocationEntity(0, buildId=1068, name="大创园（立心楼）", latitude=30.51063, longitude=114.338522, area="南湖", aliases=listOf("艺术与设计学院"), note="建筑，学院"),
                    LocationEntity(0, buildId=1069, name="大创园（立行楼）", latitude=30.510081, longitude=114.338408, area="南湖", aliases=listOf(""), note="建筑"),
                    LocationEntity(0, buildId=1070, name="大创园（立言楼）", latitude=30.509778, longitude=114.338295, area="南湖", aliases=listOf(""), note="建筑"),
                    LocationEntity(0, buildId=1071, name="大创园（立功楼）", latitude=30.509593, longitude=114.338093, area="南湖", aliases=listOf(""), note="建筑"),
                    LocationEntity(0, buildId=1072, name="大创园（立德楼）", latitude=30.509439, longitude=114.337765, area="南湖", aliases=listOf("南湖医务室", "学生工作部（处）、武装部"), note="建筑,医疗,部门"),
                    LocationEntity(0, buildId=1073, name="越苑食堂", latitude=30.508782, longitude=114.33764, area="南湖", aliases=listOf(""), note="生活,餐饮"),
//                    LocationEntity(0, buildId = 1001,name = "武汉理工大学鉴湖校区",latitude = 30.513791, longitude = 114.343923,
//                        area = "鉴湖", aliases = listOf("武汉理工大学马房山校区北院"), note = "校区"),
//                    LocationEntity(0, buildId = 1001,name = "鉴湖",latitude = 30.512076, longitude = 114.342616,
//                        area = "鉴湖", aliases = listOf(""), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "鉴湖12号留学生公寓",latitude = 30.511892, longitude = 114.341576,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "保卫处办公楼",latitude = 30.512045, longitude = 114.341329,
//                        area = "鉴湖", aliases = listOf("保卫处（部）"), note = "建筑,部门"),
//                    LocationEntity(0, buildId = 1001,name = "北5舍",latitude = 30.512375, longitude = 114.341516,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "北4舍（西11舍）",latitude = 30.512664, longitude = 114.341581,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "北3舍（西10舍）",latitude = 30.51306, longitude = 114.341648,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "北2舍（西9舍）",latitude = 30.513579, longitude = 114.34154,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "教育超市",latitude = 30.513065, longitude = 114.342242,
//                        area = "鉴湖", aliases = listOf(""), note = "生活"),
//                    LocationEntity(0, buildId = 1001,name = "北院第二教学楼（北教二）",latitude = 30.513255, longitude = 114.34218,
//                        area = "鉴湖", aliases = listOf(""), note = "教学楼"),
//                    LocationEntity(0, buildId = 1001,name = "鉴湖配电房",latitude = 30.513091, longitude = 114.343295,
//                        area = "鉴湖", aliases = listOf(""), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "鉴湖水泵房",latitude = 30.51353, longitude = 114.342016,
//                        area = "鉴湖", aliases = listOf(""), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "韵苑食堂",latitude = 30.512952, longitude = 114.342649,
//                        area = "鉴湖", aliases = listOf(""), note = "生活,餐饮"),
//                    LocationEntity(0, buildId = 1001,name = "爱特楼（IT楼）",latitude = 30.512732572359337, longitude = 114.3438893366862,
//                        area = "鉴湖", aliases = listOf("鉴主","鉴湖主教学楼","计算机科学与技术学院","信息工程学院","网络信息中心"), note = "教学楼，学院,单位"),
//                    LocationEntity(0, buildId = 1001,name = "北院第一教学楼",latitude = 30.51275105849265, longitude = 114.34314368259281,
//                        area = "鉴湖", aliases = listOf("经济学院"), note = "教学楼"),
//                    LocationEntity(0, buildId = 1001,name = "北院第三教学楼（北教三）",latitude = 30.51238364593364, longitude = 114.34453172573427,
//                        area = "鉴湖", aliases = listOf("北教三","鉴3教学楼","自习室：鉴3-101,鉴3-103,鉴3-104"), note = "教学楼,自习室"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.512761, longitude = 114.344756,
//                        area = "鉴湖", aliases = listOf(""), note = "停车场"),
//                    LocationEntity(0, buildId = 1001,name = "中百罗森",latitude = 30.512741, longitude = 114.345116,
//                        area = "鉴湖", aliases = listOf(""), note = "生活"),
//                    LocationEntity(0, buildId = 1001,name = "鉴湖大门(未开)",latitude = 30.511097, longitude = 114.343419,
//                        area = "鉴湖", aliases = listOf("北院大门"), note = "校门"),
//                    LocationEntity(0, buildId = 1001,name = "工大路1号门",latitude = 30.511996, longitude = 114.344995,
//                        area = "鉴湖", aliases = listOf("北院东门1,鉴湖东南门1"), note = "校门"),
//                    LocationEntity(0, buildId = 1001,name = "工大路2号门",latitude = 30.512901, longitude = 114.345222,
//                        area = "鉴湖", aliases = listOf("北院东门2,鉴湖东南门2"), note = "校门"),
//                    LocationEntity(0, buildId = 1001,name = "南湖校区北院网球场",latitude = 30.513779, longitude = 114.3428,
//                        area = "鉴湖", aliases = listOf(""), note = "体育"),
//                    LocationEntity(0, buildId = 1001,name = "南湖校区北院篮球场",latitude = 30.514147, longitude = 114.342883,
//                        area = "鉴湖", aliases = listOf(""), note = "体育"),
//                    LocationEntity(0, buildId = 1001,name = "学海公寓A栋-1号楼",latitude = 30.513322, longitude = 114.344368,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "学海公寓A栋-2号楼",latitude = 30.513566, longitude = 114.344355,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "学海公寓A栋-3号楼",latitude = 30.513872, longitude = 114.344504,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "学海公寓A栋-4号楼",latitude = 30.513754, longitude = 114.34482,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "北1舍（西21舍）",latitude = 30.513619, longitude = 114.343946,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "雅苑食堂",latitude = 30.513273, longitude = 114.34506,
//                        area = "鉴湖", aliases = listOf("中国建设银行24小时自助银行","校园一卡通服务中心（南湖校区北院）"), note = "生活,餐饮,银行,一卡通"),
//                    LocationEntity(0, buildId = 1001,name = "鉴湖后勤出租房（东一门）",latitude = 30.513227, longitude = 114.344579,
//                        area = "鉴湖", aliases = listOf("国泰民欣大药房","中国电信（南湖校区北院）","中国联通（南湖校区北院）"), note = "建筑,医疗,通讯"),
//                    LocationEntity(0, buildId = 1001,name = "南湖校区北院排球场",latitude = 30.51477, longitude = 114.342824,
//                        area = "鉴湖", aliases = listOf(""), note = "体育"),
//                    LocationEntity(0, buildId = 1001,name = "南湖校区北院足球场",latitude = 30.515226, longitude = 114.343611,
//                        area = "鉴湖", aliases = listOf(""), note = "体育"),
//                    LocationEntity(0, buildId = 1001,name = "西院69",latitude = 30.513822, longitude = 114.345175,
//                        area = "鉴湖", aliases = listOf(""), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "工大路三号门",latitude = 30.514124, longitude = 114.345421,
//                        area = "鉴湖", aliases = listOf(""), note = "校门"),
//                    LocationEntity(0, buildId = 1001,name = "学海3舍（西20舍）",latitude = 30.514405, longitude = 114.343937,
//                        area = "鉴湖", aliases = listOf("教育超市-学海店"), note = "宿舍,超市"),
//                    LocationEntity(0, buildId = 1001,name = "学海6舍（西19舍）",latitude = 30.514494, longitude = 114.344497,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "学海2舍（西18舍）",latitude = 30.515087, longitude = 114.344039,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "学海5舍（西17舍）",latitude = 30.515024, longitude = 114.344527,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "鉴湖体育场训练房",latitude = 30.5157, longitude = 114.343516,
//                        area = "鉴湖", aliases = listOf(""), note = "体育"),
//                    LocationEntity(0, buildId = 1001,name = "学海1舍（西16舍）",latitude = 30.515577, longitude = 114.344179,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "学海4舍（西15舍）",latitude = 30.515504, longitude = 114.344678,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "学海8舍",latitude = 30.515233, longitude = 114.345319,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.515188, longitude = 114.345547,
//                        area = "鉴湖", aliases = listOf(""), note = "停车场"),
//                    LocationEntity(0, buildId = 1001,name = "学海7舍",latitude = 30.515915, longitude = 114.345264,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "理工云创城",latitude = 30.513973, longitude = 114.345224,
//                        area = "鉴湖", aliases = listOf(""), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "学海楼",latitude = 30.514693, longitude = 114.34614,
//                        area = "鉴湖", aliases = listOf("经济学院","外国语学院"), note = "教学楼,学院"),
//                    LocationEntity(0, buildId = 1001,name = "西院-12舍",latitude = 30.514191, longitude = 114.34592,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "西院-13舍",latitude = 30.51414, longitude = 114.346397,
//                        area = "鉴湖", aliases = listOf(""), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "足球场",latitude = 30.514598, longitude = 114.346518,
//                        area = "鉴湖", aliases = listOf("南湖校区北院学海楼东足球场"), note = "体育"),
//                    LocationEntity(0, buildId = 1001,name = "理工大附小（楼群2栋）",latitude = 30.515732, longitude = 114.346379,
//                        area = "鉴湖", aliases = listOf(""), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "十五亩地小区",latitude = 30.516841, longitude = 114.346532,
//                        area = "鉴湖", aliases = listOf(""), note = "建筑"),
                    LocationEntity(0, buildId=2001, name="武汉理工大学鉴湖校区", latitude=30.513791, longitude=114.343923, area="鉴湖", aliases=listOf("武汉理工大学马房山校区北院"), note="校区"),
                    LocationEntity(0, buildId=2002, name="鉴湖", latitude=30.512076, longitude=114.342616, area="鉴湖", aliases=listOf(""), note="建筑"),
                    LocationEntity(0, buildId=2003, name="鉴湖12号留学生公寓", latitude=30.511892, longitude=114.341576, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2004, name="保卫处办公楼", latitude=30.512045, longitude=114.341329, area="鉴湖", aliases=listOf("保卫处（部）"), note="建筑,部门"),
                    LocationEntity(0, buildId=2005, name="北5舍", latitude=30.512375, longitude=114.341516, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2006, name="北4舍（西11舍）", latitude=30.512664, longitude=114.341581, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2007, name="北3舍（西10舍）", latitude=30.51306, longitude=114.341648, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2008, name="北2舍（西9舍）", latitude=30.513579, longitude=114.34154, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2009, name="教育超市", latitude=30.513065, longitude=114.342242, area="鉴湖", aliases=listOf(""), note="生活"),
                    LocationEntity(0, buildId=2010, name="北院第二教学楼（北教二）", latitude=30.513255, longitude=114.34218, area="鉴湖", aliases=listOf(""), note="教学楼"),
                    LocationEntity(0, buildId=2011, name="鉴湖配电房", latitude=30.513091, longitude=114.343295, area="鉴湖", aliases=listOf(""), note="建筑"),
                    LocationEntity(0, buildId=2012, name="鉴湖水泵房", latitude=30.51353, longitude=114.342016, area="鉴湖", aliases=listOf(""), note="建筑"),
                    LocationEntity(0, buildId=2013, name="韵苑食堂", latitude=30.512952, longitude=114.342649, area="鉴湖", aliases=listOf(""), note="生活,餐饮"),
                    LocationEntity(0, buildId=2014, name="爱特楼（IT楼）", latitude=30.512732572359337, longitude=114.3438893366862, area="鉴湖", aliases=listOf("鉴主", "鉴湖主教学楼", "计算机科学与技术学院", "信息工程学院", "网络信息中心"), note="教学楼，学院,单位"),
                    LocationEntity(0, buildId=2015, name="北院第一教学楼", latitude=30.51275105849265, longitude=114.34314368259281, area="鉴湖", aliases=listOf("经济学院"), note="教学楼"),
                    LocationEntity(0, buildId=2016, name="北院第三教学楼（北教三）", latitude=30.51238364593364, longitude=114.34453172573427, area="鉴湖", aliases=listOf("北教三", "鉴3教学楼", "自习室：鉴3-101,鉴3-103,鉴3-104"), note="教学楼,自习室"),
                    LocationEntity(0, buildId=2017, name="停车场", latitude=30.512761, longitude=114.344756, area="鉴湖", aliases=listOf(""), note="停车场"),
                    LocationEntity(0, buildId=2018, name="中百罗森", latitude=30.512741, longitude=114.345116, area="鉴湖", aliases=listOf(""), note="生活"),
                    LocationEntity(0, buildId=2019, name="鉴湖大门(未开)", latitude=30.511097, longitude=114.343419, area="鉴湖", aliases=listOf("北院大门"), note="校门"),
                    LocationEntity(0, buildId=2020, name="工大路1号门", latitude=30.511996, longitude=114.344995, area="鉴湖", aliases=listOf("北院东门1,鉴湖东南门1"), note="校门"),
                    LocationEntity(0, buildId=2021, name="工大路2号门", latitude=30.512901, longitude=114.345222, area="鉴湖", aliases=listOf("北院东门2,鉴湖东南门2"), note="校门"),
                    LocationEntity(0, buildId=2022, name="南湖校区北院网球场", latitude=30.513779, longitude=114.3428, area="鉴湖", aliases=listOf(""), note="体育"),
                    LocationEntity(0, buildId=2023, name="南湖校区北院篮球场", latitude=30.514147, longitude=114.342883, area="鉴湖", aliases=listOf(""), note="体育"),
                    LocationEntity(0, buildId=2024, name="学海公寓A栋-1号楼", latitude=30.513322, longitude=114.344368, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2025, name="学海公寓A栋-2号楼", latitude=30.513566, longitude=114.344355, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2026, name="学海公寓A栋-3号楼", latitude=30.513872, longitude=114.344504, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2027, name="学海公寓A栋-4号楼", latitude=30.513754, longitude=114.34482, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2028, name="北1舍（西21舍）", latitude=30.513619, longitude=114.343946, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2029, name="雅苑食堂", latitude=30.513273, longitude=114.34506, area="鉴湖", aliases=listOf("中国建设银行24小时自助银行", "校园一卡通服务中心（南湖校区北院）"), note="生活,餐饮,银行,一卡通"),
                    LocationEntity(0, buildId=2030, name="鉴湖后勤出租房（东一门）", latitude=30.513227, longitude=114.344579, area="鉴湖", aliases=listOf("国泰民欣大药房", "中国电信（南湖校区北院）", "中国联通（南湖校区北院）"), note="建筑,医疗,通讯"),
                    LocationEntity(0, buildId=2031, name="南湖校区北院排球场", latitude=30.51477, longitude=114.342824, area="鉴湖", aliases=listOf(""), note="体育"),
                    LocationEntity(0, buildId=2032, name="南湖校区北院足球场", latitude=30.515226, longitude=114.343611, area="鉴湖", aliases=listOf(""), note="体育"),
                    LocationEntity(0, buildId=2033, name="西院69", latitude=30.513822, longitude=114.345175, area="鉴湖", aliases=listOf(""), note="建筑"),
                    LocationEntity(0, buildId=2034, name="工大路三号门", latitude=30.514124, longitude=114.345421, area="鉴湖", aliases=listOf(""), note="校门"),
                    LocationEntity(0, buildId=2035, name="学海3舍（西20舍）", latitude=30.514405, longitude=114.343937, area="鉴湖", aliases=listOf("教育超市-学海店"), note="宿舍,超市"),
                    LocationEntity(0, buildId=2036, name="学海6舍（西19舍）", latitude=30.514494, longitude=114.344497, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2037, name="学海2舍（西18舍）", latitude=30.515087, longitude=114.344039, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2038, name="学海5舍（西17舍）", latitude=30.515024, longitude=114.344527, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2039, name="鉴湖体育场训练房", latitude=30.5157, longitude=114.343516, area="鉴湖", aliases=listOf(""), note="体育"),
                    LocationEntity(0, buildId=2040, name="学海1舍（西16舍）", latitude=30.515577, longitude=114.344179, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2041, name="学海4舍（西15舍）", latitude=30.515504, longitude=114.344678, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2042, name="学海8舍", latitude=30.515233, longitude=114.345319, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2043, name="停车场", latitude=30.515188, longitude=114.345547, area="鉴湖", aliases=listOf(""), note="停车场"),
                    LocationEntity(0, buildId=2044, name="学海7舍", latitude=30.515915, longitude=114.345264, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2045, name="理工云创城", latitude=30.513973, longitude=114.345224, area="鉴湖", aliases=listOf(""), note="建筑"),
                    LocationEntity(0, buildId=2046, name="学海楼", latitude=30.514693, longitude=114.34614, area="鉴湖", aliases=listOf("经济学院", "外国语学院"), note="教学楼,学院"),
                    LocationEntity(0, buildId=2047, name="西院-12舍", latitude=30.514191, longitude=114.34592, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2048, name="西院-13舍", latitude=30.51414, longitude=114.346397, area="鉴湖", aliases=listOf(""), note="宿舍"),
                    LocationEntity(0, buildId=2049, name="足球场", latitude=30.514598, longitude=114.346518, area="鉴湖", aliases=listOf("南湖校区北院学海楼东足球场"), note="体育"),
                    LocationEntity(0, buildId=2050, name="理工大附小（楼群2栋）", latitude=30.515732, longitude=114.346379, area="鉴湖", aliases=listOf(""), note="建筑"),
                    LocationEntity(0, buildId=2051, name="十五亩地小区", latitude=30.516841, longitude=114.346532, area="鉴湖", aliases=listOf(""), note="建筑"),

//                    LocationEntity(2000, buildId = 1001,name = "武汉理工大学马房山校区西院",latitude = 30.521886, longitude = 114.348732,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院南1门",latitude = 30.518282, longitude = 114.346319,
//                        area = "西院", aliases = listOf(" "), note = "校门"),
//                    LocationEntity(0, buildId = 1001,name = "西院南2门",latitude = 30.51783, longitude = 114.347507,
//                        area = "西院", aliases = listOf(" "), note = "校门"),
//                    LocationEntity(0, buildId = 1001,name = "西院体育馆",latitude = 30.518061, longitude = 114.34788,
//                        area = "西院", aliases = listOf(" "), note = "体育"),
//                    LocationEntity(0, buildId = 1001,name = "西院足球场",latitude = 30.518705, longitude = 114.347005,
//                        area = "西院", aliases = listOf("马房山校区西院足球场"), note = "体育"),
//                    LocationEntity(0, buildId = 1001,name = "教职工宿舍",latitude = 30.518603, longitude = 114.348636,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院14舍",latitude = 30.518578, longitude = 114.348359,
//                        area = "西院", aliases = listOf(" "), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "西院博士后公寓",latitude = 30.51826, longitude = 114.348737,
//                        area = "西院", aliases = listOf(" "), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "1号楼",latitude = 30.517838, longitude = 114.350005,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西4舍",latitude = 30.519843, longitude = 114.346904,
//                        area = "西院", aliases = listOf(" "), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "网球场",latitude = 30.520179, longitude = 114.346863,
//                        area = "西院", aliases = listOf("马房山校区西院网球场"), note = "体育"),
//                    LocationEntity(0, buildId = 1001,name = "篮球场",latitude = 30.520339, longitude = 114.347512,
//                        area = "西院", aliases = listOf(" "), note = "体育"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.52009, longitude = 114.347525,
//                        area = "西院", aliases = listOf(" "), note = "停车场"),
//                    LocationEntity(0, buildId = 1001,name = "西区59号楼",latitude = 30.520095, longitude = 114.347767,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西区校园服务中心",latitude = 30.519878, longitude = 114.347912,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院58栋",latitude = 30.519996, longitude = 114.348226,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "57号楼",latitude = 30.520056, longitude = 114.348718,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西3舍",latitude = 30.520606, longitude = 114.346865,
//                        area = "西院", aliases = listOf(" "), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "西2舍",latitude = 30.520906, longitude = 114.346844,
//                        area = "西院", aliases = listOf(" "), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "西1舍",latitude = 30.521175, longitude = 114.346899,
//                        area = "西院", aliases = listOf(" "), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "西区篮球场",latitude = 30.521419, longitude = 114.346992,
//                        area = "西院", aliases = listOf(" "), note = "体育"),
//                    LocationEntity(0, buildId = 1001,name = "西实验楼",latitude = 30.521225, longitude = 114.34782,
//                        area = "西院", aliases = listOf("资源与环境工程学院"), note = "教学楼，学院"),
//                    LocationEntity(0, buildId = 1001,name = "恬苑食堂",latitude = 30.520551, longitude = 114.347973,
//                        area = "西院", aliases = listOf(" "), note = "生活,餐饮"),
//                    LocationEntity(0, buildId = 1001,name = "工会委员会",latitude = 30.520325, longitude = 114.348703,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "教职工活动中心",latitude = 30.520665, longitude = 114.348693,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "工大楼",latitude = 30.521476, longitude = 114.348541,
//                        area = "西院", aliases = listOf("材料科学与工程学院"), note = "教学楼,学院"),
//                    LocationEntity(0, buildId = 1001,name = "工大楼西配楼",latitude = 30.521249, longitude = 114.348252,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "材料大楼附楼",latitude = 30.521264, longitude = 114.348612,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "工大楼东配楼",latitude = 30.521284, longitude = 114.348947,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "工大楼南配楼",latitude = 30.520973, longitude = 114.348426,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "工大楼南配楼",latitude = 30.520968, longitude = 114.348838,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东实验楼",latitude = 30.521474, longitude = 114.34951,
//                        area = "西院", aliases = listOf(" "), note = "教学楼"),
//                    LocationEntity(0, buildId = 1001,name = "材料实验中心附楼",latitude = 30.521252, longitude = 114.349514,
//                        area = "西院", aliases = listOf(" "), note = "教学楼"),
//                    LocationEntity(0, buildId = 1001,name = "东实验楼南楼",latitude = 30.520985, longitude = 114.349543,
//                        area = "西院", aliases = listOf("材料研究与测试中心"), note = "教学楼,单位"),
//                    LocationEntity(0, buildId = 1001,name = "高温高压物理实验室",latitude = 30.520702, longitude = 114.349781,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院超市",latitude = 30.520467, longitude = 114.349928,
//                        area = "西院", aliases = listOf("教育超市-西院店","西院快递服务中心（马房山校区西院)"), note = "生活,超市，快递"),
//                    LocationEntity(0, buildId = 1001,name = "西8舍",latitude = 30.520329, longitude = 114.350603,
//                        area = "西院", aliases = listOf(" "), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "德生楼",latitude = 30.521178, longitude = 114.350201,
//                        area = "西院", aliases = listOf(" "), note = "教学楼"),
//                    LocationEntity(0, buildId = 1001,name = "西院大门",latitude = 30.52174, longitude = 114.350718,
//                        area = "西院", aliases = listOf(" "), note = "校门"),
//                    LocationEntity(0, buildId = 1001,name = "西品二号楼",latitude = 30.5218, longitude = 114.34691,
//                        area = "西院", aliases = listOf("研究生院"), note = "建筑,部门"),
//                    LocationEntity(0, buildId = 1001,name = "西品三号楼",latitude = 30.521835, longitude = 114.347666,
//                        area = "西院", aliases = listOf("国有资产与实验室管理处","人事处"), note = "建筑，部门"),
//                    LocationEntity(0, buildId = 1001,name = "西院36号",latitude = 30.521904, longitude = 114.346596,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院留学生公寓",latitude = 30.522113, longitude = 114.346676,
//                        area = "西院", aliases = listOf(" "), note = "宿舍"),
//                    LocationEntity(0, buildId = 1001,name = "西品一号楼",latitude = 30.522118, longitude = 114.347246,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.522146, longitude = 114.348035,
//                        area = "西院", aliases = listOf(" "), note = "停车场"),
//                    LocationEntity(0, buildId = 1001,name = "绿化中心",latitude = 30.522308, longitude = 114.346479,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西教学楼",latitude = 30.522691, longitude = 114.347139,
//                        area = "西院", aliases = listOf(" "), note = "教学楼"),
//                    LocationEntity(0, buildId = 1001,name = "西院图书馆",latitude = 30.522661, longitude = 114.347746,
//                        area = "西院", aliases = listOf("材料科学与工程国际化学院"), note = "建筑,学院"),
//                    LocationEntity(0, buildId = 1001,name = "西院校医院（楼群2栋）",latitude = 30.5231, longitude = 114.347445,
//                        area = "西院", aliases = listOf("西院校医院"), note = "建筑,医疗"),
//                    LocationEntity(0, buildId = 1001,name = "杜鹃园",latitude = 30.52309, longitude = 114.347922,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.523379, longitude = 114.347158,
//                        area = "西院", aliases = listOf(" "), note = "停车场"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍1",latitude = 30.523489, longitude = 114.347527,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍2",latitude = 30.523717, longitude = 114.347482,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院6栋",latitude = 30.523663, longitude = 114.346842,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "飞马广场",latitude = 30.522004, longitude = 114.348535,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "腾飞楼",latitude = 30.522337, longitude = 114.348543,
//                        area = "西院", aliases = listOf("纪委办公室、监察处、巡察办","党委统战部","党政办公室","党委组织部（党校）","党委宣传部"), note = "建筑，部门"),
//                    LocationEntity(0, buildId = 1001,name = "西院大礼堂",latitude = 30.522693, longitude = 114.348597,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东品二号楼",latitude = 30.521889, longitude = 114.349617,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东品三号楼",latitude = 30.521916, longitude = 114.350351,
//                        area = "西院", aliases = listOf("科学技术发展院"), note = "建筑,部门"),
//                    LocationEntity(0, buildId = 1001,name = "东品一号楼",latitude = 30.522206, longitude = 114.349874,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "珞樱楼",latitude = 30.522418, longitude = 114.350525,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东教学楼",latitude = 30.522515, longitude = 114.349443,
//                        area = "西院", aliases = listOf("土木工程与建筑学院"), note = "教学楼，学院"),
//                    LocationEntity(0, buildId = 1001,name = "后勤集团办公楼",latitude = 30.523329, longitude = 114.348338,
//                        area = "西院", aliases = listOf("后勤（集团）总公司"), note = "建筑，单位"),
//                    LocationEntity(0, buildId = 1001,name = "校友之家",latitude = 30.523161, longitude = 114.348373,
//                        area = "西院", aliases = listOf("社会合作与校友工作处"), note = "建筑，部门"),
//                    LocationEntity(0, buildId = 1001,name = "后勤集团办公楼",latitude = 30.523319, longitude = 114.34835,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "5号楼",latitude = 30.523629, longitude = 114.34834,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "院士楼",latitude = 30.524021, longitude = 114.348336,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍8",latitude = 30.524507, longitude = 114.34823,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院小北门",latitude = 30.524999, longitude = 114.347682,
//                        area = "西院", aliases = listOf(" "), note = "校门"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍9",latitude = 30.523412, longitude = 114.348879,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍10",latitude = 30.523656, longitude = 114.348967,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "后勤集团教工楼",latitude = 30.523965, longitude = 114.348859,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍12",latitude = 30.524355, longitude = 114.348894,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍13",latitude = 30.524566, longitude = 114.348925,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院老小学（楼群3栋）",latitude = 30.524798, longitude = 114.348822,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院14",latitude = 30.523034, longitude = 114.349415,
//                        area = "西院", aliases = listOf("中国工商银行（武汉理工大学新区支行）"), note = "建筑,银行"),
//                    LocationEntity(0, buildId = 1001,name = "教育超市",latitude = 30.523044, longitude = 114.349674,
//                        area = "西院", aliases = listOf("西院老年生活服务中心"), note = "生活,超市"),
//                    LocationEntity(0, buildId = 1001,name = "门球场",latitude = 30.523069, longitude = 114.349862,
//                        area = "西院", aliases = listOf("马房山校区西院门球场"), note = "体育"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍14",latitude = 30.523278, longitude = 114.349622,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍15",latitude = 30.52392, longitude = 114.349503,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.52411, longitude = 114.349401,
//                        area = "西院", aliases = listOf(" "), note = "停车场"),
//                    LocationEntity(0, buildId = 1001,name = "离退休工作处西院办公室",latitude = 30.524351, longitude = 114.349407,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍16",latitude = 30.523422, longitude = 114.349971,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍21",latitude = 30.523742, longitude = 114.34985,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍18",latitude = 30.52408, longitude = 114.349903,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍20",latitude = 30.523148, longitude = 114.350558,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍19",latitude = 30.523458, longitude = 114.350593,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍30",latitude = 30.524135, longitude = 114.350593,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍31",latitude = 30.524475, longitude = 114.35066,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "逸夫楼",latitude = 30.522384, longitude = 114.351212,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍24",latitude = 30.522695, longitude = 114.35146,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "润章楼",latitude = 30.52285, longitude = 114.351269,
//                        area = "西院", aliases = listOf("新材料研究所（材料复合新技术国家重点研究室）"), note = "建筑，科研基地"),
//                    LocationEntity(0, buildId = 1001,name = "西院幼儿园（楼群4栋）",latitude = 30.523319, longitude = 114.351372,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍26",latitude = 30.523234, longitude = 114.351646,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍29",latitude = 30.52391, longitude = 114.351198,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍28",latitude = 30.523755, longitude = 114.35189,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西院教工宿舍32",latitude = 30.524234, longitude = 114.351763,
//                        area = "西院", aliases = listOf(" "), note = "建筑"),
                    LocationEntity(0, buildId=3001, name="武汉理工大学马房山校区西院", latitude=30.521886, longitude=114.348732, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3002, name="西院南1门", latitude=30.518282, longitude=114.346319, area="西院", aliases=listOf(" "), note="校门"),
                    LocationEntity(0, buildId=3003, name="西院南2门", latitude=30.51783, longitude=114.347507, area="西院", aliases=listOf(" "), note="校门"),
                    LocationEntity(0, buildId=3004, name="西院体育馆", latitude=30.518061, longitude=114.34788, area="西院", aliases=listOf(" "), note="体育"),
                    LocationEntity(0, buildId=3005, name="西院足球场", latitude=30.518705, longitude=114.347005, area="西院", aliases=listOf("马房山校区西院足球场"), note="体育"),
                    LocationEntity(0, buildId=3006, name="教职工宿舍", latitude=30.518603, longitude=114.348636, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3007, name="西院14舍", latitude=30.518578, longitude=114.348359, area="西院", aliases=listOf(" "), note="宿舍"),
                    LocationEntity(0, buildId=3008, name="西院博士后公寓", latitude=30.51826, longitude=114.348737, area="西院", aliases=listOf(" "), note="宿舍"),
                    LocationEntity(0, buildId=3009, name="1号楼", latitude=30.517838, longitude=114.350005, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3010, name="西4舍", latitude=30.519843, longitude=114.346904, area="西院", aliases=listOf(" "), note="宿舍"),
                    LocationEntity(0, buildId=3011, name="网球场", latitude=30.520179, longitude=114.346863, area="西院", aliases=listOf("马房山校区西院网球场"), note="体育"),
                    LocationEntity(0, buildId=3012, name="篮球场", latitude=30.520339, longitude=114.347512, area="西院", aliases=listOf(" "), note="体育"),
                    LocationEntity(0, buildId=3013, name="停车场", latitude=30.52009, longitude=114.347525, area="西院", aliases=listOf(" "), note="停车场"),
                    LocationEntity(0, buildId=3014, name="西区59号楼", latitude=30.520095, longitude=114.347767, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3015, name="西区校园服务中心", latitude=30.519878, longitude=114.347912, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3016, name="西院58栋", latitude=30.519996, longitude=114.348226, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3017, name="57号楼", latitude=30.520056, longitude=114.348718, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3018, name="西3舍", latitude=30.520606, longitude=114.346865, area="西院", aliases=listOf(" "), note="宿舍"),
                    LocationEntity(0, buildId=3019, name="西2舍", latitude=30.520906, longitude=114.346844, area="西院", aliases=listOf(" "), note="宿舍"),
                    LocationEntity(0, buildId=3020, name="西1舍", latitude=30.521175, longitude=114.346899, area="西院", aliases=listOf(" "), note="宿舍"),
                    LocationEntity(0, buildId=3021, name="西区篮球场", latitude=30.521419, longitude=114.346992, area="西院", aliases=listOf(" "), note="体育"),
                    LocationEntity(0, buildId=3022, name="西实验楼", latitude=30.521225, longitude=114.34782, area="西院", aliases=listOf("资源与环境工程学院"), note="教学楼，学院"),
                    LocationEntity(0, buildId=3023, name="恬苑食堂", latitude=30.520551, longitude=114.347973, area="西院", aliases=listOf(" "), note="生活,餐饮"),
                    LocationEntity(0, buildId=3024, name="工会委员会", latitude=30.520325, longitude=114.348703, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3025, name="教职工活动中心", latitude=30.520665, longitude=114.348693, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3026, name="工大楼", latitude=30.521476, longitude=114.348541, area="西院", aliases=listOf("材料科学与工程学院"), note="教学楼,学院"),
                    LocationEntity(0, buildId=3027, name="工大楼西配楼", latitude=30.521249, longitude=114.348252, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3028, name="材料大楼附楼", latitude=30.521264, longitude=114.348612, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3029, name="工大楼东配楼", latitude=30.521284, longitude=114.348947, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3030, name="工大楼南配楼", latitude=30.520973, longitude=114.348426, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3031, name="工大楼南配楼", latitude=30.520968, longitude=114.348838, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3032, name="东实验楼", latitude=30.521474, longitude=114.34951, area="西院", aliases=listOf(" "), note="教学楼"),
                    LocationEntity(0, buildId=3033, name="材料实验中心附楼", latitude=30.521252, longitude=114.349514, area="西院", aliases=listOf(" "), note="教学楼"),
                    LocationEntity(0, buildId=3034, name="东实验楼南楼", latitude=30.520985, longitude=114.349543, area="西院", aliases=listOf("材料研究与测试中心"), note="教学楼,单位"),
                    LocationEntity(0, buildId=3035, name="高温高压物理实验室", latitude=30.520702, longitude=114.349781, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3036, name="西院超市", latitude=30.520467, longitude=114.349928, area="西院", aliases=listOf("教育超市-西院店", "西院快递服务中心（马房山校区西院)"), note="生活,超市，快递"),
                    LocationEntity(0, buildId=3037, name="西8舍", latitude=30.520329, longitude=114.350603, area="西院", aliases=listOf(" "), note="宿舍"),
                    LocationEntity(0, buildId=3038, name="德生楼", latitude=30.521178, longitude=114.350201, area="西院", aliases=listOf(" "), note="教学楼"),
                    LocationEntity(0, buildId=3039, name="西院大门", latitude=30.52174, longitude=114.350718, area="西院", aliases=listOf(" "), note="校门"),
                    LocationEntity(0, buildId=3040, name="西品二号楼", latitude=30.5218, longitude=114.34691, area="西院", aliases=listOf("研究生院"), note="建筑,部门"),
                    LocationEntity(0, buildId=3041, name="西品三号楼", latitude=30.521835, longitude=114.347666, area="西院", aliases=listOf("国有资产与实验室管理处", "人事处"), note="建筑，部门"),
                    LocationEntity(0, buildId=3042, name="西院36号", latitude=30.521904, longitude=114.346596, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3043, name="西院留学生公寓", latitude=30.522113, longitude=114.346676, area="西院", aliases=listOf(" "), note="宿舍"),
                    LocationEntity(0, buildId=3044, name="西品一号楼", latitude=30.522118, longitude=114.347246, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3045, name="停车场", latitude=30.522146, longitude=114.348035, area="西院", aliases=listOf(" "), note="停车场"),
                    LocationEntity(0, buildId=3046, name="绿化中心", latitude=30.522308, longitude=114.346479, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3047, name="西教学楼", latitude=30.522691, longitude=114.347139, area="西院", aliases=listOf(" "), note="教学楼"),
                    LocationEntity(0, buildId=3048, name="西院图书馆", latitude=30.522661, longitude=114.347746, area="西院", aliases=listOf("材料科学与工程国际化学院"), note="建筑,学院"),
                    LocationEntity(0, buildId=3049, name="西院校医院（楼群2栋）", latitude=30.5231, longitude=114.347445, area="西院", aliases=listOf("西院校医院"), note="建筑,医疗"),
                    LocationEntity(0, buildId=3050, name="杜鹃园", latitude=30.52309, longitude=114.347922, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3051, name="停车场", latitude=30.523379, longitude=114.347158, area="西院", aliases=listOf(" "), note="停车场"),
                    LocationEntity(0, buildId=3052, name="西院教工宿舍1", latitude=30.523489, longitude=114.347527, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3053, name="西院教工宿舍2", latitude=30.523717, longitude=114.347482, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3054, name="西院6栋", latitude=30.523663, longitude=114.346842, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3055, name="飞马广场", latitude=30.522004, longitude=114.348535, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3056, name="腾飞楼", latitude=30.522337, longitude=114.348543, area="西院", aliases=listOf("纪委办公室、监察处、巡察办", "党委统战部", "党政办公室", "党委组织部（党校）", "党委宣传部"), note="建筑，部门"),
                    LocationEntity(0, buildId=3057, name="西院大礼堂", latitude=30.522693, longitude=114.348597, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3058, name="东品二号楼", latitude=30.521889, longitude=114.349617, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3059, name="东品三号楼", latitude=30.521916, longitude=114.350351, area="西院", aliases=listOf("科学技术发展院"), note="建筑,部门"),
                    LocationEntity(0, buildId=3060, name="东品一号楼", latitude=30.522206, longitude=114.349874, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3061, name="珞樱楼", latitude=30.522418, longitude=114.350525, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3062, name="东教学楼", latitude=30.522515, longitude=114.349443, area="西院", aliases=listOf("土木工程与建筑学院"), note="教学楼，学院"),
                    LocationEntity(0, buildId=3063, name="后勤集团办公楼", latitude=30.523329, longitude=114.348338, area="西院", aliases=listOf("后勤（集团）总公司"), note="建筑，单位"),
                    LocationEntity(0, buildId=3064, name="校友之家", latitude=30.523161, longitude=114.348373, area="西院", aliases=listOf("社会合作与校友工作处"), note="建筑，部门"),
                    LocationEntity(0, buildId=3065, name="后勤集团办公楼", latitude=30.523319, longitude=114.34835, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3066, name="5号楼", latitude=30.523629, longitude=114.34834, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3067, name="院士楼", latitude=30.524021, longitude=114.348336, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3068, name="西院教工宿舍8", latitude=30.524507, longitude=114.34823, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3069, name="西院小北门", latitude=30.524999, longitude=114.347682, area="西院", aliases=listOf(" "), note="校门"),
                    LocationEntity(0, buildId=3070, name="西院教工宿舍9", latitude=30.523412, longitude=114.348879, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3071, name="西院教工宿舍10", latitude=30.523656, longitude=114.348967, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3072, name="后勤集团教工楼", latitude=30.523965, longitude=114.348859, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3073, name="西院教工宿舍12", latitude=30.524355, longitude=114.348894, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3074, name="西院教工宿舍13", latitude=30.524566, longitude=114.348925, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3075, name="西院老小学（楼群3栋）", latitude=30.524798, longitude=114.348822, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3076, name="西院14", latitude=30.523034, longitude=114.349415, area="西院", aliases=listOf("中国工商银行（武汉理工大学新区支行）"), note="建筑,银行"),
                    LocationEntity(0, buildId=3077, name="教育超市", latitude=30.523044, longitude=114.349674, area="西院", aliases=listOf("西院老年生活服务中心"), note="生活,超市"),
                    LocationEntity(0, buildId=3078, name="门球场", latitude=30.523069, longitude=114.349862, area="西院", aliases=listOf("马房山校区西院门球场"), note="体育"),
                    LocationEntity(0, buildId=3079, name="西院教工宿舍14", latitude=30.523278, longitude=114.349622, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3080, name="西院教工宿舍15", latitude=30.52392, longitude=114.349503, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3081, name="停车场", latitude=30.52411, longitude=114.349401, area="西院", aliases=listOf(" "), note="停车场"),
                    LocationEntity(0, buildId=3082, name="离退休工作处西院办公室", latitude=30.524351, longitude=114.349407, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3083, name="西院教工宿舍16", latitude=30.523422, longitude=114.349971, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3084, name="西院教工宿舍21", latitude=30.523742, longitude=114.34985, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3085, name="西院教工宿舍18", latitude=30.52408, longitude=114.349903, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3086, name="西院教工宿舍20", latitude=30.523148, longitude=114.350558, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3087, name="西院教工宿舍19", latitude=30.523458, longitude=114.350593, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3088, name="西院教工宿舍30", latitude=30.524135, longitude=114.350593, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3089, name="西院教工宿舍31", latitude=30.524475, longitude=114.35066, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3090, name="逸夫楼", latitude=30.522384, longitude=114.351212, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3091, name="西院教工宿舍24", latitude=30.522695, longitude=114.35146, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3092, name="润章楼", latitude=30.52285, longitude=114.351269, area="西院", aliases=listOf("新材料研究所（材料复合新技术国家重点研究室）"), note="建筑，科研基地"),
                    LocationEntity(0, buildId=3093, name="西院幼儿园（楼群4栋）", latitude=30.523319, longitude=114.351372, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3094, name="西院教工宿舍26", latitude=30.523234, longitude=114.351646, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3095, name="西院教工宿舍29", latitude=30.52391, longitude=114.351198, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3096, name="西院教工宿舍28", latitude=30.523755, longitude=114.35189, area="西院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=3097, name="西院教工宿舍32", latitude=30.524234, longitude=114.351763, area="西院", aliases=listOf(" "), note="建筑"),

//                    LocationEntity(3000, buildId = 1001,name = "武汉理工大学马房山校区东院",latitude = 30.51869, longitude = 114.353814,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "大学生创新创业中心",latitude = 30.523891, longitude = 114.352784,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "珞东教师小区",latitude = 30.523704, longitude = 114.353235,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "水泥混凝土实验楼",latitude = 30.523382, longitude = 114.354307,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "孵化楼一期",latitude = 30.523043, longitude = 114.352478,
//                        area = "东院", aliases = listOf("中国建设银行（武汉新华支行）","产业集团 (资产经营公司)"), note = "建筑,银行,单位"),
//                    LocationEntity(0, buildId = 1001,name = "马房山大厦",latitude = 30.522166, longitude = 114.352195,
//                        area = "东院", aliases = listOf("设计研究院","出版社"), note = "建筑,单位"),
//                    LocationEntity(0, buildId = 1001,name = "硅酸盐国家重点实验室",latitude = 30.522947, longitude = 114.353839,
//                        area = "东院", aliases = listOf("硅酸盐材料工程研究中心（硅酸盐建筑材料国家重点实验室）"), note = "建筑,科研基地"),
//                    LocationEntity(0, buildId = 1001,name = "土木工程结构实验室",latitude = 30.522656, longitude = 114.35322,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.52293, longitude = 114.352855,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "厂区配电房",latitude = 30.522464, longitude = 114.353087,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "材料学院复材楼",latitude = 30.522543, longitude = 114.353819,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "新材料研究所SHS实验室",latitude = 30.52225, longitude = 114.353556,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "陶瓷中试车间实验室",latitude = 30.522775, longitude = 114.354434,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "特种功能材料技术教育部重点实验室",latitude = 30.522578, longitude = 114.354573,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "材料成型与加工实验楼",latitude = 30.522008, longitude = 114.352913,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院综合实验楼",latitude = 30.522042, longitude = 114.353563,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "材料涂层实验室",latitude = 30.521949, longitude = 114.354231,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "老光纤楼2",latitude = 30.5218, longitude = 114.355,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院大门",latitude = 30.521715, longitude = 114.352173,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.521669, longitude = 114.352374,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院水电收费点",latitude = 30.521588, longitude = 114.352183,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院第五配电房",latitude = 30.521491, longitude = 114.352538,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.521326, longitude = 114.352036,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院7舍",latitude = 30.520964, longitude = 114.352353,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.521594, longitude = 114.352985,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "求实楼西附楼",latitude = 30.52125, longitude = 114.353024,
//                        area = "东院", aliases = listOf("继续教育学院,档案馆"), note = "建筑，学院，单位"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.521602, longitude = 114.353702,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.521397, longitude = 114.353723,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "求实楼",latitude =30.521028, longitude = 114.35362,
//                        area = "东院", aliases = listOf("自动化学院","教务处","财务处","教育科学研究院"), note = "建筑,学院,部门,单位"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.520889, longitude = 114.353657,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.521509, longitude = 114.354421,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "求实楼东附楼",latitude = 30.521197, longitude = 114.354452,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.521012, longitude = 114.354481,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院101",latitude = 30.520927, longitude = 114.355387,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.520673, longitude = 114.351695,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "篮球场",latitude = 30.520386, longitude = 114.351712,
//                        area = "东院", aliases = listOf("马房山校区东院篮球场"), note = "体育"),
//                    LocationEntity(0, buildId = 1001,name = "思源楼",latitude = 30.520638, longitude = 114.352312,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "莘子苑食堂",latitude = 30.520243, longitude = 114.352534,
//                        area = "东院", aliases = listOf(" "), note = "生活,餐饮"),
//                    LocationEntity(0, buildId = 1001,name = "东8舍",latitude = 30.519911, longitude = 114.352261,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院校医院",latitude = 30.519582, longitude = 114.351863,
//                        area = "东院", aliases = listOf("东院校医院"), note = "建筑,医疗"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.519672, longitude = 114.352437,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院9舍",latitude = 30.519361, longitude = 114.352566,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院产业大楼",latitude =30.518948, longitude = 114.351154,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院车队（楼群3栋）",latitude = 30.518872, longitude = 114.35154,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院浴室",latitude = 30.518715, longitude = 114.352046,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院超市",latitude = 30.518705, longitude = 114.352417,
//                        area = "东院", aliases = listOf("教育超市-东院店","菜鸟驿站（马房山校区东院）"), note = "生活,超市，快递"),
//                    LocationEntity(0, buildId = 1001,name = "东院43",latitude = 30.518842, longitude = 114.352669,
//                        area = "东院", aliases = listOf("中国工商银行（武汉理工大支行）","校园一卡通服务中心（马区东院）"), note = "建筑，银行,一卡通"),
//                    LocationEntity(0, buildId = 1001,name = "思源广场",latitude = 30.520434, longitude = 114.353044,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东1舍",latitude = 30.520127, longitude = 114.353079,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东2舍",latitude = 30.519893, longitude = 114.353126,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东3舍",latitude = 30.519598, longitude = 114.353142,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东4舍",latitude = 30.51926, longitude = 114.353127,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东5舍",latitude = 30.518978, longitude = 114.353142,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东6舍",latitude = 30.51871, longitude = 114.353142,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "武工楼",latitude =30.520015, longitude = 114.35425,
//                        area = "东院", aliases = listOf("国际交流与合作处(港澳台办公室)","教学督导与质量管理办公室","审计处","基建处","发展规划与改革办公室","科技合作与成果转化中心"), note = "建筑，部门，单位"),
//                    LocationEntity(0, buildId = 1001,name = "自动化学院专业综合实验教学中心",latitude = 30.520768, longitude = 114.354563,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东10舍",latitude = 30.520261, longitude = 114.355286,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "弘毅楼",latitude = 30.520042, longitude = 114.354967,
//                        area = "东院", aliases = listOf("马克思主义学院","国际教育学院","法学与人文社会学院"), note = "建筑，学院"),
//                    LocationEntity(0, buildId = 1001,name = "东院留学生公寓",latitude = 30.520049, longitude = 114.356128,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "青年教师公寓",latitude = 30.519865, longitude = 114.356768,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.520208, longitude = 114.356666,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.519625, longitude = 114.356778,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院小东门",latitude = 30.519556, longitude = 114.356749,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "临桂苑食堂",latitude = 30.519388, longitude = 114.356669,
//                        area = "东院", aliases = listOf("博雅园餐厅"), note = "生活,餐饮"),
//                    LocationEntity(0, buildId = 1001,name = "东院图书馆",latitude = 30.519148, longitude = 114.353913,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院体育馆",latitude = 30.517735, longitude = 114.353852,
//                        area = "东院", aliases = listOf("体育部"), note = "建筑,学院"),
//                    LocationEntity(0, buildId = 1001,name = "致远楼",latitude = 30.519231, longitude = 114.355441,
//                        area = "东院", aliases = listOf("东院第一教学楼","自习室：教1-511,教1-512,教1-211,教1-212","离退休党委、离退休工作处"), note = "建筑,自习室,部门"),
//                    LocationEntity(0, buildId = 1001,name = "致远楼附楼",latitude = 30.518798, longitude = 114.355109,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院工程训练中心",latitude = 30.518351, longitude = 114.354684,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "机电学院实验中心北楼",latitude = 30.518461, longitude = 114.355045,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "机电学院实验中心南楼",latitude = 30.518275, longitude = 114.355229,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东51",latitude = 30.51842, longitude = 114.355628,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东58",latitude = 30.518263, longitude = 114.355735,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东6区",latitude = 30.519201, longitude = 114.356131,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东7区",latitude = 30.518952, longitude = 114.356227,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东8区",latitude = 30.518717, longitude = 114.356235,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东9区",latitude = 30.518461, longitude = 114.356198,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东10区",latitude = 30.518199, longitude = 114.356209,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院西南门",latitude = 30.518602, longitude = 114.351081,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院57",latitude = 30.518484, longitude = 114.350992,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西23区",latitude = 30.518173, longitude = 114.350971,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西22区",latitude = 30.517878, longitude = 114.350979,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西21区",latitude = 30.517555, longitude = 114.350963,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西20区",latitude = 30.517246, longitude = 114.351067,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西19区",latitude = 30.516978, longitude = 114.351069,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西18区",latitude = 30.516709, longitude = 114.351027,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "马区综合服务中心",latitude = 30.518484, longitude = 114.351442,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西17区",latitude = 30.518216, longitude = 114.351452,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西16区",latitude = 30.518032, longitude = 114.351497,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西15区",latitude = 30.517739, longitude = 114.351457,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西14区",latitude = 30.517197, longitude = 114.351398,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "老年活动中心",latitude = 30.516833, longitude = 114.351685,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西26区",latitude = 30.516515, longitude = 114.351861,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西27区",latitude = 30.516727, longitude = 114.351995,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西10区",latitude = 30.517011, longitude = 114.351968,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西8区",latitude = 30.51731, longitude = 114.351984,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西6区",latitude = 30.51758, longitude = 114.351912,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.517833, longitude = 114.352048,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西28区",latitude = 30.518055, longitude = 114.352436,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.518525, longitude = 114.351851,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "就业中心楼",latitude = 30.518458, longitude = 114.352436,
//                        area = "东院", aliases = listOf("创业学院","工会"), note = "建筑，学院,部门"),
//                    LocationEntity(0, buildId = 1001,name = "篮球场",latitude = 30.518306, longitude = 114.353113,
//                        area = "东院", aliases = listOf("马房山校区东院灯光篮球场"), note = "体育"),
//                    LocationEntity(0, buildId = 1001,name = "西5区",latitude = 30.517585, longitude = 114.352323,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院幼儿园",latitude = 30.517247, longitude = 114.352454,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西4区",latitude = 30.51758, longitude = 114.352864,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西7区",latitude = 30.517291, longitude = 114.352991,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西9区",latitude = 30.516981, longitude = 114.353033,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西25区",latitude = 30.516683, longitude = 114.352962,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西24区",latitude =30.516378, longitude =  114.352896,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西11区",latitude = 30.516058, longitude = 114.353033,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "汽车学院新实验楼",latitude = 30.515493, longitude = 114.353131,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "汽车零部件实验室",latitude = 30.515101, longitude = 114.352947,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = ""引擎"大学生创新创业实践基地",latitude = 30.514384, longitude = 114.352971,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "机电学院楼",latitude = 30.517764, longitude = 114.354897,
//                        area = "东院", aliases = listOf("机电工程学院"), note = "建筑,学院"),
//                    LocationEntity(0, buildId = 1001,name = "东院足球场",latitude = 30.516392, longitude = 114.353827,
//                        area = "东院", aliases = listOf("马房山校区东院足球场"), note = "体育"),
//                    LocationEntity(0, buildId = 1001,name = "东院体院场南器材室",latitude = 30.515555, longitude = 114.353871,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "后保处办公楼",latitude = 30.517534, longitude = 114.354611,
//                        area = "东院", aliases = listOf("后勤保障处"), note = "建筑,部门"),
//                    LocationEntity(0, buildId = 1001,name = "东65",latitude = 30.517575, longitude = 114.355021,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东63",latitude = 30.517544, longitude = 114.355384,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "中心配电房",latitude = 30.517311, longitude = 114.355024,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东66",latitude = 30.517285, longitude = 114.355348,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东69",latitude = 30.517191, longitude = 114.354989,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东70",latitude = 30.516955, longitude = 114.355574,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东71",latitude = 30.516976, longitude = 114.355215,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院网球场",latitude = 30.516433, longitude = 114.354673,
//                        area = "东院", aliases = listOf("马房山校区东院网球场"), note = "体育"),
//                    LocationEntity(0, buildId = 1001,name = "东73",latitude = 30.516177, longitude = 114.355044,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "同力智能",latitude = 30.515824, longitude = 114.354697,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院网球场",latitude = 30.515742, longitude = 114.355074,
//                        area = "东院", aliases = listOf("马房山校区东院网球场"), note = "体育"),
//                    LocationEntity(0, buildId = 1001,name = "南1区",latitude = 30.516648, longitude = 114.355392,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "南2区",latitude = 30.516436, longitude = 114.355446,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "南3区",latitude = 30.516172, longitude = 114.355392,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "南4区",latitude = 30.515873, longitude = 114.35546,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "南5区",latitude = 30.515578, longitude = 114.355481,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "南6区",latitude = 30.515865, longitude = 114.356343,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "南7区",latitude = 30.515617, longitude = 114.35631,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "南8区",latitude = 30.515066, longitude = 114.355266,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.515372, longitude = 114.353942,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "神龙园",latitude = 30.514848, longitude = 114.353645,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.514871, longitude = 114.353752,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.514779, longitude = 114.353356,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.514434, longitude = 114.353351,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "汽车学院楼",latitude = 30.514058, longitude = 114.353551,
//                        area = "东院", aliases = listOf("汽车工程学院"), note = "建筑，学院"),
//                    LocationEntity(0, buildId = 1001,name = "汽车测试中心",latitude = 30.514678, longitude = 114.354656,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "汽车学院实验室",latitude = 30.514295, longitude = 114.354602,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东11舍",latitude = 30.514477, longitude = 114.355156,
//                        area = "东院", aliases = listOf("安全科学与应急管理学院"), note = "建筑,学院"),
//                    LocationEntity(0, buildId = 1001,name = "玉成楼",latitude = 30.514512, longitude = 114.355501,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "应急管理研究中心",latitude = 30.514265, longitude = 114.355528,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "应急管理研究中心",latitude = 30.514134, longitude = 114.354998,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院12舍",latitude = 30.513957, longitude = 114.355491,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "南门门房",latitude = 30.513892, longitude = 114.354667,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "东院南门",latitude = 30.513811, longitude = 114.354728,
//                        area = "东院", aliases = listOf(" "), note = "建筑"),
                    LocationEntity(0, buildId=4001, name="武汉理工大学马房山校区东院", latitude=30.51869, longitude=114.353814, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4002, name="大学生创新创业中心", latitude=30.523891, longitude=114.352784, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4003, name="珞东教师小区", latitude=30.523704, longitude=114.353235, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4004, name="水泥混凝土实验楼", latitude=30.523382, longitude=114.354307, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4005, name="孵化楼一期", latitude=30.523043, longitude=114.352478, area="东院", aliases=listOf("中国建设银行（武汉新华支行）", "产业集团 (资产经营公司)"), note="建筑,银行,单位"),
                    LocationEntity(0, buildId=4006, name="马房山大厦", latitude=30.522166, longitude=114.352195, area="东院", aliases=listOf("设计研究院", "出版社"), note="建筑,单位"),
                    LocationEntity(0, buildId=4007, name="硅酸盐国家重点实验室", latitude=30.522947, longitude=114.353839, area="东院", aliases=listOf("硅酸盐材料工程研究中心（硅酸盐建筑材料国家重点实验室）"), note="建筑,科研基地"),
                    LocationEntity(0, buildId=4008, name="土木工程结构实验室", latitude=30.522656, longitude=114.35322, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4009, name="停车场", latitude=30.52293, longitude=114.352855, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4010, name="厂区配电房", latitude=30.522464, longitude=114.353087, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4011, name="材料学院复材楼", latitude=30.522543, longitude=114.353819, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4012, name="新材料研究所SHS实验室", latitude=30.52225, longitude=114.353556, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4013, name="陶瓷中试车间实验室", latitude=30.522775, longitude=114.354434, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4014, name="特种功能材料技术教育部重点实验室", latitude=30.522578, longitude=114.354573, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4015, name="材料成型与加工实验楼", latitude=30.522008, longitude=114.352913, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4016, name="东院综合实验楼", latitude=30.522042, longitude=114.353563, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4017, name="材料涂层实验室", latitude=30.521949, longitude=114.354231, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4018, name="老光纤楼2", latitude=30.5218, longitude=114.355, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4019, name="东院大门", latitude=30.521715, longitude=114.352173, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4020, name="停车场", latitude=30.521669, longitude=114.352374, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4021, name="东院水电收费点", latitude=30.521588, longitude=114.352183, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4022, name="东院第五配电房", latitude=30.521491, longitude=114.352538, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4023, name="停车场", latitude=30.521326, longitude=114.352036, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4024, name="东院7舍", latitude=30.520964, longitude=114.352353, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4025, name="停车场", latitude=30.521594, longitude=114.352985, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4026, name="求实楼西附楼", latitude=30.52125, longitude=114.353024, area="东院", aliases=listOf("继续教育学院,档案馆"), note="建筑，学院，单位"),
                    LocationEntity(0, buildId=4027, name="停车场", latitude=30.521602, longitude=114.353702, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4028, name="停车场", latitude=30.521397, longitude=114.353723, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4029, name="求实楼", latitude=30.521028, longitude=114.35362, area="东院", aliases=listOf("自动化学院", "教务处", "财务处", "教育科学研究院"), note="建筑,学院,部门,单位"),
                    LocationEntity(0, buildId=4030, name="停车场", latitude=30.520889, longitude=114.353657, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4031, name="停车场", latitude=30.521509, longitude=114.354421, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4032, name="求实楼东附楼", latitude=30.521197, longitude=114.354452, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4033, name="停车场", latitude=30.521012, longitude=114.354481, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4034, name="东院101", latitude=30.520927, longitude=114.355387, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4035, name="停车场", latitude=30.520673, longitude=114.351695, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4036, name="篮球场", latitude=30.520386, longitude=114.351712, area="东院", aliases=listOf("马房山校区东院篮球场"), note="体育"),
                    LocationEntity(0, buildId=4037, name="思源楼", latitude=30.520638, longitude=114.352312, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4038, name="莘子苑食堂", latitude=30.520243, longitude=114.352534, area="东院", aliases=listOf(" "), note="生活,餐饮"),
                    LocationEntity(0, buildId=4039, name="东8舍", latitude=30.519911, longitude=114.352261, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4040, name="东院校医院", latitude=30.519582, longitude=114.351863, area="东院", aliases=listOf("东院校医院"), note="建筑,医疗"),
                    LocationEntity(0, buildId=4041, name="停车场", latitude=30.519672, longitude=114.352437, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4042, name="东院9舍", latitude=30.519361, longitude=114.352566, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4043, name="东院产业大楼", latitude=30.518948, longitude=114.351154, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4044, name="东院车队（楼群3栋）", latitude=30.518872, longitude=114.35154, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4045, name="东院浴室", latitude=30.518715, longitude=114.352046, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4046, name="东院超市", latitude=30.518705, longitude=114.352417, area="东院", aliases=listOf("教育超市-东院店", "菜鸟驿站（马房山校区东院）"), note="生活,超市，快递"),
                    LocationEntity(0, buildId=4047, name="东院43", latitude=30.518842, longitude=114.352669, area="东院", aliases=listOf("中国工商银行（武汉理工大支行）", "校园一卡通服务中心（马区东院）"), note="建筑，银行,一卡通"),
                    LocationEntity(0, buildId=4048, name="思源广场", latitude=30.520434, longitude=114.353044, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4049, name="东1舍", latitude=30.520127, longitude=114.353079, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4050, name="东2舍", latitude=30.519893, longitude=114.353126, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4051, name="东3舍", latitude=30.519598, longitude=114.353142, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4052, name="东4舍", latitude=30.51926, longitude=114.353127, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4053, name="东5舍", latitude=30.518978, longitude=114.353142, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4054, name="东6舍", latitude=30.51871, longitude=114.353142, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4055, name="武工楼", latitude=30.520015, longitude=114.35425, area="东院", aliases=listOf("国际交流与合作处(港澳台办公室)", "教学督导与质量管理办公室", "审计处", "基建处", "发展规划与改革办公室", "科技合作与成果转化中心"), note="建筑，部门，单位"),
                    LocationEntity(0, buildId=4056, name="自动化学院专业综合实验教学中心", latitude=30.520768, longitude=114.354563, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4057, name="东10舍", latitude=30.520261, longitude=114.355286, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4058, name="弘毅楼", latitude=30.520042, longitude=114.354967, area="东院", aliases=listOf("马克思主义学院", "国际教育学院", "法学与人文社会学院"), note="建筑，学院"),
                    LocationEntity(0, buildId=4059, name="东院留学生公寓", latitude=30.520049, longitude=114.356128, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4060, name="青年教师公寓", latitude=30.519865, longitude=114.356768, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4061, name="停车场", latitude=30.520208, longitude=114.356666, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4062, name="停车场", latitude=30.519625, longitude=114.356778, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4063, name="东院小东门", latitude=30.519556, longitude=114.356749, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4064, name="临桂苑食堂", latitude=30.519388, longitude=114.356669, area="东院", aliases=listOf("博雅园餐厅"), note="生活,餐饮"),
                    LocationEntity(0, buildId=4065, name="东院图书馆", latitude=30.519148, longitude=114.353913, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4066, name="东院体育馆", latitude=30.517735, longitude=114.353852, area="东院", aliases=listOf("体育部"), note="建筑,学院"),
                    LocationEntity(0, buildId=4067, name="致远楼", latitude=30.519231, longitude=114.355441, area="东院", aliases=listOf("东院第一教学楼", "自习室：教1-511,教1-512,教1-211,教1-212", "离退休党委、离退休工作处"), note="建筑,自习室,部门"),
                    LocationEntity(0, buildId=4068, name="致远楼附楼", latitude=30.518798, longitude=114.355109, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4069, name="东院工程训练中心", latitude=30.518351, longitude=114.354684, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4070, name="机电学院实验中心北楼", latitude=30.518461, longitude=114.355045, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4071, name="机电学院实验中心南楼", latitude=30.518275, longitude=114.355229, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4072, name="东51", latitude=30.51842, longitude=114.355628, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4073, name="东58", latitude=30.518263, longitude=114.355735, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4074, name="东6区", latitude=30.519201, longitude=114.356131, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4075, name="东7区", latitude=30.518952, longitude=114.356227, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4076, name="东8区", latitude=30.518717, longitude=114.356235, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4077, name="东9区", latitude=30.518461, longitude=114.356198, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4078, name="东10区", latitude=30.518199, longitude=114.356209, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4079, name="东院西南门", latitude=30.518602, longitude=114.351081, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4080, name="东院57", latitude=30.518484, longitude=114.350992, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4081, name="西23区", latitude=30.518173, longitude=114.350971, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4082, name="西22区", latitude=30.517878, longitude=114.350979, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4083, name="西21区", latitude=30.517555, longitude=114.350963, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4084, name="西20区", latitude=30.517246, longitude=114.351067, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4085, name="西19区", latitude=30.516978, longitude=114.351069, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4086, name="西18区", latitude=30.516709, longitude=114.351027, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4087, name="马区综合服务中心", latitude=30.518484, longitude=114.351442, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4088, name="西17区", latitude=30.518216, longitude=114.351452, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4089, name="西16区", latitude=30.518032, longitude=114.351497, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4090, name="西15区", latitude=30.517739, longitude=114.351457, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4091, name="西14区", latitude=30.517197, longitude=114.351398, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4092, name="老年活动中心", latitude=30.516833, longitude=114.351685, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4093, name="西26区", latitude=30.516515, longitude=114.351861, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4094, name="西27区", latitude=30.516727, longitude=114.351995, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4095, name="西10区", latitude=30.517011, longitude=114.351968, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4096, name="西8区", latitude=30.51731, longitude=114.351984, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4097, name="西6区", latitude=30.51758, longitude=114.351912, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4098, name="停车场", latitude=30.517833, longitude=114.352048, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4099, name="西28区", latitude=30.518055, longitude=114.352436, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4100, name="停车场", latitude=30.518525, longitude=114.351851, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4101, name="就业中心楼", latitude=30.518458, longitude=114.352436, area="东院", aliases=listOf("创业学院", "工会"), note="建筑，学院,部门"),
                    LocationEntity(0, buildId=4102, name="篮球场", latitude=30.518306, longitude=114.353113, area="东院", aliases=listOf("马房山校区东院灯光篮球场"), note="体育"),
                    LocationEntity(0, buildId=4103, name="西5区", latitude=30.517585, longitude=114.352323, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4104, name="东院幼儿园", latitude=30.517247, longitude=114.352454, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4105, name="西4区", latitude=30.51758, longitude=114.352864, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4106, name="西7区", latitude=30.517291, longitude=114.352991, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4107, name="西9区", latitude=30.516981, longitude=114.353033, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4108, name="西25区", latitude=30.516683, longitude=114.352962, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4109, name="西24区", latitude=30.516378, longitude=114.352896, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4110, name="西11区", latitude=30.516058, longitude=114.353033, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4111, name="汽车学院新实验楼", latitude=30.515493, longitude=114.353131, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4112, name="汽车零部件实验室", latitude=30.515101, longitude=114.352947, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4113, name="'引擎'大学生创新创业实践基地", latitude=30.514384, longitude=114.352971, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4114, name="机电学院楼", latitude=30.517764, longitude=114.354897, area="东院", aliases=listOf("机电工程学院"), note="建筑,学院"),
                    LocationEntity(0, buildId=4115, name="东院足球场", latitude=30.516392, longitude=114.353827, area="东院", aliases=listOf("马房山校区东院足球场"), note="体育"),
                    LocationEntity(0, buildId=4116, name="东院体院场南器材室", latitude=30.515555, longitude=114.353871, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4117, name="后保处办公楼", latitude=30.517534, longitude=114.354611, area="东院", aliases=listOf("后勤保障处"), note="建筑,部门"),
                    LocationEntity(0, buildId=4118, name="东65", latitude=30.517575, longitude=114.355021, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4119, name="东63", latitude=30.517544, longitude=114.355384, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4120, name="中心配电房", latitude=30.517311, longitude=114.355024, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4121, name="东66", latitude=30.517285, longitude=114.355348, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4122, name="东69", latitude=30.517191, longitude=114.354989, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4123, name="东70", latitude=30.516955, longitude=114.355574, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4124, name="东71", latitude=30.516976, longitude=114.355215, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4125, name="东院网球场", latitude=30.516433, longitude=114.354673, area="东院", aliases=listOf("马房山校区东院网球场"), note="体育"),
                    LocationEntity(0, buildId=4126, name="东73", latitude=30.516177, longitude=114.355044, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4127, name="同力智能", latitude=30.515824, longitude=114.354697, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4128, name="东院网球场", latitude=30.515742, longitude=114.355074, area="东院", aliases=listOf("马房山校区东院网球场"), note="体育"),
                    LocationEntity(0, buildId=4129, name="南1区", latitude=30.516648, longitude=114.355392, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4130, name="南2区", latitude=30.516436, longitude=114.355446, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4131, name="南3区", latitude=30.516172, longitude=114.355392, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4132, name="南4区", latitude=30.515873, longitude=114.35546, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4133, name="南5区", latitude=30.515578, longitude=114.355481, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4134, name="南6区", latitude=30.515865, longitude=114.356343, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4135, name="南7区", latitude=30.515617, longitude=114.35631, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4136, name="南8区", latitude=30.515066, longitude=114.355266, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4137, name="停车场", latitude=30.515372, longitude=114.353942, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4138, name="神龙园", latitude=30.514848, longitude=114.353645, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4139, name="停车场", latitude=30.514871, longitude=114.353752, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4140, name="停车场", latitude=30.514779, longitude=114.353356, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4141, name="停车场", latitude=30.514434, longitude=114.353351, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4142, name="汽车学院楼", latitude=30.514058, longitude=114.353551, area="东院", aliases=listOf("汽车工程学院"), note="建筑，学院"),
                    LocationEntity(0, buildId=4143, name="汽车测试中心", latitude=30.514678, longitude=114.354656, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4144, name="汽车学院实验室", latitude=30.514295, longitude=114.354602, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4145, name="东11舍", latitude=30.514477, longitude=114.355156, area="东院", aliases=listOf("安全科学与应急管理学院"), note="建筑,学院"),
                    LocationEntity(0, buildId=4146, name="玉成楼", latitude=30.514512, longitude=114.355501, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4147, name="应急管理研究中心", latitude=30.514265, longitude=114.355528, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4148, name="应急管理研究中心", latitude=30.514134, longitude=114.354998, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4149, name="东院12舍", latitude=30.513957, longitude=114.355491, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4150, name="南门门房", latitude=30.513892, longitude=114.354667, area="东院", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=4151, name="东院南门", latitude=30.513811, longitude=114.354728, area="东院", aliases=listOf(" "), note="建筑"),
                    
//                    LocationEntity(0, buildId = 1001,name = "武汉理工大学余家头校区",latitude = 30.606383, longitude = 114.356614,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "内河航运技术湖北省重点实验室",latitude = 30.601072, longitude = 114.357265,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场",latitude = 30.601335, longitude = 114.357039,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "航海楼",latitude = 30.601856, longitude = 114.357942,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区天文馆",latitude = 30.603027, longitude = 114.358592,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区5号门",latitude = 30.603689, longitude = 114.357764,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区28",latitude = 30.605052, longitude = 114.352411,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区29",latitude = 30.604816, longitude = 114.352665,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区30",latitude = 30.604645, longitude = 114.352867,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区31",latitude = 30.604508, longitude = 114.353039,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区32",latitude = 30.604327, longitude = 114.353232,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "百花园",latitude = 30.604407, longitude = 114.353625,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区35",latitude = 30.603976, longitude = 114.352684,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区23",latitude = 30.603646, longitude = 114.353634,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区车队",latitude = 30.603227, longitude = 114.353677,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "理工大社区居委会",latitude = 30.603578, longitude = 114.353931,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "工程训练中心",latitude = 30.60328, longitude = 114.354612,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区3号门",latitude = 30.602595, longitude = 114.353683,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区西园三舍",latitude = 30.602509, longitude = 114.355259,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西园综合服务部",latitude = 30.602975, longitude = 114.355548,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区西园二舍",latitude = 30.603074, longitude = 114.355855,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区西园4栋",latitude = 30.603406, longitude = 114.355873,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区校医院",latitude = 30.603453, longitude = 114.356268,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "中原平价超市",latitude = 30.603742, longitude = 114.355775,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区14舍B",latitude = 30.603817, longitude = 114.35614,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区14舍A",latitude = 30.603913, longitude = 114.356719,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区4号门",latitude = 30.603854, longitude = 114.356975,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区12舍",latitude = 30.60422, longitude = 114.356507,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区13舍",latitude = 30.60416, longitude = 114.356205,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区9舍",latitude = 30.604574, longitude = 114.356616,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区10舍",latitude = 30.604507, longitude = 114.356386,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区11舍",latitude = 30.604429, longitude = 114.356019,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "后勤集团余区公司",latitude = 30.604726, longitude = 114.356344,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余家头校区后勤集团",latitude = 30.604696, longitude = 114.356057,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "水运一、二食堂",latitude = 30.605025, longitude = 114.356507,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区大学生活动中心",latitude = 30.605469, longitude = 114.356094,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区教职工活动中心",latitude = 30.604004, longitude = 114.355699,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "船员培训中心",latitude = 30.605084, longitude = 114.355585,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区老年活动中心",latitude = 30.605377, longitude = 114.355385,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区公寓楼14栋",latitude = 30.603876, longitude = 114.354903,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区公寓楼13栋",latitude = 30.604114, longitude = 114.354881,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区公寓楼12栋",latitude = 30.604332, longitude = 114.354867,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区公寓楼11栋",latitude = 30.604547, longitude = 114.354813,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区公寓楼10栋",latitude = 30.604752, longitude = 114.354729,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区公寓楼9栋",latitude = 30.604978, longitude = 114.354668,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区公寓楼8栋",latitude = 30.605314, longitude = 114.354532,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区公寓楼7栋",latitude = 30.605525, longitude = 114.354475,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "教工食堂（楼群2栋）",latitude = 30.604938, longitude = 114.353877,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区17",latitude = 30.605306, longitude = 114.353666,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区幼儿园",latitude = 30.605373, longitude = 114.353036,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "大浴室",latitude = 30.605673, longitude = 114.353904,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区16",latitude = 30.605843, longitude = 114.353781,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "学友情餐厅",latitude = 30.605816, longitude = 114.353457,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区公寓楼2栋",latitude = 30.606633, longitude = 114.354208,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区公寓楼1栋",latitude = 30.606787, longitude = 114.354475,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "大众水果副食",latitude = 30.606338, longitude = 114.353424,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "心曲餐厅",latitude = 30.606557, longitude = 114.353714,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区学生宿舍（北园）",latitude = 30.606919, longitude = 114.353474,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区公寓楼3栋",latitude = 30.607444, longitude = 114.354111,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区公寓楼",latitude = 30.6073, longitude = 114.353374,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "体育运动场",latitude = 30.606666, longitude = 114.355472,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "篮球场",latitude = 30.606905, longitude = 114.356174,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "航海体能训练中心",latitude = 30.608224, longitude = 114.354926,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区保卫办",latitude = 30.608423, longitude = 114.355767,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "西合楼",latitude = 30.608887, longitude = 114.355264,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区西配楼",latitude = 30.609244, longitude = 114.355413,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区游泳池管理用房",latitude = 30.60879, longitude = 114.354509,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区2号门",latitude = 30.60896, longitude = 114.353861,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区小车库",latitude = 30.609884, longitude = 114.355432,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区1号门",latitude = 30.610765, longitude = 114.356516,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "水运湖",latitude = 30.610955, longitude = 114.357076,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区05",latitude = 30.609887, longitude = 114.356161,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区创业中心（青桐学院）",latitude = 30.609636, longitude = 114.356178,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区教学大楼",latitude = 30.609165, longitude = 114.356907,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区综合楼",latitude = 30.608422, longitude = 114.356569,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "青年园",latitude = 30.608578, longitude = 114.35742,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "社科楼",latitude = 30.607945, longitude = 114.356668,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "交运楼",latitude = 30.608086, longitude = 114.3576,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "风雨棚",latitude = 30.607524, longitude = 114.356679,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "流体力学楼",latitude = 30.607665, longitude = 114.357635,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "中国银行",latitude = 30.607153, longitude = 114.356464,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "风雨棚附楼",latitude = 30.607223, longitude = 114.356942,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区2舍",latitude = 30.606882, longitude = 114.356866,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区1舍",latitude = 30.606977, longitude = 114.357723,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区4舍",latitude = 30.606435, longitude = 114.356965,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区3舍",latitude = 30.606496, longitude = 114.357705,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区6舍",latitude = 30.605753, longitude = 114.357058,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区5舍",latitude = 30.605924, longitude = 114.357874,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "停车场（才汇巷）",latitude = 30.605387, longitude = 114.3576,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区东配楼",latitude = 30.609648, longitude = 114.358145,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "交通学院楼",latitude = 30.609794, longitude = 114.358818,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "计算机实验中心",latitude = 30.609331, longitude = 114.358394,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "物流液压实验楼",latitude = 30.609476, longitude = 114.35887,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "港口大楼",latitude = 30.608965, longitude = 114.358018,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区16栋",latitude = 30.608959, longitude = 114.358449,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "立体仓库实验楼",latitude = 30.608781, longitude = 114.358204,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "物流装备实验楼",latitude = 30.608594, longitude = 114.357987,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "港机实验楼",latitude = 30.608559, longitude = 114.35859,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "物流学院楼",latitude = 30.608185, longitude = 114.358376,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "焊接楼",latitude = 30.607739, longitude = 114.358294,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "小船池",latitude = 30.607819, longitude = 114.358663,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "能动学院实验楼（楼群4栋）",latitude = 30.607155, longitude = 114.358556,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "交通学院船舶工程系",latitude = 30.606742, longitude = 114.35859,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "动力楼",latitude = 30.606528, longitude = 114.358728,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "水运三食堂",latitude = 30.605641, longitude = 114.358825,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "联盟小区3栋",latitude = 30.609069, longitude = 114.359277,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "联盟小区1栋",latitude = 30.60914, longitude = 114.359657,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "联盟小区4栋",latitude = 30.608822, longitude = 114.359336,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "联盟小区2栋",latitude = 30.608879, longitude = 114.359707,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "联盟小区5栋",latitude = 30.608577, longitude = 114.359163,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "联盟小区6栋",latitude = 30.608605, longitude = 114.359762,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "联盟小区7栋",latitude = 30.608315, longitude = 114.359243,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "联盟小区8栋",latitude = 30.608375, longitude = 114.359788,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "联盟小区9栋",latitude = 30.608044, longitude = 114.35927,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "船舶结构实验室",latitude = 30.608402, longitude = 114.360336,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "交通学院实验中心",latitude = 30.608111, longitude = 114.360469,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "心理健康教育中心",latitude = 30.608052, longitude = 114.359969,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "造船工艺楼",latitude = 30.607846, longitude = 114.360514,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "大船池（楼群2栋）",latitude = 30.607145, longitude = 114.360359,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "交通学院船舶与海洋工程系",latitude = 30.606859, longitude = 114.359525,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "交通学院交通运输管理系",latitude = 30.606589, longitude = 114.35959,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区15舍",latitude = 30.606065, longitude = 114.359735,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区7舍（海虹6栋）",latitude = 30.606629, longitude = 114.360352,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "余区8舍（海虹7栋）",latitude = 30.606329, longitude = 114.360359,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "海虹1栋",latitude = 30.60834, longitude = 114.360909,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "海虹2栋",latitude = 30.608048, longitude = 114.36108,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "海虹3栋",latitude = 30.60778, longitude = 114.361285,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "海虹4栋",latitude = 30.607549, longitude = 114.361414,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
//                    LocationEntity(0, buildId = 1001,name = "海虹5栋",latitude = 30.60717, longitude = 114.361562,
//                        area = "余家头", aliases = listOf(" "), note = "建筑"),
                    LocationEntity(0, buildId=5001, name="武汉理工大学余家头校区", latitude=30.606383, longitude=114.356614, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5002, name="内河航运技术湖北省重点实验室", latitude=30.601072, longitude=114.357265, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5003, name="停车场", latitude=30.601335, longitude=114.357039, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5004, name="航海楼", latitude=30.601856, longitude=114.357942, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5005, name="余区天文馆", latitude=30.603027, longitude=114.358592, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5006, name="余区5号门", latitude=30.603689, longitude=114.357764, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5007, name="余区28", latitude=30.605052, longitude=114.352411, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5008, name="余区29", latitude=30.604816, longitude=114.352665, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5009, name="余区30", latitude=30.604645, longitude=114.352867, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5010, name="余区31", latitude=30.604508, longitude=114.353039, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5011, name="余区32", latitude=30.604327, longitude=114.353232, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5012, name="百花园", latitude=30.604407, longitude=114.353625, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5013, name="余区35", latitude=30.603976, longitude=114.352684, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5014, name="余区23", latitude=30.603646, longitude=114.353634, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5015, name="余区车队", latitude=30.603227, longitude=114.353677, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5016, name="理工大社区居委会", latitude=30.603578, longitude=114.353931, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5017, name="工程训练中心", latitude=30.60328, longitude=114.354612, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5018, name="余区3号门", latitude=30.602595, longitude=114.353683, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5019, name="余区西园三舍", latitude=30.602509, longitude=114.355259, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5020, name="西园综合服务部", latitude=30.602975, longitude=114.355548, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5021, name="余区西园二舍", latitude=30.603074, longitude=114.355855, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5022, name="余区西园4栋", latitude=30.603406, longitude=114.355873, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5023, name="余区校医院", latitude=30.603453, longitude=114.356268, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5024, name="中原平价超市", latitude=30.603742, longitude=114.355775, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5025, name="余区14舍B", latitude=30.603817, longitude=114.35614, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5026, name="余区14舍A", latitude=30.603913, longitude=114.356719, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5027, name="余区4号门", latitude=30.603854, longitude=114.356975, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5028, name="余区12舍", latitude=30.60422, longitude=114.356507, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5029, name="余区13舍", latitude=30.60416, longitude=114.356205, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5030, name="余区9舍", latitude=30.604574, longitude=114.356616, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5031, name="余区10舍", latitude=30.604507, longitude=114.356386, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5032, name="余区11舍", latitude=30.604429, longitude=114.356019, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5033, name="后勤集团余区公司", latitude=30.604726, longitude=114.356344, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5034, name="余家头校区后勤集团", latitude=30.604696, longitude=114.356057, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5035, name="水运一、二食堂", latitude=30.605025, longitude=114.356507, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5036, name="余区大学生活动中心", latitude=30.605469, longitude=114.356094, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5037, name="余区教职工活动中心", latitude=30.604004, longitude=114.355699, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5038, name="船员培训中心", latitude=30.605084, longitude=114.355585, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5039, name="余区老年活动中心", latitude=30.605377, longitude=114.355385, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5040, name="余区公寓楼14栋", latitude=30.603876, longitude=114.354903, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5041, name="余区公寓楼13栋", latitude=30.604114, longitude=114.354881, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5042, name="余区公寓楼12栋", latitude=30.604332, longitude=114.354867, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5043, name="余区公寓楼11栋", latitude=30.604547, longitude=114.354813, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5044, name="余区公寓楼10栋", latitude=30.604752, longitude=114.354729, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5045, name="余区公寓楼9栋", latitude=30.604978, longitude=114.354668, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5046, name="余区公寓楼8栋", latitude=30.605314, longitude=114.354532, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5047, name="余区公寓楼7栋", latitude=30.605525, longitude=114.354475, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5048, name="教工食堂（楼群2栋）", latitude=30.604938, longitude=114.353877, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5049, name="余区17", latitude=30.605306, longitude=114.353666, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5050, name="余区幼儿园", latitude=30.605373, longitude=114.353036, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5051, name="大浴室", latitude=30.605673, longitude=114.353904, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5052, name="余区16", latitude=30.605843, longitude=114.353781, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5053, name="学友情餐厅", latitude=30.605816, longitude=114.353457, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5054, name="余区公寓楼2栋", latitude=30.606633, longitude=114.354208, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5055, name="余区公寓楼1栋", latitude=30.606787, longitude=114.354475, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5056, name="大众水果副食", latitude=30.606338, longitude=114.353424, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5057, name="心曲餐厅", latitude=30.606557, longitude=114.353714, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5058, name="余区学生宿舍（北园）", latitude=30.606919, longitude=114.353474, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5059, name="余区公寓楼3栋", latitude=30.607444, longitude=114.354111, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5060, name="余区公寓楼", latitude=30.6073, longitude=114.353374, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5061, name="体育运动场", latitude=30.606666, longitude=114.355472, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5062, name="篮球场", latitude=30.606905, longitude=114.356174, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5063, name="航海体能训练中心", latitude=30.608224, longitude=114.354926, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5064, name="余区保卫办", latitude=30.608423, longitude=114.355767, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5065, name="西合楼", latitude=30.608887, longitude=114.355264, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5066, name="余区西配楼", latitude=30.609244, longitude=114.355413, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5067, name="余区游泳池管理用房", latitude=30.60879, longitude=114.354509, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5068, name="余区2号门", latitude=30.60896, longitude=114.353861, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5069, name="余区小车库", latitude=30.609884, longitude=114.355432, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5070, name="余区1号门", latitude=30.610765, longitude=114.356516, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5071, name="水运湖", latitude=30.610955, longitude=114.357076, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5072, name="余区05", latitude=30.609887, longitude=114.356161, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5073, name="余区创业中心（青桐学院）", latitude=30.609636, longitude=114.356178, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5074, name="余区教学大楼", latitude=30.609165, longitude=114.356907, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5075, name="余区综合楼", latitude=30.608422, longitude=114.356569, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5076, name="青年园", latitude=30.608578, longitude=114.35742, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5077, name="社科楼", latitude=30.607945, longitude=114.356668, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5078, name="交运楼", latitude=30.608086, longitude=114.3576, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5079, name="风雨棚", latitude=30.607524, longitude=114.356679, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5080, name="流体力学楼", latitude=30.607665, longitude=114.357635, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5081, name="中国银行", latitude=30.607153, longitude=114.356464, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5082, name="风雨棚附楼", latitude=30.607223, longitude=114.356942, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5083, name="余区2舍", latitude=30.606882, longitude=114.356866, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5084, name="余区1舍", latitude=30.606977, longitude=114.357723, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5085, name="余区4舍", latitude=30.606435, longitude=114.356965, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5086, name="余区3舍", latitude=30.606496, longitude=114.357705, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5087, name="余区6舍", latitude=30.605753, longitude=114.357058, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5088, name="余区5舍", latitude=30.605924, longitude=114.357874, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5089, name="停车场（才汇巷）", latitude=30.605387, longitude=114.3576, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5090, name="余区东配楼", latitude=30.609648, longitude=114.358145, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5091, name="交通学院楼", latitude=30.609794, longitude=114.358818, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5092, name="计算机实验中心", latitude=30.609331, longitude=114.358394, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5093, name="物流液压实验楼", latitude=30.609476, longitude=114.35887, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5094, name="港口大楼", latitude=30.608965, longitude=114.358018, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5095, name="余区16栋", latitude=30.608959, longitude=114.358449, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5096, name="立体仓库实验楼", latitude=30.608781, longitude=114.358204, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5097, name="物流装备实验楼", latitude=30.608594, longitude=114.357987, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5098, name="港机实验楼", latitude=30.608559, longitude=114.35859, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5099, name="物流学院楼", latitude=30.608185, longitude=114.358376, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5100, name="焊接楼", latitude=30.607739, longitude=114.358294, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5101, name="小船池", latitude=30.607819, longitude=114.358663, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5102, name="能动学院实验楼（楼群4栋）", latitude=30.607155, longitude=114.358556, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5103, name="交通学院船舶工程系", latitude=30.606742, longitude=114.35859, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5104, name="动力楼", latitude=30.606528, longitude=114.358728, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5105, name="水运三食堂", latitude=30.605641, longitude=114.358825, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5106, name="联盟小区3栋", latitude=30.609069, longitude=114.359277, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5107, name="联盟小区1栋", latitude=30.60914, longitude=114.359657, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5108, name="联盟小区4栋", latitude=30.608822, longitude=114.359336, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5109, name="联盟小区2栋", latitude=30.608879, longitude=114.359707, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5110, name="联盟小区5栋", latitude=30.608577, longitude=114.359163, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5111, name="联盟小区6栋", latitude=30.608605, longitude=114.359762, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5112, name="联盟小区7栋", latitude=30.608315, longitude=114.359243, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5113, name="联盟小区8栋", latitude=30.608375, longitude=114.359788, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5114, name="联盟小区9栋", latitude=30.608044, longitude=114.35927, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5115, name="船舶结构实验室", latitude=30.608402, longitude=114.360336, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5116, name="交通学院实验中心", latitude=30.608111, longitude=114.360469, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5117, name="心理健康教育中心", latitude=30.608052, longitude=114.359969, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5118, name="造船工艺楼", latitude=30.607846, longitude=114.360514, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5119, name="大船池（楼群2栋）", latitude=30.607145, longitude=114.360359, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5120, name="交通学院船舶与海洋工程系", latitude=30.606859, longitude=114.359525, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5121, name="交通学院交通运输管理系", latitude=30.606589, longitude=114.35959, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5122, name="余区15舍", latitude=30.606065, longitude=114.359735, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5123, name="余区7舍（海虹6栋）", latitude=30.606629, longitude=114.360352, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5124, name="余区8舍（海虹7栋）", latitude=30.606329, longitude=114.360359, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5125, name="海虹1栋", latitude=30.60834, longitude=114.360909, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5126, name="海虹2栋", latitude=30.608048, longitude=114.36108, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5127, name="海虹3栋", latitude=30.60778, longitude=114.361285, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5128, name="海虹4栋", latitude=30.607549, longitude=114.361414, area="余家头", aliases=listOf(" "), note="建筑"),
                    LocationEntity(0, buildId=5129, name="海虹5栋", latitude=30.60717, longitude=114.361562, area="余家头", aliases=listOf(" "), note="建筑"),


                    )
                insertLocations(locations)
            }
        }



//        locations.add(LocationEntity(0, buildId = 1001,name = "武汉理工大学鉴湖校区",latitude = 30.513791, longitude = 114.343923, area = "鉴湖", aliases = listOf(" ")))
//        locations.add(LocationEntity(0, buildId = 1001,name = "武汉理工大学鉴湖校区",latitude = 30.513791, longitude = 114.343923, area = "鉴湖", aliases = listOf(" ")))
//        locations.add(LocationEntity(0, buildId = 1001,name = "武汉理工大学鉴湖校区",latitude = 30.513791, longitude = 114.343923, area = "鉴湖", aliases = listOf(" ")))



    }
    fun seeMyPosition(){
        aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
    }
    fun switchMap(isSatellite:Boolean){
        if(isSatellite){
            aMap?.mapType = AMap.MAP_TYPE_SATELLITE
        }else{
            aMap?.mapType = AMap.MAP_TYPE_NORMAL
        }
    }
    fun toMyPosition(change:(Boolean)->Unit){
        if(isRealTimeLocation){
            mLocationClient?.stopLocation()
            isRealTimeLocation = false
            showMsg("关闭实时定位")
            change(isRealTimeLocation)

        }else{
            mLocationClient?.startLocation()
            isRealTimeLocation = true
            showMsg("开启实时定位")
            change(isRealTimeLocation)

        }

    }

}