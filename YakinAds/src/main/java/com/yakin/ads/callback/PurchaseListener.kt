package com.yakin.ads.callback

interface PurchaseListener {
    fun onProductPurchased(productId: String?, transactionDetails: String?)
    fun displayErrorMessage(errorMsg: String?)
    fun onUserCancelBilling()
}