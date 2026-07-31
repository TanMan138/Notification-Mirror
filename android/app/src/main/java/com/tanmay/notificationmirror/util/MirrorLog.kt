package com.tanmay.notificationmirror.util

import android.util.Log

private const val TAG = "NotificationMirror"

object MirrorLog {
    fun d(message: String) = Log.d(TAG, message)
    fun w(message: String) = Log.w(TAG, message)
    fun e(message: String, throwable: Throwable? = null) = Log.e(TAG, message, throwable)
}
