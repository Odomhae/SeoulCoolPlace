package com.odom.seoulcoolplace

import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem


class MyItem(val _position : LatLng, val _title:String, val _snippet:String, val _icon : BitmapDescriptor)
    : ClusterItem {

    override fun getSnippet(): String {
        return _snippet
    }

    override fun getTitle(): String {
        return _title
    }

    override fun getPosition(): LatLng {
        return _position
    }
    fun getIcon():BitmapDescriptor{
        return _icon
    }

    // 검색에서 아이템을 찾기 위한 동등성 함수
    // GPS상 위도, 경도 제목 설명이 모두 같으면 같은 객체로 취급
    override fun equals(other: Any?): Boolean {
        if(other is MyItem){
            return(other.position.latitude == position.latitude
                    && other.position.longitude == position.longitude
                    && other.snippet == _snippet
                    && other.title == _title)
        }

        return false
    }

    // 같은 객체는 같은 해시코드를 반환해야
    override fun hashCode(): Int {
        var hash = _position.latitude.hashCode()*31
        hash = hash*31 + _position.longitude.hashCode()
        hash = hash*31 + title.hashCode()
        hash = hash*31 + snippet.hashCode()

        return hash
    }
}