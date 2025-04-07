/*
* Modifications copyright (C) 2025 Wakala Srl
*/

package com.android.mdl.appreader.util

import android.util.Log
import com.android.mdl.appreader.VerifierApp

fun Any.logDebug(message: String, exception: Throwable? = null) {
    if (!VerifierApp.isDebugLogEnabled()) return
    val tag = tagValue()
    val finalMessage = "appverifier: $message"
    if (exception == null) {
        Log.d(tag, finalMessage)
    } else {
        Log.d(tag, finalMessage, exception)
    }
}

fun Any.logError(message: String, exception: Throwable? = null) {
    if (!VerifierApp.isDebugLogEnabled()) return
    val tag = tagValue()
    val finalMessage = "appverifier: $message"
    if (exception == null) {
        Log.e(tag, finalMessage)
    } else {
        Log.e(tag, finalMessage, exception)
    }
}

private fun Any.tagValue(): String {
    if (this is String) return this
    val fullClassName: String = this::class.qualifiedName ?: this::class.java.typeName
    val outerClassName = fullClassName.substringBefore('$')
    val simplerOuterClassName = outerClassName.substringAfterLast('.')
    return if (simplerOuterClassName.isEmpty()) {
        fullClassName
    } else {
        simplerOuterClassName.removeSuffix("Kt")
    }
}
