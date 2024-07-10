package com.huanyu.position.util

import android.icu.text.DecimalFormat
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.LatLonPoint


/**
 * 地图帮助类
 * @author llw
 */
class MapUtil {
    companion object{
        /**
         * 把LatLng对象转化为LatLonPoint对象
         */
        fun convertToLatLonPoint(latLng: LatLng): LatLonPoint {
            return LatLonPoint(latLng.latitude, latLng.longitude)
        }

        /**
         * 把LatLonPoint对象转化为LatLon对象
         */
        fun convertToLatLng(latLonPoint: LatLonPoint): LatLng {
            return LatLng(latLonPoint.getLatitude(), latLonPoint.getLongitude())
        }

        fun getFriendlyTime(second: Int): String {
            if (second > 3600) {
                val hour = second / 3600
                val miniate = (second % 3600) / 60
                return hour.toString() + "小时" + miniate + "分钟"
            }
            if (second >= 60) {
                val miniate = second / 60
                return miniate.toString() + "分钟"
            }
            return second.toString() + "秒"
        }

        fun getFriendlyLength(lenMeter: Int): String {
            if (lenMeter > 10000) // 10 km
            {
                val dis = lenMeter / 1000
                return dis.toString() + ChString.Kilometer
            }

            if (lenMeter > 1000) {
                val dis = lenMeter.toFloat() / 1000
                val fnum: DecimalFormat = DecimalFormat("##0.0")
                val dstr: String = fnum.format(dis)
                return dstr + ChString.Kilometer
            }

            if (lenMeter > 100) {
                val dis = lenMeter / 50 * 50
                return dis.toString() + ChString.Meter
            }

            var dis = lenMeter / 10 * 10
            if (dis == 0) {
                dis = 10
            }

            return dis.toString() + ChString.Meter
        }

    }
}