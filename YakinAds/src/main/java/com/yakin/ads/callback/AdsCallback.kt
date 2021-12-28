package com.yakin.ads.callback

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.nativead.NativeAd

open class AdsCallback {
    open fun onAdClosed() {}

    open fun onAdFailedToLoad(i: LoadAdError?) {}

    open fun onAdFailedToShow(adError: AdError?) {}

    open fun onAdLeftApplication() {}

    open fun onAdLoaded() {}

    open fun onInterstitialLoad(interstitialAd: InterstitialAd?) {}

    open fun onAdClicked() {}

    open fun onAdImpression() {}

    open fun onUnifiedNativeAdLoaded(unifiedNativeAd: NativeAd?) {}

    open fun onLoadAdsTimeout() {}
}