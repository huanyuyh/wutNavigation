package com.huanyu.position.activity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.huanyu.position.R
import com.huanyu.position.databinding.ActivityRouteBinding
import com.huanyu.position.databinding.ActivitySettingsBinding

class SettingsActivity:AppCompatActivity() {
    lateinit var _binding: ActivitySettingsBinding
    lateinit var settingsViewModel: SettingsViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivitySettingsBinding.inflate(layoutInflater)
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        enableEdgeToEdge()
        setContentView(_binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}