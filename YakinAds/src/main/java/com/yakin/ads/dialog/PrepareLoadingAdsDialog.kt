package com.yakin.ads.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import com.yakin.ads.R

class PrepareLoadingAdsDialog(context: Context) : Dialog(
    context, R.style.AppTheme
) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_prepair_loading_ads)
    }

    fun hideLoadingAdsText() {
        findViewById<View>(R.id.loading_dialog_tv)?.visibility = View.INVISIBLE
    }
}