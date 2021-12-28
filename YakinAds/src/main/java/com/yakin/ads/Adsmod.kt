package com.yakin.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.applovin.mediation.AppLovinExtras
import com.applovin.mediation.ApplovinAdapter
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.ads.mediation.facebook.FacebookAdapter
import com.google.ads.mediation.facebook.FacebookExtras
import com.google.android.gms.ads.*
import com.google.android.gms.ads.initialization.InitializationStatus
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.jirbo.adcolony.AdColonyAdapter
import com.jirbo.adcolony.AdColonyBundleBuilder
import com.yakin.ads.callback.AdsCallback
import com.yakin.ads.callback.AdsmodHelper
import com.yakin.ads.callback.RewardCallback
import com.yakin.ads.dialog.PrepareLoadingAdsDialog
import com.yakin.ads.utils.FirebaseAnalyticsUtil
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*

class Adsmod {

    companion object {
        val SPLASH_ADS = 0
        val RESUME_ADS = 1
        val BANNER_ADS = 2
        val INTERS_ADS = 3
        val REWARD_ADS = 4
        val NATIVE_ADS = 5
        private var instance: Adsmod? = null

        fun getInstance(): Adsmod? {
            if (instance == null) {
                instance = Adsmod()
                instance?.isShowLoadingSplash = false
            }
            return instance
        }
    }

    private val TAG = "Adsmod"


    private var currentClicked = 0
    private var nativeId: String? = null
    private var numShowAds = 3

    private var maxClickAds = 100
    private var handlerTimeout: Handler? = null
    private var rdTimeout: Runnable? = null
    private var dialog: PrepareLoadingAdsDialog? = null
    private var isTimeout // xử lý timeout show ads
        = false

    //kiểm tra trạng thái ad splash, ko cho load, show khi đang show loading ads splash
    private var isShowLoadingSplash = false

    private var isFan = false
    private var isAdcolony = false
    private var isAppLovin = false
    var isTimeDelay = false //xử lý delay time show ads, = true mới show ads

    private var openActivityAfterShowInterAds = false
    private var context: Context? = null
    var mInterstitialSplash: InterstitialAd? = null
    var interstitialAd: InterstitialAd? = null


    fun setFan(fan: Boolean) {
        isFan = fan
    }

    fun setColony(adcolony: Boolean) {
        isAdcolony = adcolony
    }

    fun setAppLovin(appLovin: Boolean) {
        isAppLovin = appLovin
    }

    /**
     * Giới hạn số lần click trên 1 admod tren 1 ngay
     *
     * @param maxClickAds
     */
    fun setMaxClickAdsPerDay(maxClickAds: Int) {
        this.maxClickAds = maxClickAds
    }

    fun setNumToShowAds(numShowAds: Int) {
        this.numShowAds = numShowAds
    }

    fun setNumToShowAds(numShowAds: Int, currentClicked: Int) {
        this.numShowAds = numShowAds
        this.currentClicked = currentClicked
    }

    /**
     * khởi tạo admod
     *
     * @param context
     */
    fun init(context: Context, testDeviceList: List<String?>?) {
        MobileAds.initialize(context) { }
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder().setTestDeviceIds(testDeviceList).build()
        )
        this.context = context
    }

    fun init(context: Context?) {
        context?.let {
            MobileAds.initialize(it)
        }

        if (BuildConfig.DEBUG) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf((context as Activity?)?.let { getDeviceId(it) }))
                    .build()
            )
        }
        this.context = context
    }

    fun setOpenActivityAfterShowInterAds(openActivityAfterShowInterAds: Boolean) {
        this.openActivityAfterShowInterAds = openActivityAfterShowInterAds
    }

    fun getAdRequest(): AdRequest? {
        val builder = AdRequest.Builder()
        if (isFan) {
            val extras = FacebookExtras()
                .setNativeBanner(true)
                .build()
            builder.addNetworkExtrasBundle(FacebookAdapter::class.java, extras)
        }
        if (isAdcolony) {
            AdColonyBundleBuilder.setShowPrePopup(true)
            AdColonyBundleBuilder.setShowPostPopup(true)
            builder.addNetworkExtrasBundle(
                AdColonyAdapter::class.java,
                AdColonyBundleBuilder.build()
            )
        }
        if (isAppLovin) {
            val extras = AppLovinExtras.Builder()
                .setMuteAudio(true)
                .build()
            builder.addNetworkExtrasBundle(ApplovinAdapter::class.java, extras)
        }
        //        builder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR);
        return builder.build()
    }

    fun isInterstitialSplashLoaded(): Boolean {
        return mInterstitialSplash != null
    }

    fun getInterstitialSplash(): InterstitialAd? {
        return mInterstitialSplash
    }

    /**
     * Load quảng cáo Full tại màn SplashActivity
     * Sau khoảng thời gian timeout thì load ads và callback về cho View
     *
     * @param context
     * @param id
     * @param timeOut    : thời gian chờ ads, timeout <= 0 tương đương với việc bỏ timeout
     * @param timeDelay  : thời gian chờ show ad từ lúc load ads
     * @param adListener
     */
    fun loadSplashInterstitialAds(
        context: Context?,
        id: String,
        timeOut: Long,
        timeDelay: Long,
        adListener: AdsCallback?
    ) {
        isTimeDelay = false
        isTimeout = false
        Log.i(
            TAG,
            "loadSplashInterstitialAds  start time loading:" + Calendar.getInstance().timeInMillis + "    ShowLoadingSplash:" + isShowLoadingSplash
        )
        if (AppPurchase.instance?.isPurchased(context) == true) {
            adListener?.onAdClosed()
            return
        }
        Handler(Looper.getMainLooper()).postDelayed(Runnable { //check delay show ad splash
            if (mInterstitialSplash != null) {
                Log.i(TAG, "loadSplashInterstitialAds:show ad on delay ")
                (context as Activity?)?.let { onShowSplash(it, adListener) }
                return@Runnable
            }
            Log.i(TAG, "loadSplashInterstitialAds: delay validate")
            isTimeDelay = true
        }, timeDelay)

        if (timeOut > 0) {
            handlerTimeout = Handler(Looper.getMainLooper())
            rdTimeout = Runnable {
                Log.e(TAG, "loadSplashInterstitialAds: on timeout")
                isTimeout = true
                if (mInterstitialSplash != null) {
                    Log.i(TAG, "loadSplashInterstitialAds:show ad on timeout ")
                    (context as Activity?)?.let { onShowSplash(it, adListener) }
                    return@Runnable
                }
                if (adListener != null) {
                    adListener.onLoadAdsTimeout()
                    isShowLoadingSplash = false
                }
            }
            handlerTimeout?.postDelayed(rdTimeout!!, timeOut)
        }

        isShowLoadingSplash = true
        if (context != null) {
            getInterstitialAds(context, id, object : AdsCallback() {
                override fun onInterstitialLoad(interstitialAd: InterstitialAd?) {
                    super.onInterstitialLoad(interstitialAd)
                    Log.e(
                        TAG,
                        "loadSplashInterstitialAds  end time loading success:" + Calendar.getInstance().timeInMillis + "     time limit:" + isTimeout
                    )
                    if (isTimeout) return
                    if (interstitialAd != null) {
                        mInterstitialSplash = interstitialAd
                        if (isTimeDelay) {
                            (context as Activity?)?.let { onShowSplash(it, adListener) }
                            Log.i(
                                TAG,
                                "loadSplashInterstitialAds:show ad on loaded "
                            )
                        }
                    }
                }

                override fun onAdFailedToLoad(i: LoadAdError?) {
                    super.onAdFailedToLoad(i)
                    Log.e(
                        TAG,
                        "loadSplashInterstitialAds  end time loading error:" + Calendar.getInstance().timeInMillis + "     time limit:" + isTimeout
                    )
                    if (isTimeout) return
                    if (adListener != null) {
                        if (handlerTimeout != null && rdTimeout != null) {
                            handlerTimeout?.removeCallbacks(rdTimeout!!)
                        }
                        if (i != null) Log.e(
                            TAG,
                            "loadSplashInterstitialAds: load fail " + i.message
                        )
                        adListener.onAdFailedToLoad(i)
                    }
                }
            })
        }
    }

    private fun onShowSplash(activity: Activity, adListener: AdsCallback?) {
        isShowLoadingSplash = true
        if (mInterstitialSplash != null) {
            mInterstitialSplash!!.setOnPaidEventListener { adValue: AdValue ->
                Log.d(
                    TAG,
                    "OnPaidEvent splash:" + adValue.valueMicros
                )
                FirebaseAnalyticsUtil.logPaidAdImpression(
                    context,
                    adValue,
                    mInterstitialSplash!!.adUnitId,
                    mInterstitialSplash!!.responseInfo
                        .mediationAdapterClassName
                )
            }
        }
        if (handlerTimeout != null && rdTimeout != null) {
            handlerTimeout!!.removeCallbacks(rdTimeout!!)
        }
        adListener?.onAdLoaded()
        mInterstitialSplash?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                isShowLoadingSplash = false
            }

            override fun onAdDismissedFullScreenContent() {
                if (AppOpenManager.instance?.isInitialized == true) {
                    AppOpenManager.instance!!.enableAppResume()
                }
                if (adListener != null) {
                    if (!openActivityAfterShowInterAds) {
                        adListener.onAdClosed()
                    }
                    dialog?.dismiss()
                }
                mInterstitialSplash = null
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                mInterstitialSplash = null
                isShowLoadingSplash = false
                if (adListener != null) {
                    if (!openActivityAfterShowInterAds) {
                        adListener.onAdFailedToShow(adError)
                    }
                    dialog?.dismiss()
                }
            }
        }
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            try {
                if (dialog?.isShowing == true) dialog?.dismiss()
                dialog = PrepareLoadingAdsDialog(activity)
                try {
                    dialog?.show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    adListener?.onAdClosed()
                    return
                }
            } catch (e: Exception) {
                dialog = null
                e.printStackTrace()
            }
            Handler(Looper.getMainLooper()).postDelayed({
                if (AppOpenManager.instance?.isInitialized == true) {
                    AppOpenManager.instance?.disableAppResume()
                }
                if (openActivityAfterShowInterAds && adListener != null) {
                    adListener.onAdClosed()
                    Handler(Looper.getMainLooper()).postDelayed(
                        {
                            if (dialog?.isShowing == true && !activity.isDestroyed) dialog?.dismiss()
                        }, 1500
                    )
                }
                mInterstitialSplash!!.show(activity)
                isShowLoadingSplash = false
            }, 800)
        }
    }

    fun loadInterstitialAds(
        context: Context,
        id: String,
        timeOut: Long,
        adListener: AdsCallback?
    ) {
        isTimeout = false
        if (AppPurchase.instance?.isPurchased(context) == true) {
            adListener?.onAdClosed()
            return
        }
        interstitialAd = null
        getInterstitialAds(context, id, object : AdsCallback() {
            override fun onInterstitialLoad(interstitialAd: InterstitialAd?) {
                this@Adsmod.interstitialAd = interstitialAd
                if (interstitialAd == null) {
                    adListener?.onAdFailedToLoad(null)
                    return
                }
                if (handlerTimeout != null && rdTimeout != null) {
                    handlerTimeout!!.removeCallbacks(rdTimeout!!)
                }
                if (isTimeout) {
                    return
                }
                if (adListener != null) {
                    if (handlerTimeout != null && rdTimeout != null) {
                        handlerTimeout!!.removeCallbacks(rdTimeout!!)
                    }
                    adListener.onInterstitialLoad(interstitialAd)
                }
                interstitialAd.setOnPaidEventListener { adValue: AdValue ->
                    Log.d(
                        TAG,
                        "OnPaidEvent loadInterstitialAds:" + adValue.valueMicros
                    )
                    FirebaseAnalyticsUtil.logPaidAdImpression(
                        context,
                        adValue,
                        interstitialAd.adUnitId,
                        interstitialAd.responseInfo
                            .mediationAdapterClassName
                    )
                }
            }

            override fun onAdFailedToLoad(i: LoadAdError?) {
                if (adListener != null) {
                    if (handlerTimeout != null && rdTimeout != null) {
                        handlerTimeout!!.removeCallbacks(rdTimeout!!)
                    }
                    adListener.onAdFailedToLoad(i)
                }
            }
        })
        if (timeOut > 0) {
            handlerTimeout = Handler(Looper.getMainLooper())
            rdTimeout = Runnable {
                isTimeout = true
                if (interstitialAd != null) {
                    adListener?.onInterstitialLoad(interstitialAd)
                    return@Runnable
                }
                adListener?.onAdClosed()
            }
            handlerTimeout!!.postDelayed(rdTimeout!!, timeOut)
        }
    }


    /**
     * Trả về 1 InterstitialAd và request Ads
     *
     * @param context
     * @param id
     * @return
     */
    fun getInterstitialAds(context: Context, id: String, AdsCallback: AdsCallback?) {
        if (Arrays.asList(*context.resources.getStringArray(R.array.list_id_test)).contains(id)) {
            showTestIdAlert(context, INTERS_ADS, id)
        }
        if (AppPurchase.instance?.isPurchased(context) == true || AdsmodHelper.getNumClickAdsPerDay(
                context,
                id
            ) >= maxClickAds
        ) {
            AdsCallback?.onInterstitialLoad(null)
        }
        getAdRequest()?.let { adRequest ->
            InterstitialAd.load(context, id, adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        AdsCallback?.onInterstitialLoad(interstitialAd)

                        //tracking adjust
                        interstitialAd.setOnPaidEventListener { adValue: AdValue ->
                            Log.d(
                                TAG,
                                "OnPaidEvent getInterstitialAds:" + adValue.valueMicros
                            )
                            FirebaseAnalyticsUtil.logPaidAdImpression(
                                context,
                                adValue,
                                interstitialAd.adUnitId,
                                interstitialAd.responseInfo
                                    .mediationAdapterClassName
                            )
                        }
                        Log.i(TAG, "onAdLoaded")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        // Handle the error
                        Log.i(TAG, loadAdError.message)
                        AdsCallback?.onAdFailedToLoad(loadAdError)
                    }
                })
        }

    }


    /**
     * Hiển thị ads theo số lần được xác định trước và callback result
     * vd: click vào 3 lần thì show ads full.
     * AdmodHelper.setupAdmodData(context) -> kiểm tra xem app đc hoạt động đc 1 ngày chưa nếu YES thì reset lại số lần click vào ads
     *
     * @param context
     * @param mInterstitialAd
     * @param callback
     */
    fun showInterstitialAdByTimes(
        context: Context,
        mInterstitialAd: InterstitialAd?,
        callback: AdsCallback?
    ) {
        showInterstitialAdByTimes(context, mInterstitialAd, callback, true)
    }

    /**
     * Hiển thị ads  timeout
     * Sử dụng khi reopen app in splash
     *
     * @param context
     * @param mInterstitialAd
     * @param timeDelay
     */
    fun showInterstitialAdByTimes(
        context: Context,
        mInterstitialAd: InterstitialAd?,
        callback: AdsCallback?,
        timeDelay: Long
    ) {
        if (timeDelay > 0) {
            handlerTimeout = Handler(Looper.getMainLooper())
            rdTimeout =
                Runnable { forceShowInterstitial(context, mInterstitialAd, callback, false) }
            handlerTimeout!!.postDelayed(rdTimeout!!, timeDelay)
        } else {
            forceShowInterstitial(context, mInterstitialAd, callback, false)
        }
    }


    /**
     * Hiển thị ads theo số lần được xác định trước và callback result
     * vd: click vào 3 lần thì show ads full.
     * AdmodHelper.setupAdmodData(context) -> kiểm tra xem app đc hoạt động đc 1 ngày chưa nếu YES thì reset lại số lần click vào ads
     *
     * @param context
     * @param mInterstitialAd
     * @param callback
     * @param shouldReloadAds
     */
    fun showInterstitialAdByTimes(
        context: Context,
        mInterstitialAd: InterstitialAd?,
        callback: AdsCallback?,
        shouldReloadAds: Boolean
    ) {
        AdsmodHelper.setupAdmodData(context)
        if (AppPurchase.instance?.isPurchased(context) == true) {
            callback?.onAdClosed()
            return
        }
        if (mInterstitialAd == null) {
            callback?.onAdClosed()
            return
        }
        mInterstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                super.onAdDismissedFullScreenContent()
                // Called when fullscreen content is dismissed.
                if (AppOpenManager.instance?.isInitialized == true) {
                    AppOpenManager.instance?.enableAppResume()
                }
                if (callback != null) {
                    if (!openActivityAfterShowInterAds) {
                        callback.onAdClosed()
                    }
                    dialog?.dismiss()
                }
                Log.e(TAG, "onAdDismissedFullScreenContent")
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                super.onAdFailedToShowFullScreenContent(adError)
                Log.e(
                    TAG,
                    "onAdFailedToShowFullScreenContent: " + adError.message
                )
                // Called when fullscreen content failed to show.
                if (callback != null) {
                    if (!openActivityAfterShowInterAds) {
                        callback.onAdClosed()
                    }
                    dialog?.dismiss()
                }
            }

        }
        if (AdsmodHelper.getNumClickAdsPerDay(context, mInterstitialAd.adUnitId) < maxClickAds) {
            showInterstitialAd(context, mInterstitialAd, callback)
            return
        }
        callback?.onAdClosed()
    }

    /**
     * Bắt buộc hiển thị  ads full và callback result
     *
     * @param context
     * @param mInterstitialAd
     * @param callback
     */
    fun forceShowInterstitial(
        context: Context,
        mInterstitialAd: InterstitialAd?,
        callback: AdsCallback?
    ) {
        forceShowInterstitial(context, mInterstitialAd, callback, true)
    }

    /**
     * Bắt buộc hiển thị  ads full và callback result
     *
     * @param context
     * @param mInterstitialAd
     * @param callback
     */
    fun forceShowInterstitial(
        context: Context,
        mInterstitialAd: InterstitialAd?,
        callback: AdsCallback?,
        shouldReload: Boolean
    ) {
        currentClicked = numShowAds
        showInterstitialAdByTimes(context, mInterstitialAd, callback, shouldReload)
    }

    /**
     * Kiểm tra và hiện thị ads
     *
     * @param context
     * @param mInterstitialAd
     * @param callback
     */
    private fun showInterstitialAd(
        context: Context,
        mInterstitialAd: InterstitialAd?,
        callback: AdsCallback?
    ) {
        currentClicked++
        if (currentClicked >= numShowAds && mInterstitialAd != null) {
            if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                try {
                    if (dialog?.isShowing == true) dialog?.dismiss()
                    dialog = PrepareLoadingAdsDialog(context)
                    try {
                        dialog?.show()
                    } catch (e: Exception) {
                        callback?.onAdClosed()
                        return
                    }
                } catch (e: Exception) {
                    dialog = null
                    e.printStackTrace()
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    if (AppOpenManager.instance?.isInitialized == true) {
                        AppOpenManager.instance?.disableAppResume()
                    }
                    if (openActivityAfterShowInterAds && callback != null) {
                        callback.onAdClosed()
                        Handler(Looper.getMainLooper()).postDelayed(
                            { if (dialog?.isShowing == true && !(context as Activity).isDestroyed) dialog?.dismiss() },
                            1500
                        )
                    }
                    mInterstitialAd.show(context as Activity)
                }, 200)
            }
            currentClicked = 0
        } else if (callback != null) {
            dialog?.dismiss()
            callback.onAdClosed()
        }
    }

    /**
     * Load quảng cáo Banner Trong Activity set Inline adaptive banners
     *
     * @param mActivity
     * @param id
     */
    fun loadBanner(mActivity: Activity, id: String, useInlineAdaptive: Boolean?) {
        val adContainer = mActivity.findViewById<FrameLayout>(R.id.banner_container)
        val containerShimmer: ShimmerFrameLayout =
            mActivity.findViewById(R.id.shimmer_container_banner)
        loadBanner(mActivity, id, adContainer, containerShimmer, true)
    }

    /**
     * Load quảng cáo Banner Trong Activity
     *
     * @param mActivity
     * @param id
     */
    fun loadBanner(mActivity: Activity, id: String) {
        val adContainer = mActivity.findViewById<FrameLayout>(R.id.banner_container)
        val containerShimmer: ShimmerFrameLayout =
            mActivity.findViewById(R.id.shimmer_container_banner)
        loadBanner(mActivity, id, adContainer, containerShimmer, false)
    }

    /**
     * Load quảng cáo Banner Trong Activity
     *
     * @param mActivity
     * @param id
     */
    fun loadBanner(mActivity: Activity, id: String, callback: AdsCallback?) {
        val adContainer = mActivity.findViewById<FrameLayout>(R.id.banner_container)
        val containerShimmer: ShimmerFrameLayout =
            mActivity.findViewById(R.id.shimmer_container_banner)
        loadBanner(mActivity, id, adContainer, containerShimmer, callback, false)
    }


    /**
     * Load quảng cáo Banner Trong Activity set Inline adaptive banners
     *
     * @param mActivity
     * @param id
     */
    fun loadBanner(
        mActivity: Activity,
        id: String,
        callback: AdsCallback?,
        useInlineAdaptive: Boolean
    ) {
        val adContainer = mActivity.findViewById<FrameLayout>(R.id.banner_container)
        val containerShimmer: ShimmerFrameLayout =
            mActivity.findViewById(R.id.shimmer_container_banner)
        loadBanner(mActivity, id, adContainer, containerShimmer, callback, useInlineAdaptive)
    }

    /**
     * Load Quảng Cáo Banner Trong Fragment
     *
     * @param mActivity
     * @param id
     * @param rootView
     */
    fun loadBannerFragment(mActivity: Activity, id: String, rootView: View) {
        val adContainer = rootView.findViewById<FrameLayout>(R.id.banner_container)
        val containerShimmer: ShimmerFrameLayout =
            rootView.findViewById(R.id.shimmer_container_banner)
        loadBanner(mActivity, id, adContainer, containerShimmer, false)
    }

    /**
     * Load Quảng Cáo Banner Trong Fragment set Inline adaptive banners
     *
     * @param mActivity
     * @param id
     * @param rootView
     */
    fun loadBannerFragment(
        mActivity: Activity,
        id: String,
        rootView: View,
        useInlineAdaptive: Boolean
    ) {
        val adContainer = rootView.findViewById<FrameLayout>(R.id.banner_container)
        val containerShimmer: ShimmerFrameLayout =
            rootView.findViewById(R.id.shimmer_container_banner)
        loadBanner(mActivity, id, adContainer, containerShimmer, useInlineAdaptive)
    }

    /**
     * Load Quảng Cáo Banner Trong Fragment
     *
     * @param mActivity
     * @param id
     * @param rootView
     */
    fun loadBannerFragment(
        mActivity: Activity,
        id: String,
        rootView: View,
        callback: AdsCallback?
    ) {
        val adContainer = rootView.findViewById<FrameLayout>(R.id.banner_container)
        val containerShimmer: ShimmerFrameLayout =
            rootView.findViewById(R.id.shimmer_container_banner)
        loadBanner(mActivity, id, adContainer, containerShimmer, callback, false)
    }

    /**
     * Load Quảng Cáo Banner Trong Fragment set Inline adaptive banners
     *
     * @param mActivity
     * @param id
     * @param rootView
     * @param callback
     */
    fun loadBannerFragment(
        mActivity: Activity,
        id: String,
        rootView: View,
        callback: AdsCallback?,
        useInlineAdaptive: Boolean
    ) {
        val adContainer = rootView.findViewById<FrameLayout>(R.id.banner_container)
        val containerShimmer: ShimmerFrameLayout =
            rootView.findViewById(R.id.shimmer_container_banner)
        loadBanner(mActivity, id, adContainer, containerShimmer, callback, useInlineAdaptive)
    }

    var bannerLoaded = false

    private fun loadBanner(
        mActivity: Activity,
        id: String,
        adContainer: FrameLayout,
        containerShimmer: ShimmerFrameLayout,
        useInlineAdaptive: Boolean
    ) {
        if (Arrays.asList(*mActivity.resources.getStringArray(R.array.list_id_test)).contains(id)) {
            showTestIdAlert(mActivity, BANNER_ADS, id)
        }
        if (AppPurchase.instance?.isPurchased(mActivity) == true) {
            containerShimmer.visibility = View.GONE
            return
        }
        containerShimmer.visibility = View.VISIBLE
        containerShimmer.startShimmer()
        try {
            val adView = AdView(mActivity)
            adView.adUnitId = id
            adContainer.addView(adView)
            val adSize = getAdSize(mActivity, useInlineAdaptive)
            adView.adSize = adSize
            adView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            getAdRequest()?.let { adRequest ->
                adView.loadAd(adRequest)
            }

            adView.adListener = object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    containerShimmer.stopShimmer()
                    adContainer.visibility = View.GONE
                    containerShimmer.visibility = View.GONE
                }

                override fun onAdLoaded() {
                    Log.d(
                        TAG,
                        "Banner adapter class name: " + adView.responseInfo?.mediationAdapterClassName
                    )
                    containerShimmer.stopShimmer()
                    containerShimmer.visibility = View.GONE
                    adContainer.visibility = View.VISIBLE
                    adView.setOnPaidEventListener { adValue: AdValue? ->
                        FirebaseAnalyticsUtil.logPaidAdImpression(
                            context,
                            adValue,
                            adView.adUnitId,
                            adView.responseInfo?.mediationAdapterClassName
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadBanner(
        mActivity: Activity,
        id: String,
        adContainer: FrameLayout,
        containerShimmer: ShimmerFrameLayout,
        callback: AdsCallback?,
        useInlineAdaptive: Boolean
    ) {
        if (Arrays.asList(*mActivity.resources.getStringArray(R.array.list_id_test)).contains(id)) {
            showTestIdAlert(mActivity, BANNER_ADS, id)
        }
        if (AppPurchase.instance?.isPurchased(mActivity) == true) {
            containerShimmer.visibility = View.GONE
            return
        }
        containerShimmer.visibility = View.VISIBLE
        containerShimmer.startShimmer()
        try {
            val adView = AdView(mActivity)
            adView.adUnitId = id
            adContainer.addView(adView)
            val adSize = getAdSize(mActivity, useInlineAdaptive)
            adView.adSize = adSize
            adView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            getAdRequest()?.let { adRequest ->
                adView.loadAd(adRequest)
            }
            adView.adListener = object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    containerShimmer.stopShimmer()
                    adContainer.visibility = View.GONE
                    containerShimmer.visibility = View.GONE
                }

                override fun onAdLoaded() {
                    Log.d(
                        TAG,
                        "Banner adapter class name: " + adView.responseInfo?.mediationAdapterClassName
                    )
                    containerShimmer.stopShimmer()
                    containerShimmer.visibility = View.GONE
                    adContainer.visibility = View.VISIBLE
                    adView.setOnPaidEventListener { adValue: AdValue? ->
                        FirebaseAnalyticsUtil.logPaidAdImpression(
                            context,
                            adValue,
                            adView.adUnitId,
                            adView.responseInfo?.mediationAdapterClassName
                        )
                    }
                }

                override fun onAdClicked() {
                    super.onAdClicked()
                    if (callback != null) {
                        callback.onAdClicked()
                        Log.d(TAG, "onAdClicked")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getAdSize(mActivity: Activity, useInlineAdaptive: Boolean): AdSize {

        // Step 2 - Determine the screen width (less decorations) to use for the ad width.
        val display = mActivity.windowManager.defaultDisplay
        val outMetrics = DisplayMetrics()
        display.getMetrics(outMetrics)
        val widthPixels = outMetrics.widthPixels.toFloat()
        val density = outMetrics.density
        val adWidth = (widthPixels / density).toInt()

        // Step 3 - Get adaptive ad size and return for setting on the ad view.
        return if (useInlineAdaptive) {
            AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(mActivity, adWidth)
        } else AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(mActivity, adWidth)
    }

    /**
     * load quảng cáo big native
     *
     * @param mActivity
     * @param id
     */
    fun loadNative(mActivity: Activity, id: String) {
        val frameLayout = mActivity.findViewById<FrameLayout>(R.id.fl_adplaceholder)
        val containerShimmer: ShimmerFrameLayout =
            mActivity.findViewById(R.id.shimmer_container_native)
        loadNative(mActivity, containerShimmer, frameLayout, id, R.layout.native_admob_ad)
    }

    fun loadNativeFragment(mActivity: Activity, id: String, parent: View) {
        val frameLayout = parent.findViewById<FrameLayout>(R.id.fl_adplaceholder)
        val containerShimmer: ShimmerFrameLayout =
            parent.findViewById(R.id.shimmer_container_native)
        loadNative(mActivity, containerShimmer, frameLayout, id, R.layout.native_admob_ad)
    }

    fun loadLargeNative(mActivity: Activity, id: String) {
        val frameLayout = mActivity.findViewById<FrameLayout>(R.id.fl_adplaceholder)
        val containerShimmer: ShimmerFrameLayout =
            mActivity.findViewById(R.id.shimmer_container_native)
        loadNative(mActivity, containerShimmer, frameLayout, id, R.layout.native_admob_ad)
    }

    fun loadSmallNative(mActivity: Activity, adUnitId: String) {
        val frameLayout = mActivity.findViewById<FrameLayout>(R.id.fl_adplaceholder)
        val containerShimmer: ShimmerFrameLayout =
            mActivity.findViewById(R.id.shimmer_container_small_native)
        loadNative(
            mActivity,
            containerShimmer,
            frameLayout,
            adUnitId,
            R.layout.small_native_admod_ad
        )
    }

    fun loadSmallNativeFragment(mActivity: Activity, adUnitId: String, parent: View) {
        val frameLayout = parent.findViewById<FrameLayout>(R.id.fl_adplaceholder)
        val containerShimmer: ShimmerFrameLayout =
            parent.findViewById(R.id.shimmer_container_small_native)
        loadNative(
            mActivity,
            containerShimmer,
            frameLayout,
            adUnitId,
            R.layout.small_native_admod_ad
        )
    }

    fun loadNativeAd(context: Context, id: String, callback: AdsCallback?) {
        if (Arrays.asList(*context.resources.getStringArray(R.array.list_id_test)).contains(id)) {
            showTestIdAlert(context, NATIVE_ADS, id)
        }
        if (AppPurchase.instance?.isPurchased(context) == true) {
            callback?.onAdClosed()
            return
        }
        val videoOptions = VideoOptions.Builder()
            .setStartMuted(true)
            .build()
        val adOptions = NativeAdOptions.Builder()
            .setVideoOptions(videoOptions)
            .build()
        val adLoader = AdLoader.Builder(context, id)
            .forNativeAd { nativeAd ->
                callback?.onUnifiedNativeAdLoaded(nativeAd)
                nativeAd.setOnPaidEventListener { adValue: AdValue? ->
                    FirebaseAnalyticsUtil.logPaidAdImpression(
                        context,
                        adValue,
                        "",
                        "native"
                    )
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "NativeAd onAdFailedToLoad: " + error.message)
                    callback?.onAdFailedToLoad(error)
                }

                override fun onAdClicked() {
                    super.onAdClicked()
                    if (callback != null) {
                        callback.onAdClicked()
                        Log.d(TAG, "onAdClicked")
                    }
                }
            })
            .withNativeAdOptions(adOptions)
            .build()
        adLoader.loadAd(getAdRequest()!!)
    }

    private fun loadNative(
        context: Context,
        containerShimmer: ShimmerFrameLayout,
        frameLayout: FrameLayout,
        id: String,
        layout: Int
    ) {
        if (Arrays.asList(*context.resources.getStringArray(R.array.list_id_test)).contains(id)) {
            showTestIdAlert(context, NATIVE_ADS, id)
        }
        if (AppPurchase.instance?.isPurchased(context) == true) {
            containerShimmer.visibility = View.GONE
            return
        }
        frameLayout.removeAllViews()
        frameLayout.visibility = View.GONE
        containerShimmer.visibility = View.VISIBLE
        containerShimmer.startShimmer()
        val videoOptions = VideoOptions.Builder()
            .setStartMuted(true)
            .build()
        val adOptions = NativeAdOptions.Builder()
            .setVideoOptions(videoOptions)
            .build()
        val adLoader = AdLoader.Builder(context, id)
            .forNativeAd { nativeAd ->
                containerShimmer.stopShimmer()
                containerShimmer.visibility = View.GONE
                frameLayout.visibility = View.VISIBLE
                @SuppressLint("InflateParams") val adView = LayoutInflater.from(context)
                    .inflate(layout, null) as NativeAdView
                nativeAd.setOnPaidEventListener { adValue: AdValue? ->
                    FirebaseAnalyticsUtil.logPaidAdImpression(
                        context,
                        adValue,
                        "",
                        "native"
                    )
                }
                populateUnifiedNativeAdView(nativeAd, adView)
                frameLayout.removeAllViews()
                frameLayout.addView(adView)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "onAdFailedToLoad: " + error.message)
                    containerShimmer.stopShimmer()
                    containerShimmer.visibility = View.GONE
                    frameLayout.visibility = View.GONE
                }
            })
            .withNativeAdOptions(adOptions)
            .build()
        getAdRequest()?.let {
            adLoader.loadAd(it)
        }

    }

    private fun loadNative(
        context: Context,
        containerShimmer: ShimmerFrameLayout,
        frameLayout: FrameLayout,
        id: String,
        layout: Int,
        callback: AdsCallback?
    ) {
        if (Arrays.asList(*context.resources.getStringArray(R.array.list_id_test)).contains(id)) {
            showTestIdAlert(context, NATIVE_ADS, id)
        }
        if (AppPurchase.instance?.isPurchased(context) == true) {
            containerShimmer.visibility = View.GONE
            return
        }
        frameLayout.removeAllViews()
        frameLayout.visibility = View.GONE
        containerShimmer.visibility = View.VISIBLE
        containerShimmer.startShimmer()
        val videoOptions = VideoOptions.Builder()
            .setStartMuted(true)
            .build()
        val adOptions = NativeAdOptions.Builder()
            .setVideoOptions(videoOptions)
            .build()
        val adLoader = AdLoader.Builder(context, id)
            .forNativeAd { nativeAd ->
                containerShimmer.stopShimmer()
                containerShimmer.visibility = View.GONE
                frameLayout.visibility = View.VISIBLE
                @SuppressLint("InflateParams") val adView = LayoutInflater.from(context)
                    .inflate(layout, null) as NativeAdView
                nativeAd.setOnPaidEventListener { adValue: AdValue? ->
                    FirebaseAnalyticsUtil.logPaidAdImpression(
                        context,
                        adValue,
                        "",
                        "native"
                    )
                }
                populateUnifiedNativeAdView(nativeAd, adView)
                frameLayout.removeAllViews()
                frameLayout.addView(adView)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "onAdFailedToLoad: " + error.message)
                    containerShimmer.stopShimmer()
                    containerShimmer.visibility = View.GONE
                    frameLayout.visibility = View.GONE
                }

                override fun onAdClicked() {
                    super.onAdClicked()
                    if (callback != null) {
                        callback.onAdClicked()
                        Log.d(TAG, "onAdClicked")
                    }
                }
            })
            .withNativeAdOptions(adOptions)
            .build()
        getAdRequest()?.let {
            adLoader.loadAd(it)
        }
    }


    fun populateUnifiedNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        adView.mediaView = adView.findViewById(R.id.ad_media)
        adView.mediaView?.let { mediaView ->
            mediaView.postDelayed({
                if (context != null && BuildConfig.DEBUG) {
                    val sizeMin = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 120f,
                        context!!.resources.displayMetrics
                    )
                    Log.e(TAG, "Native sizeMin: $sizeMin")
                    Log.e(
                        TAG,
                        "Native w/h media : " + mediaView.width + "/" + mediaView.height
                    )
                    if (mediaView.width < sizeMin || mediaView.height < sizeMin) {
                        Toast.makeText(context, "Size media native not valid", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }, 1000)
        }
        // Set other ad assets.
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)
        adView.advertiserView = adView.findViewById(R.id.ad_advertiser)

        // The headline is guaranteed to be in every UnifiedNativeAd.
        try {
            (adView.headlineView as TextView).text = nativeAd.headline
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // These assets aren't guaranteed to be in every UnifiedNativeAd, so it's important to
        // check before trying to display them.
        try {
            if (nativeAd.body == null) {
                adView.bodyView?.visibility = View.INVISIBLE
            } else {
                adView.bodyView?.visibility = View.VISIBLE
                (adView.bodyView as TextView).text = nativeAd.body
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd.callToAction == null) {
                Objects.requireNonNull(adView.callToActionView).visibility = View.INVISIBLE
            } else {
                Objects.requireNonNull(adView.callToActionView).visibility = View.VISIBLE
                (adView.callToActionView as TextView).text = nativeAd.callToAction
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd.icon == null) {
                Objects.requireNonNull(adView.iconView).visibility = View.GONE
            } else {
                (adView.iconView as ImageView).setImageDrawable(
                    nativeAd.icon?.drawable
                )
                adView.iconView?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd.price == null) {
                Objects.requireNonNull(adView.priceView).visibility = View.INVISIBLE
            } else {
                Objects.requireNonNull(adView.priceView).visibility = View.VISIBLE
                (adView.priceView as TextView).text = nativeAd.price
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd.store == null) {
                Objects.requireNonNull(adView.storeView).visibility = View.INVISIBLE
            } else {
                Objects.requireNonNull(adView.storeView).visibility = View.VISIBLE
                (adView.storeView as TextView).text = nativeAd.store
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd.starRating == null) {
                Objects.requireNonNull(adView.starRatingView).visibility = View.INVISIBLE
            } else {
                (Objects.requireNonNull(adView.starRatingView) as RatingBar).rating =
                    nativeAd.starRating?.toFloat() ?: 0f
                adView.starRatingView?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd.advertiser == null) {
                adView.advertiserView?.visibility = View.INVISIBLE
            } else {
                (adView.advertiserView as TextView).text = nativeAd.advertiser
                adView.advertiserView?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // This method tells the Google Mobile Ads SDK that you have finished populating your
        // native ad view with this native ad. The SDK will populate the adView's MediaView
        // with the media content from this native ad.
        adView.setNativeAd(nativeAd)
    }


    private var rewardedAd: RewardedAd? = null

    /**
     * Khởi tạo quảng cáo reward
     *
     * @param context
     * @param id
     */
    fun initRewardAds(context: Context, id: String) {
        if (Arrays.asList(*context.resources.getStringArray(R.array.list_id_test)).contains(id)) {
            showTestIdAlert(context, REWARD_ADS, id)
        }
        if (AppPurchase.instance?.isPurchased(context) == true) {
            return
        }
        nativeId = id
        if (AppPurchase.instance?.isPurchased(context) == true) {
            return
        }
        getAdRequest()?.let { adRequest ->
            RewardedAd.load(context, id, adRequest, object : RewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    this@Adsmod.rewardedAd = rewardedAd
                    this@Adsmod.rewardedAd?.onPaidEventListener =
                        OnPaidEventListener { adValue: AdValue? ->
                            FirebaseAnalyticsUtil.logPaidAdImpression(
                                context,
                                adValue,
                                "",
                                "native"
                            )
                        }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    Log.e(
                        TAG,
                        "RewardedAd onAdFailedToLoad: " + loadAdError.message
                    )
                }
            })
        }

    }

    /**
     * Khởi tạo quảng cáo reward
     *
     * @param context
     * @param id
     */
    fun initRewardAds(context: Context, id: String, callback: AdsCallback) {
        if (listOf(*context.resources.getStringArray(R.array.list_id_test)).contains(id)) {
            showTestIdAlert(context, REWARD_ADS, id)
        }
        if (AppPurchase.instance?.isPurchased(context) == true) {
            return
        }
        nativeId = id
        if (AppPurchase.instance?.isPurchased(context) == true) {
            return
        }
        getAdRequest()?.let { adRequest ->
            RewardedAd.load(context, id, adRequest, object : RewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    callback.onAdLoaded()
                    this@Adsmod.rewardedAd = rewardedAd
                    this@Adsmod.rewardedAd?.onPaidEventListener =
                        OnPaidEventListener { adValue: AdValue? ->
                            FirebaseAnalyticsUtil.logPaidAdImpression(
                                context,
                                adValue,
                                "",
                                "native"
                            )
                        }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    callback.onAdFailedToLoad(loadAdError)
                    Log.e(
                        TAG,
                        "RewardedAd onAdFailedToLoad: " + loadAdError.message
                    )
                }
            })
        }

    }

    fun getRewardedAd(): RewardedAd? {
        return rewardedAd
    }

    /**
     * Show quảng cáo reward và nhận kết quả trả về
     *
     * @param context
     * @param adsCallback
     */
    fun showRewardAds(context: Activity, adsCallback: RewardCallback?) {
        if (AppPurchase.instance?.isPurchased(context) == true) {
            adsCallback?.onUserEarnedReward(null)
            return
        }
        if (rewardedAd == null) {
            initRewardAds(context, nativeId!!)
            adsCallback?.onRewardedAdFailedToShow(0)
            return
        } else {
            this.rewardedAd?.fullScreenContentCallback = object :
                FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    super.onAdDismissedFullScreenContent()
                    adsCallback?.onRewardedAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    super.onAdFailedToShowFullScreenContent(adError)
                    adsCallback?.onRewardedAdFailedToShow(adError.code)
                }
            }
            rewardedAd?.show(context) { rewardItem ->
                if (adsCallback != null) {
                    adsCallback.onUserEarnedReward(rewardItem)
                    initRewardAds(context, nativeId!!)
                }
            }
        }
    }


    @SuppressLint("HardwareIds")
    fun getDeviceId(activity: Activity): String? {
        val androidId = Settings.Secure.getString(
            activity.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return md5(androidId).toUpperCase(Locale.getDefault())
    }

    private fun md5(s: String): String {
        try {
            // Create MD5 Hash
            val digest = MessageDigest
                .getInstance("MD5")
            digest.update(s.toByteArray())
            val messageDigest = digest.digest()

            // Create Hex String
            val hexString = StringBuffer()
            for (i in messageDigest.indices) {
                var h = Integer.toHexString(0xFF and messageDigest[i].toInt())
                while (h.length < 2) h = "0$h"
                hexString.append(h)
            }
            return hexString.toString()
        } catch (e: NoSuchAlgorithmException) {
        }
        return ""
    }

    private fun showTestIdAlert(context: Context, typeAds: Int, id: String?) {
        var content: String? = ""
        when (typeAds) {
            BANNER_ADS -> content = "Banner Ads: "
            INTERS_ADS -> content = "Interstitial Ads: "
            REWARD_ADS -> content = "Rewarded Ads: "
            NATIVE_ADS -> content = "Native Ads: "
        }
        content += id
        val notification = NotificationCompat.Builder(context, "warning_ads")
            .setContentTitle("Found test ad id")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_warning)
            .build()
        val notificationManager = NotificationManagerCompat.from(context)
        notification.flags = notification.flags or Notification.FLAG_AUTO_CANCEL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "warning_ads",
                "Warning Ads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
        notificationManager.notify(typeAds, notification)
    }

}