package com.yakin.ads.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.yakin.ads.Adsmod
import com.yakin.ads.R
import com.yakin.ads.callback.DialogExitListener
import com.yakin.ads.databinding.DialogExitAppBinding

class DialogExitApp(context: Context, private val nativeAds: NativeAd?, private val onExitListener: DialogExitListener) :
    Dialog(context) {
    lateinit var binding: DialogExitAppBinding
    lateinit var adView: NativeAdView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = DialogExitAppBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window?.setBackgroundDrawableResource(android.R.color.transparent);
        initView()
    }

    private fun initView() {
        adView = LayoutInflater.from(context).inflate(R.layout.native_exit1, null, false) as NativeAdView
        binding.nativeAdContainer.addView(adView)
        if (nativeAds != null) {
            Adsmod.getInstance()?.populateUnifiedNativeAdView(nativeAd = nativeAds, adView)
        }
        binding.btnOk.setOnClickListener {
            dismiss()
            onExitListener.onExit()
        }
        binding.btnCancel.setOnClickListener { dismiss() }
    }
}