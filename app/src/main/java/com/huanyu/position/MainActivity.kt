package com.huanyu.position

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.geocoder.GeocodeQuery
import com.huanyu.position.activity.SearchActivity
import com.huanyu.position.databinding.ActivityMainBinding
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions


class MainActivity : AppCompatActivity() {
    private lateinit var mainViewModel: MainViewModel
    private lateinit var _binding:ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 设置状态栏颜色
//        window.statusBarColor = Color.TRANSPARENT // 你可以替换成任何你想要的颜色
        _binding = ActivityMainBinding.inflate(layoutInflater)
        //隐私合规效验
        ServiceSettings.updatePrivacyShow(this, true, true)
        ServiceSettings.updatePrivacyAgree(this, true)
        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        setContentView(_binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // 动态设置导航视图的宽度
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val width = (displayMetrics.widthPixels * 0.70).toInt() // 设置宽度为屏幕宽度的80%
        val params: ViewGroup.LayoutParams = _binding.navigationView.layoutParams
        params.width = width
        _binding.navigationView.layoutParams = params
        _binding.mapCtrlSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            mainViewModel.switchMap(isChecked)
        }

        _binding.mapView.onCreate(savedInstanceState)
        _binding.fabShow.setOnClickListener {
            Log.d("buttonclick","fab")
            mainViewModel.showWutMarker()
        }
//        _binding.fabMyposition.setOnClickListener {
//            Log.d("buttonclick","toMyposition")
//            mainViewModel.toMyPosition(change = {
//                Log.d("fabMyposition",it.toString())
//                if(it){
//                    _binding.fabMyposition.setImageResource(R.drawable.stopposition)
//                }else{
//                    _binding.fabMyposition.setImageResource(R.drawable.position)
//                }
//
//            })
//        }
        _binding.fabMyposition.setOnClickListener{
            mainViewModel.seeMyPosition()
        }
        _binding.fabSearch.setOnClickListener {
            val intent = Intent(this,SearchActivity::class.java)
            startActivity(intent)
        }
        _binding.btnTest.setOnClickListener {
//            val intent = Intent(this,RouteActivity::class.java)
//            startActivity(intent)
            _binding.drawer.openDrawer(GravityCompat.END)
        }
        _binding.search.setOnKeyListener { v, keyCode, event ->
            if (keyCode === KeyEvent.KEYCODE_ENTER && event.action === KeyEvent.ACTION_UP) {
                //获取输入框的值
                val address: String = _binding.search.getText().toString().trim()
                if (address == null || address.isEmpty()) {
                    showMsg("请输入地址")
                } else {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    //隐藏软键盘
                    imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)

                    // name表示地址，第二个参数表示查询城市，中文或者中文全拼，citycode、adcode
                    val query = GeocodeQuery(address, "武汉")
                    mainViewModel.geocodeSearch.getFromLocationNameAsyn(query)
                }
               true
            }
            false
        }
//        mainViewModel.mapView = _binding.mapView
//        mainViewModel.deleteAllLocations()
        mainViewModel.prepareDate()
//        mainViewModel.insertLocation(LocationEntity(0,"你好",12.0,23.0, listOf("你好")))
        mainViewModel.initLocation()
        checkingAndroidVersion()
        mainViewModel.initMap(mapView = _binding.mapView, context = this)

        mainViewModel.getAddress().observe(this) {

            _binding.tvContent.text = it
        }
        mainViewModel.getWeather().observe(this){
            _binding.tvTop.text = it
        }
    }
    /**
     * 检查Android版本
     */
    private fun checkingAndroidVersion() {
        //Android6.0及以上先获取权限再定位
        requestPermission()

    }
    companion object{
        //请求权限码
        private const val REQUEST_PERMISSIONS = 9527
    }


    /**
     * 动态请求权限
     */
    @AfterPermissionGranted(REQUEST_PERMISSIONS)
    private fun requestPermission() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        if (EasyPermissions.hasPermissions(this, *permissions)) {
            //true 有权限 开始定位
            mainViewModel.mLocationClient?.let {
                showMsg("已获得权限，可以定位啦！")
                it.startLocation()
            }

        } else {
            //false 无权限
            EasyPermissions.requestPermissions(this, "需要权限", REQUEST_PERMISSIONS, *permissions)
        }
    }

    /**
     * 请求权限结果
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //设置权限请求结果
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    /**
     * Toast提示
     * @param msg 提示内容
     */
    private fun showMsg(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
//        销毁定位客户端
        mainViewModel.mLocationClient?.onDestroy()
//        销毁地图
        _binding.mapView.onDestroy()
        super.onDestroy()
    }
    override fun onResume() {
        super.onResume()
        //在activity执行onResume时执行mMapView.onResume ()，重新绘制加载地图
        _binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        //在activity执行onPause时执行mMapView.onPause ()，暂停地图的绘制
        _binding.mapView.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        //在activity执行onSaveInstanceState时执行mMapView.onSaveInstanceState (outState)，保存地图当前的状态
        _binding.mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

}