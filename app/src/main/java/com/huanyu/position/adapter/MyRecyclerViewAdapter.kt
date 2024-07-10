package com.huanyu.position.adapter

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Poi
import com.amap.api.navi.AmapNaviPage
import com.amap.api.navi.AmapNaviParams
import com.amap.api.navi.AmapNaviType
import com.amap.api.navi.AmapPageType
import com.huanyu.position.R
import com.huanyu.position.entity.LocationEntity


class MyRecyclerViewAdapter(val context:Context,var itemList: List<LocationEntity>?):RecyclerView.Adapter<MyRecyclerViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyRecyclerViewAdapter.ViewHolder {
        val view: View = LayoutInflater.from(parent.context).inflate(R.layout.item_pos, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return itemList?.size?:0
    }

    override fun onBindViewHolder(holder: MyRecyclerViewAdapter.ViewHolder, position: Int) {
        itemList?.let {
            var locationList = it
            val item: LocationEntity = locationList[position]
            holder.textViewName.setText(item.name)
            holder.textViewDescription.setText(item.aliases.toString())
            holder.cardView.setOnClickListener {
                showCustomDialog(locationList[position])
//                val latLng = LatLng(locationList[position].latitude,locationList[position].longitude)
//                val end = Poi(null, latLng, null)
//                // 组件参数配置
//                val params = AmapNaviParams(null, null, end, AmapNaviType.WALK, AmapPageType.ROUTE)
//                params.setMultipleRouteNaviMode(true)
//                params.setShowVoiceSetings(true)
//                params.setTrafficEnabled(true)
//                val aMapNavi = AmapNaviPage.getInstance()
//                // 启动组件
//                aMapNavi.showRouteActivity(context, params, null)

            }
        }

    }
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var textViewName: TextView = itemView.findViewById<TextView>(R.id.textViewName)
        var textViewDescription: TextView =
            itemView.findViewById<TextView>(R.id.textViewDescription)
        var cardView:CardView = itemView.findViewById(R.id.card_pos)
    }

    private fun showDialog() {
        // 创建AlertDialog
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
        builder.setTitle("Information")
            .setMessage("This is some important information. Please choose an option.")
            .setPositiveButton(
                "OK",
                DialogInterface.OnClickListener { dialog, which -> // 用户点击OK按钮后的处理
                    Toast.makeText(context, "You clicked OK", Toast.LENGTH_SHORT).show()
                })
            .setNegativeButton(
                "Cancel",
                DialogInterface.OnClickListener { dialog, which -> // 用户点击Cancel按钮后的处理
                    Toast.makeText(context, "You clicked Cancel", Toast.LENGTH_SHORT)
                        .show()
                })

        // 显示对话框
        builder.create().show()
    }

    private fun showCustomDialog(locationEntity: LocationEntity) {
        // 创建AlertDialog
        val builder = AlertDialog.Builder(context)

        // 获取自定义对话框布局
        val inflater: LayoutInflater =LayoutInflater.from(context)
        val dialogView: View = inflater.inflate(R.layout.pop_position, null)
        builder.setView(dialogView)

        // 获取TextView和Button
        val textViewTitle = dialogView.findViewById<TextView>(R.id.text_view_title)
        val textViewDesc = dialogView.findViewById<TextView>(R.id.text_view_desc)
        val buttonGo = dialogView.findViewById<Button>(R.id.button_go)
        val buttonClose = dialogView.findViewById<Button>(R.id.button_close)

        // 设置TextView的文本
        textViewTitle.text = locationEntity.name
        textViewDesc.text = locationEntity.aliases.toString()

        // 显示对话框
        val dialog = builder.create()
        // 设置Button的点击事件
        buttonGo.setOnClickListener {
            val latLng = LatLng(locationEntity.latitude,locationEntity.longitude)
            val end = Poi(null, latLng, null)
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
    fun updateData(items: List<LocationEntity>?) {
        this.itemList = items!!
        notifyDataSetChanged()
    }
}