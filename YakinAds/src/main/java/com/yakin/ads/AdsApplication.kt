package com.yakin.ads

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import com.akexorcist.localizationactivity.ui.LocalizationApplication

abstract class AdsApplication : LocalizationApplication() {

    override fun onCreate() {
        super.onCreate()
        Adsmod.getInstance()?.init(this, getListTestDeviceId())
        if (enableAdsResume()) {
            AppOpenManager.instance?.init(this, getOpenAppAdId())
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    abstract fun enableAdsResume(): Boolean

    abstract fun getListTestDeviceId(): List<String?>?

    abstract fun getOpenAppAdId(): String?
}