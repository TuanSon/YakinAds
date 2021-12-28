package com.yakin.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.annotation.IntDef
import com.android.billingclient.api.*
import com.yakin.ads.callback.BillingListener
import com.yakin.ads.callback.PurchaseListener
import java.lang.Exception
import java.text.NumberFormat
import java.util.*

class AppPurchase private constructor() {
    @SuppressLint("StaticFieldLeak")
    var price = "1.49$"
        get() = getPrice(productId)
    private var oldPrice = "2.99$"
    private var productId: String? = null
    private var listSubscriptionId: MutableList<String>? = null
    private var listINAPId: MutableList<String>? = null
    private var purchaseListener: PurchaseListener? = null
    private var billingListener: BillingListener? = null
    var initBillingFinish = false
        private set
    private var billingClient: BillingClient? = null
    private var skuListINAPFromStore: List<SkuDetails>? = null
    private var skuListSubsFromStore: List<SkuDetails>? = null
    private val skuDetailsINAPMap: MutableMap<String?, SkuDetails> = HashMap<String?, SkuDetails>()
    private val skuDetailsSubsMap: MutableMap<String, SkuDetails> = HashMap<String, SkuDetails>()
    var isAvailable = false
        private set
    private var isListGot = false
    private var isConsumePurchase = false

    //tracking purchase adjust
    private var idPurchaseCurrent = ""
    private var typeIap = 0

    fun setPurchaseListener(purchaseListener: PurchaseListener?) {
        this.purchaseListener = this.purchaseListener
    }

    /**
     * listener init billing app
     *
     * @param billingListener
     */
    fun setBillingListener(billingListener: BillingListener) {
        this.billingListener = billingListener
        if (isAvailable) {
            billingListener.onInitBillingListener(0)
            initBillingFinish = true
        }
    }

    /**
     * listener init billing app with timeout
     *
     * @param billingListener
     * @param timeout
     */
    fun setBillingListener(billingListener: BillingListener, timeout: Int) {
        this.billingListener = billingListener
        if (isAvailable) {
            billingListener.onInitBillingListener(0)
            initBillingFinish = true
            return
        }
        Handler().postDelayed(object : Runnable {
            override fun run() {
                if (!initBillingFinish) {
                    Log.e(TAG, "setBillingListener: timeout ")
                    initBillingFinish = true
                    billingListener.onInitBillingListener(BillingClient.BillingResponseCode.ERROR)
                }
            }
        }, timeout.toLong())
    }

    fun setConsumePurchase(consumePurchase: Boolean) {
        isConsumePurchase = consumePurchase
    }

    fun setOldPrice(oldPrice: String) {
        this.oldPrice = oldPrice
    }

    var purchaseUpdatedListener: PurchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, list ->
            Log.e(TAG, "onPurchasesUpdated code: " + billingResult.responseCode)
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && list != null) {
                for (purchase in list) {
                    val sku: List<String> = purchase.skus
                    handlePurchase(purchase)
                }
            } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                if (purchaseListener != null) purchaseListener?.onUserCancelBilling()
                Log.d(TAG, "onPurchasesUpdated:USER_CANCELED ")
            } else {
                Log.d(TAG, "onPurchasesUpdated:... ")
            }
        }

    var purchaseClientStateListener: BillingClientStateListener =
        object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                isAvailable = false
            }

            override fun onBillingSetupFinished(billingResult: BillingResult) {
                Log.d(TAG, "onBillingSetupFinished:  " + billingResult.responseCode)
                if (billingListener != null && !initBillingFinish) billingListener?.onInitBillingListener(
                    billingResult.responseCode
                )

                initBillingFinish = true
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    isAvailable = true
                    val params: SkuDetailsParams.Builder = SkuDetailsParams.newBuilder()
                    listINAPId?.let { params.setSkusList(it).setType(BillingClient.SkuType.INAPP) }
                    billingClient?.querySkuDetailsAsync(
                        params.build(),
                        object : SkuDetailsResponseListener {
                            override fun onSkuDetailsResponse(
                                billingResult: BillingResult,
                                list: List<SkuDetails>?
                            ) {
                                if (list != null) {
                                    Log.d(TAG, "onSkuINAPDetailsResponse: " + list.size)
                                    skuListINAPFromStore = list
                                    isListGot = true
                                    addSkuINAPToMap(list)
                                }
                            }
                        })
                    listSubscriptionId?.let {
                        params.setSkusList(it).setType(BillingClient.SkuType.SUBS)
                    }
                    billingClient?.querySkuDetailsAsync(
                        params.build()
                    ) { billing, list ->
                        if (list != null) {
                            Log.d(TAG, "onSkuSubsDetailsResponse: " + list.size)
                            skuListSubsFromStore = list
                            isListGot = true
                            addSkuSubsToMap(list)
                        }
                    }
                } else if (
                    billingResult.responseCode == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ||
                    billingResult.responseCode == BillingClient.BillingResponseCode.ERROR
                ) {
                    Log.e(TAG, "onBillingSetupFinished:ERROR ")
                }
            }
        }

    fun setProductId(productId: String?) {
        this.productId = productId
    }

    fun addSubcriptionId(id: String) {
        if (listSubscriptionId == null) listSubscriptionId = ArrayList()
        listSubscriptionId!!.add(id)
    }

    fun addProductId(id: String) {
        if (listINAPId == null) listINAPId = ArrayList()
        listINAPId!!.add(id)
    }

    fun initBilling(application: Application?) {
        listSubscriptionId = ArrayList()
        listINAPId = ArrayList()
        billingClient = application?.let {
            BillingClient.newBuilder(it)
                .setListener(purchaseUpdatedListener)
                .enablePendingPurchases()
                .build()
        }
        billingClient?.startConnection(purchaseClientStateListener)
    }

    fun initBilling(
        application: Application?,
        listINAPId: MutableList<String>?,
        listSubsId: MutableList<String>?
    ) {
        listSubscriptionId = listSubsId
        this.listINAPId = listINAPId
        billingClient = application?.let {
            BillingClient.newBuilder(it)
                .setListener(purchaseUpdatedListener)
                .enablePendingPurchases()
                .build()
        }
        billingClient?.startConnection(purchaseClientStateListener)
    }

    private fun addSkuSubsToMap(skuList: List<SkuDetails>) {
        for (skuDetails in skuList) {
            skuDetailsSubsMap[skuDetails.sku] = skuDetails
        }
    }

    private fun addSkuINAPToMap(skuList: List<SkuDetails>) {
        for (skuDetails in skuList) {
            skuDetailsINAPMap[skuDetails.sku] = skuDetails
        }
    }

    //check all id INAP + Subs
    fun isPurchased(context: Context?): Boolean {
        if (listINAPId != null) {
            val result: Purchase.PurchasesResult? =
                billingClient?.queryPurchases(BillingClient.SkuType.INAPP)
            if (result?.responseCode == BillingClient.BillingResponseCode.OK && result.purchasesList != null) {
                for (purchase in result.purchasesList!!) {
                    for (id in listINAPId!!) {
                        if (purchase.skus.contains(id)) {
                            return true
                        }
                    }
                }
            }
        }
        if (listSubscriptionId != null) {
            val result: Purchase.PurchasesResult? =
                billingClient?.queryPurchases(BillingClient.SkuType.SUBS)
            if (result?.responseCode == BillingClient.BillingResponseCode.OK && result.purchasesList != null) {
                for (purchase in result.purchasesList!!) {
                    for (id in listSubscriptionId!!) {
                        if (purchase.skus.contains(id)) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    //check  id INAP
    fun isPurchased(context: Context?, productId: String): Boolean {
        Log.d(TAG, "isPurchased: $productId")
        val resultINAP: Purchase.PurchasesResult? =
            billingClient?.queryPurchases(BillingClient.SkuType.INAPP)
        if (resultINAP?.responseCode == BillingClient.BillingResponseCode.OK && resultINAP.purchasesList != null) {
            for (purchase in resultINAP.purchasesList!!) {
                if (purchase.skus.contains(productId)) {
                    return true
                }
            }
        }
        val resultSubs: Purchase.PurchasesResult? =
            billingClient?.queryPurchases(BillingClient.SkuType.SUBS)
        if (resultSubs?.responseCode == BillingClient.BillingResponseCode.OK && resultSubs.purchasesList != null) {
            for (purchase in resultSubs.purchasesList!!) {
                if (purchase.orderId.equals(productId, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    fun purchase(activity: Activity?) {
        if (productId == null) {
            Log.e(TAG, "Purchase false:productId null")
            Toast.makeText(activity, "Product id must not be empty!", Toast.LENGTH_SHORT).show()
            return
        }
        purchase(activity, productId!!)
    }

    fun purchase(activity: Activity?, productId: String): String {
        if (skuListINAPFromStore == null) {
            purchaseListener?.displayErrorMessage("Billing error init")
            return ""
        }
        val skuDetails: SkuDetails = skuDetailsINAPMap[productId] ?: return "Product ID invalid"
        //        for (int i = 0; i < skuListINAPFromStore.size(); i++) {
//            if (skuListINAPFromStore.get(i).getSku().equalsIgnoreCase(productId)) {
//                skuDetails = skuListINAPFromStore.get(i);
//            }
//        }
        idPurchaseCurrent = productId
        typeIap = TYPE_IAP.PURCHASE
        val billingFlowParams: BillingFlowParams = BillingFlowParams.newBuilder()
            .setSkuDetails(skuDetails)
            .build()
        val responseCode: BillingResult? =
            activity?.let { billingClient?.launchBillingFlow(it, billingFlowParams) }
        when (responseCode?.responseCode) {
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                purchaseListener?.displayErrorMessage("Billing not supported for type of request")
                return "Billing not supported for type of request"
            }
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED, BillingClient.BillingResponseCode.DEVELOPER_ERROR -> return ""
            BillingClient.BillingResponseCode.ERROR -> {
                purchaseListener?.displayErrorMessage("Error completing request")
                return "Error completing request"
            }
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> return "Error processing request."
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> return "Selected item is already owned"
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> return "Item not available"
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> return "Play Store service is not connected now"
            BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> return "Timeout"
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                purchaseListener?.displayErrorMessage("Network error.")
                return "Network Connection down"
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                purchaseListener?.displayErrorMessage("Request Canceled")
                return "Request Canceled"
            }
            BillingClient.BillingResponseCode.OK -> return "Subscribed Successfully"
        }
        return ""
    }

    fun subscribe(activity: Activity?, SubsId: String): String {
        if (skuListSubsFromStore == null) {
            purchaseListener?.displayErrorMessage("Billing error init")
            return ""
        }
        val skuDetails: SkuDetails? = skuDetailsSubsMap[SubsId]
        idPurchaseCurrent = SubsId
        typeIap = TYPE_IAP.SUBSCRIPTION
        if (skuDetails == null) {
            return "SubsId invalid"
        }
        val billingFlowParams: BillingFlowParams = BillingFlowParams.newBuilder()
            .setSkuDetails(skuDetails)
            .build()
        val responseCode: BillingResult? =
            activity?.let { billingClient?.launchBillingFlow(it, billingFlowParams) }
        when (responseCode?.responseCode) {
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                purchaseListener?.displayErrorMessage("Billing not supported for type of request")
                return "Billing not supported for type of request"
            }
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED, BillingClient.BillingResponseCode.DEVELOPER_ERROR -> return ""
            BillingClient.BillingResponseCode.ERROR -> {
                purchaseListener?.displayErrorMessage("Error completing request")
                return "Error completing request"
            }
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> return "Error processing request."
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> return "Selected item is already owned"
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> return "Item not available"
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> return "Play Store service is not connected now"
            BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> return "Timeout"
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                purchaseListener?.displayErrorMessage("Network error.")
                return "Network Connection down"
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                purchaseListener?.displayErrorMessage("Request Canceled")
                return "Request Canceled"
            }
            BillingClient.BillingResponseCode.OK -> return "Subscribed Successfully"
        }
        return ""
    }

    fun consumePurchase() {
        if (productId == null) {
            Log.e(TAG, "Consume Purchase false:productId null ")
            return
        }
        consumePurchase(productId)
    }

    fun consumePurchase(productId: String?) {
        var pc: Purchase? = null
        val resultINAP: Purchase.PurchasesResult? =
            billingClient?.queryPurchases(BillingClient.SkuType.INAPP)
        if (resultINAP?.responseCode == BillingClient.BillingResponseCode.OK && resultINAP.purchasesList != null) {
            for (purchase in resultINAP.purchasesList!!) {
                if (purchase.skus.contains(productId)) {
                    pc = purchase
                }
            }
        }
        if (pc == null) return
        try {
            val consumeParams: ConsumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(pc.purchaseToken)
                .build()
            val listener: ConsumeResponseListener =
                ConsumeResponseListener { billingResult, _ ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.e(TAG, "onConsumeResponse: OK")
                    }
                }
            billingClient?.consumeAsync(consumeParams, listener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handlePurchase(purchase: Purchase) {

        //tracking adjust
        val price = getPriceWithoutCurrency(idPurchaseCurrent, typeIap)
        val currentcy = getCurrency(idPurchaseCurrent, typeIap)
        purchaseListener?.onProductPurchased(
            purchase.orderId,
            purchase.originalJson
        )
        if (isConsumePurchase) {
            val consumeParams: ConsumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val listener: ConsumeResponseListener = object : ConsumeResponseListener {
                override fun onConsumeResponse(
                    billingResult: BillingResult,
                    purchaseToken: String
                ) {
                    Log.d(TAG, "onConsumeResponse: " + billingResult.debugMessage)
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    }
                }
            }
            billingClient?.consumeAsync(consumeParams, listener)
        } else {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                val acknowledgePurchaseParams: AcknowledgePurchaseParams =
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                if (!purchase.isAcknowledged) {
                    billingClient?.acknowledgePurchase(
                        acknowledgePurchaseParams
                    ) { billingResult ->
                        Log.d(
                            TAG,
                            "onAcknowledgePurchaseResponse: " + billingResult.debugMessage
                        )
                    }
                }
            }
        }
    }

    fun getPrice(productId: String?): String {
        val skuDetails: SkuDetails = skuDetailsINAPMap[productId] ?: return ""
        Log.e(TAG, "getPrice: " + skuDetails.price)
        return skuDetails.price
    }

    fun getPriceSub(productId: String): String {
        val skuDetails: SkuDetails = skuDetailsSubsMap[productId] ?: return ""
        return skuDetails.price
    }

    fun getIntroductorySubPrice(productId: String): String {
        val skuDetails: SkuDetails = skuDetailsSubsMap[productId] ?: return ""
        return skuDetails.price
    }

    fun getCurrency(productId: String, typeIAP: Int): String {
        val skuDetails: SkuDetails =
            (if (typeIAP == TYPE_IAP.PURCHASE) skuDetailsINAPMap[productId] else skuDetailsSubsMap[productId])
                ?: return ""
        return skuDetails.priceCurrencyCode
    }

    fun getPriceWithoutCurrency(productId: String, typeIAP: Int): Long {
        val skuDetails: SkuDetails =
            (if (typeIAP == TYPE_IAP.PURCHASE) skuDetailsINAPMap[productId] else skuDetailsSubsMap[productId])
                ?: return 0
        return skuDetails.priceAmountMicros
    }

    private fun formatCurrency(price: Double, currency: String): String {
        val format = NumberFormat.getCurrencyInstance()
        format.maximumFractionDigits = 0
        format.currency = Currency.getInstance(currency)
        return format.format(price)
    }

    var discount = 1.0

    annotation class TYPE_IAP {
        companion object {
            var PURCHASE = 1
            var SUBSCRIPTION = 2
        }
    }

    companion object {
        private val LICENSE_KEY: String? = null
        private val MERCHANT_ID: String? = null
        private const val TAG = "PurchaseEG"

        //    public static final String PRODUCT_ID = "android.test.purchased";
        @SuppressLint("StaticFieldLeak")
        var instance: AppPurchase? = null
            get() {
                if (field == null) {
                    field = AppPurchase()
                }
                return field
            }
            private set
    }
}