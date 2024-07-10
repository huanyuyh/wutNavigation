package com.huanyu.position.activity


import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.LocationSource
import com.amap.api.maps.LocationSource.OnLocationChangedListener
import com.amap.api.maps.MapView
import com.amap.api.maps.UiSettings
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.Poi
import com.amap.api.navi.AmapNaviPage
import com.amap.api.navi.AmapNaviParams
import com.amap.api.navi.AmapNaviType
import com.amap.api.navi.AmapPageType
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.route.BusRouteResult
import com.amap.api.services.route.DriveRouteResult
import com.amap.api.services.route.RideRouteResult
import com.amap.api.services.route.RouteSearch
import com.amap.api.services.route.WalkRouteResult
import com.huanyu.position.R
import com.huanyu.position.util.MapUtil
import com.huanyu.position.util.MapUtil.Companion.convertToLatLng
import com.huanyu.position.util.MapUtil.Companion.convertToLatLonPoint
import com.huanyu.position.util.WalkRouteOverlay


class RouteViewModel(application: Application): AndroidViewModel(application) {
    private val TAG = "RouteActivity" //地图
    @SuppressLint("StaticFieldLeak")
    private val context = getApplication<Application>().applicationContext

    //地图控制器
    private var aMap: AMap? = null
    private var latLng:LatLng = LatLng(30.507919,114.332415)
    //声明AMapLocationClient类对象
    var mLocationClient: AMapLocationClient? = null

    //声明AMapLocationClientOption对象
    var mLocationOption: AMapLocationClientOption? = null

    //位置更改监听
    private var mListener: OnLocationChangedListener? = null

    //定义一个UiSettings对象
    private var mUiSettings: UiSettings? = null

    //定位样式
    private val myLocationStyle = MyLocationStyle()

    //起点
    private var mStartPoint: LatLonPoint? = null

    //终点
    private var mEndPoint: LatLonPoint? = null

    //路线搜索对象
    private var routeSearch: RouteSearch? = null

    /**
     * 开始路线搜索
     */
    private fun startRouteSearch() {
        //在地图上添加起点Marker
        aMap!!.addMarker(
            MarkerOptions()
                .position(mStartPoint?.let { convertToLatLng(it) })
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.start))
        )
        //在地图上添加终点Marker
        aMap!!.addMarker(
            MarkerOptions()
                .position(mEndPoint?.let { convertToLatLng(it) })
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.end))
        )

        //搜索路线 构建路径的起终点
        val fromAndTo = RouteSearch.FromAndTo(
            mStartPoint, mEndPoint
        )
        //构建步行路线搜索对象
        val query = RouteSearch.WalkRouteQuery(fromAndTo)
        // 异步路径规划步行模式查询
        routeSearch?.calculateWalkRouteAsyn(query)
    }
    /**
     * 初始化路线
     */
    fun initRoute() {
        routeSearch = RouteSearch(context)
        routeSearch?.setRouteSearchListener(object : RouteSearch.OnRouteSearchListener {
            override fun onDriveRouteSearched(driveRouteResult: DriveRouteResult?, code: Int) {

            }

            override fun onBusRouteSearched(p0: BusRouteResult?, code: Int) {
                TODO("Not yet implemented")
            }

            override fun onWalkRouteSearched(walkRouteResult: WalkRouteResult, code: Int) {
                aMap!!.clear() // 清理地图上的所有覆盖物
                if (code === AMapException.CODE_AMAP_SUCCESS) {
                    if (walkRouteResult != null && walkRouteResult.paths != null) {
                        if (walkRouteResult.paths.size > 0) {
                            val walkPath = walkRouteResult.paths[0] ?: return
                            //绘制路线
                            val walkRouteOverlay = WalkRouteOverlay(
                                context, aMap, walkPath,
                                walkRouteResult.startPos,
                                walkRouteResult.targetPos
                            )
                            walkRouteOverlay.removeFromMap()
                            walkRouteOverlay.addToMap()
                            walkRouteOverlay.zoomToSpan()

                            Log.d(TAG, walkPath.steps.size.toString())
                            walkPath.steps.forEach {
                                Log.d(TAG, it.instruction)
                                Log.d(TAG, it.polyline.size.toString())

                            }
                            val dis = walkPath.distance.toInt()
                            val dur = walkPath.duration.toInt()
                            val des =
                                (MapUtil.getFriendlyTime(dur) + "(" + MapUtil.getFriendlyLength(dis)).toString() + ")"
                            Log.d(TAG, des)

                        } else if (walkRouteResult.paths == null) {
                            showMsg("对不起，没有搜索到相关数据！")
                        }
                    } else {
                        showMsg("对不起，没有搜索到相关数据！")
                    }
                } else {
                    showMsg("错误码；$code")
                }
            }

            override fun onRideRouteSearched(p0: RideRouteResult?, code: Int) {
                TODO("Not yet implemented")
            }


        })
    }
    /**
     * 初始化定位
     */
    fun initLocation() {
        //初始化定位
        mLocationClient = AMapLocationClient(context)
        mLocationClient!!.setLocationListener(AMapLocationListener {
            if (it != null) {
                if (it.getErrorCode() == 0) {
                    //地址
                    val address: String = it.getAddress()
                    //获取纬度
                    val latitude: Double = it.getLatitude()
                    //获取经度
                    val longitude: Double = it.getLongitude()
                    Log.d(TAG, it.getCity())
                    Log.d(TAG, address)

                    //设置起点
                    mStartPoint = convertToLatLonPoint(LatLng(latitude, longitude))
                    latLng = LatLng(it.latitude, it.longitude)
                    //停止定位后，本地定位服务并不会被销毁
//                    mLocationClient?.stopLocation()

                    //显示地图定位结果
                    mListener?.onLocationChanged(it)
                } else {
                    //定位失败时，可通过ErrCode（错误码）信息来确定失败的原因，errInfo是错误信息，详见错误码表。
                    Log.e(
                        "AmapError", ("location Error, ErrCode:"
                                + it.getErrorCode()).toString() + ", errInfo:"
                                + it.getErrorInfo()
                    )
                }
            }
        })
        mLocationOption = AMapLocationClientOption()
        mLocationOption!!.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy)
        mLocationOption!!.setOnceLocationLatest(true)
        mLocationOption!!.setNeedAddress(true)
        mLocationOption!!.setHttpTimeOut(20000)
        mLocationOption!!.setLocationCacheEnable(false)
        mLocationClient!!.setLocationOption(mLocationOption)
    }

    /**
     * 初始化地图
     *
     * @param savedInstanceState
     */
    fun initMap(mapView: MapView) {
        //初始化地图控制器对象
        aMap = mapView.getMap()
        //设置最小缩放等级为16 ，缩放级别范围为[3, 20]
        aMap?.setMinZoomLevel(16f)
        //开启室内地图
        aMap?.showIndoorMap(true)
        //实例化UiSettings类对象
        mUiSettings = aMap?.getUiSettings()
        //隐藏缩放按钮 默认显示
        mUiSettings?.setZoomControlsEnabled(false)
        //显示比例尺 默认不显示
        mUiSettings?.setScaleControlsEnabled(true)
        // 自定义定位蓝点图标
//        myLocationStyle.myLocationIcon(BitmapDescriptorFactory.fromResource(R.drawable.gps_point))
        //设置定位蓝点的Style
        aMap?.setMyLocationStyle(myLocationStyle)
        // 设置定位监听
        aMap?.setLocationSource(object :LocationSource{
            override fun activate(onLocationChangedListener: OnLocationChangedListener) {
                mListener = onLocationChangedListener
                mLocationClient?.startLocation() //启动定位

            }

            override fun deactivate() {
                mListener = null
                if (mLocationClient != null) {
                    mLocationClient!!.stopLocation()
                    mLocationClient!!.onDestroy()
                }
                mLocationClient = null
            }

        })
        // 设置为true表示显示定位层并可触发定位，false表示隐藏定位层并不可触发定位，默认是false
        aMap?.setMyLocationEnabled(true)
        aMap?.setOnMapClickListener {
            //终点
            mEndPoint = convertToLatLonPoint(it)
//            startRouteSearch()

            //起点
            val start = Poi("我的位置", latLng, null)

            //途经点
//            val poiList: MutableList<Poi?> = ArrayList<Poi?>()
//            poiList.add(Poi("故宫", LatLng(39.918058, 116.397026), "B000A8UIN8"))

            //终点
            val end = Poi(null, it, null)

            // 组件参数配置
            val params = AmapNaviParams(start, null, end, AmapNaviType.WALK, AmapPageType.ROUTE)
            params.setMultipleRouteNaviMode(true)
            params.setShowVoiceSetings(true)
            params.setTrafficEnabled(true)
            val aMapNavi = AmapNaviPage.getInstance()
            // 启动组件
           aMapNavi.showRouteActivity(context, params, null)
        }

        // 将地图视图移动到标记点
        aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
    }
    private fun showMsg(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

}