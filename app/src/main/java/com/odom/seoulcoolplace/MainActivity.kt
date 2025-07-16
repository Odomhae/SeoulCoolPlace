package com.odom.seoulcoolplace

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Layout
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.graphics.Insets
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.maps.android.clustering.ClusterManager
import com.odom.seoulcoolplace.databinding.ActivityMainBinding
//import kotlinx.android.synthetic.main.activity_main.*
//import kotlinx.android.synthetic.main.search_bar.view.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import kotlin.system.exitProcess


class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    var PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.INTERNET
    )

    val REQUEST_PERMISSION_CODE = 1
    val DEFAULT_ZOOM_LEVEL = 16f
    val CITY_HALL = LatLng(37.566648, 126.978449)
    var googleMap: GoogleMap? = null

    private var lastBackPressed: Long = 0

    private var mInterstitialAd: InterstitialAd? = null

    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // 인터넷 연결 안되어있으면
        // 알림 후 종료
        if(!checkInternetConnection()){
            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle(R.string.check_internet)
                .setPositiveButton(R.string.check) { _, _ ->
                    finish()
                    exitProcess(0)
                }

            val alertDialog = builder.create()
            alertDialog.show()
        }


        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

       // setContentView(R.layout.activity_main)
        binding.mapView.onCreate(savedInstanceState)
        // MapsInitializer.initialize(applicationContext)

        //권한 요청
        ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSION_CODE)

        // 현재 위치 버튼 리스너
        binding.myLocationButton.setOnClickListener { onMyLocationButtonClick() }

        // 광고 초기화
        initializeAds()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        val contentView: View = this.findViewById(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(
            contentView
        ) { v, insets ->
            val innerPadding: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(0, innerPadding.top, 0, innerPadding.bottom)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                val isLightStatusBars = AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES
                if (isLightStatusBars) {
                    v.setBackgroundColor(resources.getColor(R.color.white))
                } else {
                    v.setBackgroundColor(resources.getColor(R.color.black))
                }

            }


            insets
        }


        val isLightStatusBars = AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES
        val compat = WindowInsetsControllerCompat(this.window, this.window.decorView)
        compat.isAppearanceLightStatusBars = isLightStatusBars
        compat.isAppearanceLightNavigationBars = isLightStatusBars
    }

    private fun initializeAds() {
        // AdMob 초기화
        MobileAds.initialize(this) {
            Log.d("test", "Ad loaded")
        }

        // 전면 광고 로드
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            getString(R.string.REAL_fullscreen_ad_unit_id),
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    mInterstitialAd = interstitialAd
                    mInterstitialAd?.show(this@MainActivity)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    mInterstitialAd = null
                }
            }
        )
    }

    // 인터넷 연결 확인
    fun checkInternetConnection() : Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = cm.activeNetworkInfo

        if (activeNetwork != null)
            return true

        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 맵 초기화
        initMap()
    }

    // 권한 있나 체크
    fun hasPermissions() : Boolean{
        for(permisison in PERMISSIONS)
            if(ActivityCompat.checkSelfPermission(this, permisison) != PackageManager.PERMISSION_GRANTED)
                return false

        return true
    }

    // clusterManager 변수
    var clusterManager : ClusterManager<MyItem>? = null
    // clusterRenderer 변수
    var clusterRenderer : ClusterRenderer? = null

    @SuppressLint("MissingPermission")
    fun initMap(){
        // 맵뷰에서 구글 맵을 불러옴
        binding.mapView.getMapAsync {

            // cluster 객체 초기화
            clusterManager = ClusterManager(this, it)
            clusterRenderer = ClusterRenderer(this, it, clusterManager)

            //
            it.setOnCameraIdleListener(clusterManager)
            it.setOnMarkerClickListener(clusterManager)

            googleMap = it
            it.uiSettings.isMyLocationButtonEnabled = false

            when{
                hasPermissions() ->{
                    it.isMyLocationEnabled = true
                    it.moveCamera(CameraUpdateFactory.newLatLngZoom(getMyLocation(), DEFAULT_ZOOM_LEVEL))
                }
                else ->{
                    it.moveCamera(CameraUpdateFactory.newLatLngZoom(CITY_HALL, DEFAULT_ZOOM_LEVEL))
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // MissingPermission 문제의 Lint 검사 중지
    fun getMyLocation() : LatLng{
        val locationProvider : String = LocationManager.GPS_PROVIDER
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var lastKnownLocation : Location? = locationManager.getLastKnownLocation(locationProvider)

        // 내 폰에선 이게 되고
        if(lastKnownLocation == null){
            fusedLocationProviderClient.lastLocation
                .addOnSuccessListener { location ->
                    if(location == null) {
                        Log.d("TAG", "location get fail")
                    } else {
                        lastKnownLocation = location

                        val myLoc = LatLng(location.latitude, location.longitude)
                        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(myLoc, DEFAULT_ZOOM_LEVEL))
                    }
                }

        }

        // 안드로이드 10 버전에선 이게 되고
        else{
            val myLoc = LatLng(lastKnownLocation!!.latitude, lastKnownLocation!!.longitude)
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(myLoc, DEFAULT_ZOOM_LEVEL))
        }

        // 경도, 위도 위치 반환
        if(lastKnownLocation == null){
            Log.d("TAG", "위치 확인불가")
            return LatLng(CITY_HALL.latitude, CITY_HALL.longitude)
        }

        return LatLng(lastKnownLocation!!.latitude, lastKnownLocation!!.longitude)
    }

    fun onMyLocationButtonClick(){
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        when{
            hasPermissions() ->{
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(getMyLocation(), DEFAULT_ZOOM_LEVEL))
                Log.d("TAG", "권한있음"+" 위치 :"+getMyLocation().toString())

                // 권한은 있는데 GPS 꺼져있으면 켜는 화면으로 이동
                if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                    val builder = AlertDialog.Builder(this@MainActivity)
                    builder.setTitle(R.string.check_gps)
                        .setPositiveButton(R.string.check) { _, _ ->
                            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            intent.addCategory(Intent.CATEGORY_DEFAULT)
                            startActivity(intent)
                        }
                        .setNegativeButton(R.string.cancel) {_, _ ->
                        }

                    val alertDialog = builder.create()
                    alertDialog.show()
                }
            }

            else -> {
                val builder = AlertDialog.Builder(this@MainActivity)
                builder.setTitle(R.string.check_permission)
                    .setPositiveButton(R.string.check) { _, _ ->
                        //권한 요청
                        ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSION_CODE)
                    }
                    .setNegativeButton(R.string.cancel) {_, _ ->
                        Toast.makeText(applicationContext, R.string.alert_location_permission, Toast.LENGTH_SHORT).show()
                        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(CITY_HALL, DEFAULT_ZOOM_LEVEL))
                    }

                val alertDialog = builder.create()
                alertDialog.show()
            }
        }
    }



    // 맵뷰의 라이프사이클 함수 호출
    override fun onResume() {
        super.onResume()
        binding.mapView!!.onResume()
        //  앱 AsyncTask 중지되었으면
        if(CoolPlaceTask().status == AsyncTask.Status.FINISHED)
            CoolPlaceTask().execute()
    }
    override fun onPause() {
        binding.mapView!!.onPause()
        super.onPause()
        // 앱 AsyncTask도 pause
        if(CoolPlaceTask().status == AsyncTask.Status.RUNNING)
            CoolPlaceTask().cancel(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView!!.onDestroy()
        // 앱 종료시 AsyncTask도 종료
        if(CoolPlaceTask().status == AsyncTask.Status.RUNNING)
            CoolPlaceTask().cancel(true)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView!!.onLowMemory()
    }

    // 서울 열린 데이터 광장 발급 키
    val API_KEY = "6e7364446f6a6968313032694b695252"

    var task: CoolPlaceTask ?= null
    // 쉼터 정보 저장할 배열
    var placeArray = JSONArray()
    // JSONobject를 키로 MyItem 객체를 저장할 맵
    val itemMap = mutableMapOf<JSONObject, MyItem>()

    // 이미지
    val bitmap by lazy {
        val drawable = resources.getDrawable(R.drawable.img_flake) as BitmapDrawable
        Bitmap.createScaledBitmap(drawable.bitmap, 64, 64, false)
    }

    // JsonArrray 병합
    fun JSONArray.merge(anotherArray:JSONArray){
        for(i in 0 until anotherArray.length())
            this.put(anotherArray.get(i))
    }

    // 쉼터 정보를 읽어와서 JSONobject로 변환
    fun readData(startIndex:Int, lastIndex:Int) : JSONObject {
        val url =
            URL(
                "http://openapi.seoul.go.kr:8088" + "/" +
                        "${API_KEY}/json/TbGtnHwcwP/${startIndex}/${lastIndex}"
            )
        val connection = url.openConnection()

        val data = connection.getInputStream().readBytes().toString(charset("UTF-8"))
        return JSONObject(data)
    }

    // 쉼터 데이터를 읽어오는 AsyncTask
    @SuppressLint("StaticFieldLeak")
    inner class CoolPlaceTask : AsyncTask<Void, JSONArray, String>() {

        val asyncDialog : ProgressDialog = ProgressDialog(this@MainActivity)

        // 기존 데이터 초기화
        override fun onPreExecute() {
            // 구글맵 마커 초기화
            googleMap?.clear()
            // 쉼터 정보 초기화
            placeArray = JSONArray()
            // itemMap 변수 초기화
            itemMap.clear()
            asyncDialog.setProgressStyle(ProgressDialog.BUTTON_POSITIVE)
            asyncDialog.setMessage("Loading...")
            asyncDialog.show()
        }

        override fun doInBackground(vararg params: Void?): String {

            // 서울시 데이터는 최대 1000개씩 가져올 수 있으므로
            // 1000개씩 끊는다.
            val step = 1000
            var startIndex = 1
            var lastIndex = step
            var totalCnt = 0

            do {
                // 백그라운드 작업이 취소되었을 때는 루프 종료
                if (isCancelled)
                    break

                if (totalCnt != 0) {
                    startIndex += step // 1000
                    lastIndex += step // 1000
                }

                val jsonObject = readData(startIndex, lastIndex)

                totalCnt = jsonObject.getJSONObject("TbGtnHwcwP")
                    .getInt("list_total_count")
                //
                Log.d("===ttt " , totalCnt.toString())
                val rows =
                    jsonObject.getJSONObject("TbGtnHwcwP").getJSONArray("row")
                // 기존에 읽었던 데이터와 병합
                placeArray.merge(rows)
                // UI 업데이트를 위해 progress 발행
                publishProgress(rows)

            } while (lastIndex < totalCnt)

            return "complete"
        }

        // 데이터를 읽어올때마다 실행
        override fun onProgressUpdate(vararg values: JSONArray?) {
            // 0번째의 데이터 사용
            val array = values[0]
            array?.let {
                for (i in 0 until array.length()) {
                    // 마커 추가
                    addMarkers(array.getJSONObject(i))
                }
            }

            // clusterManager의 클러스터링 실행
            clusterManager?.cluster()
        }

        // 백그라운드 작업이 끝난 후 실행
        override fun onPostExecute(result: String?) {
            // 자동완성 텍스트뷰에서 사용할 텍스트 리스트
            val textList = mutableListOf<String>()

            // 모든 쉼터 이름을 리스트에 추가
            for(i in 0 until placeArray.length()){
                val place = placeArray.getJSONObject(i)
                textList.add(place.getString("R_AREA_NM"))
            }

            // 자동완성 텍스트뷰의 어댑터 추가
            val adapter = ArrayAdapter<String>(
                this@MainActivity,
                android.R.layout.simple_dropdown_item_1line, textList
            )

            // ProgressDialog 종료
            asyncDialog.dismiss()

            // 자동완성이 시작되는 글자수
            binding.searchBar.autoCompleteTextView.threshold = 1
            // 자동완성 텍스트뷰의 어댑터 설정
            binding.searchBar.autoCompleteTextView.setAdapter(adapter)
        }
    }

    // JSONArray에서 원소의 속성으로 검색
    fun JSONArray.findByChildProperty(propertyName : String, value : String) : JSONObject?{
        for(i in 0 until length()){
            val obj = getJSONObject(i)
            if(value == obj.getString(propertyName))
                return obj
        }
        return null
    }

    // 앱이 활성화될때마다 데이터를 읽어옴
    override fun onStart() {
        super.onStart()
        task?.cancel(true)
        task = CoolPlaceTask()

        // 인터넷 연결이 있을시에만 AsyncTask 실행★
        if(checkInternetConnection()){
            Log.d("TAG", "task EXECUTE")
            task?.execute()
        }


        // searchbar 검색 리스너 설정
        binding.searchBar.imageView.setOnClickListener {
            val word = binding.searchBar.autoCompleteTextView.text.toString()
            // 값이 없으면 그대로 리턴
            if(TextUtils.isEmpty(word))
                return@setOnClickListener

            // 검색 키워드에 해당하는 jsonobject 검색
            placeArray.findByChildProperty("R_AREA_NM", word)?.let{
                val myItem = itemMap[it]

                // clusterRenderer에서 myItem을 기반으로 마커 검색
                val marker = clusterRenderer?.getMarker(myItem)
                marker?.showInfoWindow()

                // 마커 위치로 카메라 이동
                googleMap?.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(it.getDouble("LAT"), it.getDouble("LON")), DEFAULT_ZOOM_LEVEL
                    )
                )
                clusterManager?.cluster()
            }

            // 검색 텍스트 초기화
            binding.searchBar.autoCompleteTextView.setText("")
        }
    }

    override fun onBackPressed() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressed < 2000) {
            super.onBackPressed() // 앱 종료
        } else {
            lastBackPressed = currentTime
            reviewApp(this) // 인앱 리뷰
            Toast.makeText(this, "뒤로 가기를 한번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 앱이 비활성화될때마다 백그라운드 작업취소
    override fun onStop() {
        super.onStop()
        task?.cancel(true)
        task = null
    }

    //  앱 리뷰
    fun reviewApp(context: Context) {
        val manager = ReviewManagerFactory.create(context)
        val request: Task<ReviewInfo> = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo: ReviewInfo = task.result
                manager.launchReviewFlow(context as Activity, reviewInfo)
                    .addOnCompleteListener { task1: Task<Void?> ->
                        if (task1.isSuccessful) {
                            Log.d("TAG", "Review Success")
                        }
                    }
            } else {
                Log.d("TAG", "Review Error")
            }
        }
    }

    // 마커 추가
    fun addMarkers(coolPlace : JSONObject){
        val item = MyItem(
            LatLng(coolPlace.getDouble("LAT"), coolPlace.getDouble("LON")),
            coolPlace.getString("R_AREA_NM"),
            coolPlace.getString("R_DETL_ADD"),
            BitmapDescriptorFactory.fromBitmap(bitmap)
        )

        // clusterManager를 이용해 마커 추가
        clusterManager?.addItem(
            MyItem(
                LatLng(coolPlace.getDouble("LAT"), coolPlace.getDouble("LON")),
                coolPlace.getString("R_AREA_NM"),
                coolPlace.getString("R_DETL_ADD"),
                BitmapDescriptorFactory.fromBitmap(bitmap)
            )
        )

        //
        itemMap.put(coolPlace, item)
    }
}