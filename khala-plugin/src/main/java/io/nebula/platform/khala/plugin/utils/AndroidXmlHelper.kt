package io.nebula.platform.khala.plugin.utils

import org.dom4j.QName

/**
 * @author panxinghai
 *
 * date : 2019-07-18 15:35
 */
object AndroidXmlHelper {

    const val ANDROID_URL = "http://schemas.android.com/apk/res/android"
    const val APP_URL = "http://schemas.android.com/apk/res-auto"
    const val ACTION_MAIN = "android.intent.action.MAIN"
    const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"

    const val TAG_APPLICATION = "application"
    const val TAG_ACTIVITY = "activity"
    const val TAG_ACTION = "action"
    const val TAG_CATEGORY = "category"

    fun getAndroidQName(name: String): QName {
        return QName.get(name, ANDROID_URL)
    }
}