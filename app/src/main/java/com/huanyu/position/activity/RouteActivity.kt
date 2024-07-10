package com.huanyu.position.activity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.amap.api.services.core.ServiceSettings
import com.huanyu.position.R
import com.huanyu.position.databinding.ActivityRouteBinding

class RouteActivity : AppCompatActivity(){
    lateinit var _binding:ActivityRouteBinding
    private lateinit var routeViewModel: RouteViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _binding = ActivityRouteBinding.inflate(layoutInflater)
        //隐私合规效验
        ServiceSettings.updatePrivacyShow(this, true, true)
        ServiceSettings.updatePrivacyAgree(this, true)
        routeViewModel = ViewModelProvider(this)[RouteViewModel::class.java]
        setContentView(_binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        _binding.mapView.onCreate(savedInstanceState)
        routeViewModel.initLocation()
        routeViewModel.initMap(_binding.mapView)
        routeViewModel.initRoute()
    }
    override fun onResume() {
        super.onResume()
        _binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        _binding.mapView.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding.mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        //销毁定位客户端，同时销毁本地定位服务。
        routeViewModel.mLocationClient?.onDestroy()

            _binding.mapView.onDestroy()
    }
}