package com.huanyu.position.activity

import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.amap.api.services.core.ServiceSettings
import com.huanyu.position.R
import com.huanyu.position.adapter.MyRecyclerViewAdapter
import com.huanyu.position.databinding.ActivitySearchBinding


class SearchActivity: AppCompatActivity() {
    lateinit var _binding: ActivitySearchBinding
    private lateinit var searchViewModel: SearchViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _binding = ActivitySearchBinding.inflate(layoutInflater)
        //隐私合规效验
        ServiceSettings.updatePrivacyShow(this, true, true)
        ServiceSettings.updatePrivacyAgree(this, true)
        searchViewModel = ViewModelProvider(this)[SearchViewModel::class.java]
        setContentView(_binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        _binding.searchRecycleView.layoutManager = LinearLayoutManager(this)
        var adapter = MyRecyclerViewAdapter(this,null)
        _binding.searchRecycleView.adapter = adapter
        //执行默认搜索
        searchViewModel.searchLocations()
        //更新搜索数据
        searchViewModel.allLocations.observe(this){
            var searchBuilds = it.filter { it.area == searchViewModel.area.value }
            adapter.updateData(searchBuilds)
        }
        _binding.editTextSearch.addTextChangedListener(object :TextWatcher{
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchViewModel.searchString = s.toString()
            }

            override fun afterTextChanged(s: Editable?) {

            }

        })
        _binding.buttonSearch.setOnClickListener {
            searchViewModel.searchLocations()
        }
        _binding.areaSpin.setOnClickListener {
            showDialog();
        }
        searchViewModel.area.observe(this){
            _binding.areaSpin.text = it
            searchViewModel.searchLocations()
        }
    }
    private fun showDialog() {
        // 定义选项数组
        val items = arrayOf("南湖校区", "鉴湖校区", "马房山校区西院", "马房山校区东院", "余家头校区")

        // 创建AlertDialog
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle("选择校区")
            .setItems(items, DialogInterface.OnClickListener { dialog, which -> // 用户点击某一项后的处理
                when(which){
                    0->searchViewModel.setArea("南湖")
                    1->searchViewModel.setArea("鉴湖")
                    2->searchViewModel.setArea("西院")
                    3->searchViewModel.setArea("东院")
                    4->searchViewModel.setArea("余家头")
                }
                Toast.makeText(this, "Selected: " + items[which], Toast.LENGTH_SHORT)
                    .show()
            })

        // 显示对话框
        builder.create().show()
    }
}