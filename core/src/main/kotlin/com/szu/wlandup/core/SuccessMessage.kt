package com.szu.wlandup.core

object SuccessMessage {
    const val PREFIX = "我在深大联网仅用"
    const val SUFFIX = "次就联网成功，你也快来试试吧"

    fun format(attempts: Int): String = "$PREFIX$attempts$SUFFIX"

    /** Exact sentence required by the product copy. */
    fun assertExact(attempts: Int, actual: String) {
        require(actual == format(attempts)) {
            "expected exact success copy, got: $actual"
        }
    }
}
