package com.yakin.ads

import androidx.multidex.MultiDexApplication

abstract class AdsMultiDexApplication : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        Adsmod.getInstance()?.init(this, getListTestDeviceId())
        if (enableAdsResume()) {
            AppOpenManager.instance?.init(this, getOpenAppAdId())
        }
    }

    abstract fun enableAdsResume(): Boolean

    abstract fun getListTestDeviceId(): List<String?>?

    abstract fun getOpenAppAdId(): String?
}