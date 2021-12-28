package com.yakin.ads.utils

import android.content.Context
import com.google.android.gms.ads.AdValue
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

object FirebaseAnalyticsUtil {
    fun logPaidAdImpression(
        context: Context?,
        adValue: AdValue?,
        adUnitId: String?,
        mediationAdapterClassName: String?
    ) {
        Log.d(
            "FirebaseAnalyticsUtil", String.format(
                "Paid event of value %d microcents in currency %s of precision %s%n occurred for ad unit %s from ad network %s.",
                adValue?.valueMicros,
                adValue?.currencyCode,
                adValue?.precisionType,
                adUnitId,
                mediationAdapterClassName
            )
        )
        val params = Bundle() // Log ad value in micros.
        params.putLong("valuemicros", adValue?.valueMicros ?: -1)
        // These values below won’t be used in ROAS recipe.
        // But log for purposes of debugging and future reference.
        params.putString("currency", adValue?.currencyCode)
        params.putInt("precision", adValue?.precisionType ?: -1)
        params.putString("adunitid", adUnitId)
        params.putString("network", mediationAdapterClassName)
        context?.let { FirebaseAnalytics.getInstance(it).logEvent("paid_ad_impression", params) }
    }
}