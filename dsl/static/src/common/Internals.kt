/*
 * Copyright 2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.anko.internals

import android.app.Activity
import android.app.Service
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import org.jetbrains.anko.*
import java.io.Serializable
import java.util.*

object AnkoInternals {
    @Retention(AnnotationRetention.SOURCE)
    annotation class NoBinding

    fun <T : View> addView(manager: ViewManager, view: T) {
        return when (manager) {
            is ViewGroup -> manager.addView(view)
            is AnkoContext -> manager.addView(view, null)
            is UiHelper -> manager.addView(view, null)
            else -> throw AnkoException("$manager is the wrong parent")
        }
    }

    fun <T : View> addView(ctx: Context, view: T) {
        ctx.UI { AnkoInternals.addView(this, view) }
    }

    fun <T : View> addView(activity: Activity, view: T) {
        activity.UI { AnkoInternals.addView(this, view) }
    }

    fun getContext(manager: ViewManager): Context = when (manager) {
        is ViewGroup -> manager.context
        is AnkoContext -> manager.ctx
        is UiHelper -> manager.ctx
        else -> throw AnkoException("$manager is the wrong parent")
    }

    // Some constants not present in Android SDK v.15
    private object InternalConfiguration {
        val SCREENLAYOUT_LAYOUTDIR_MASK = 0xC0
        val SCREENLAYOUT_LAYOUTDIR_SHIFT = 6
        val SCREENLAYOUT_LAYOUTDIR_RTL = 0x02 shl SCREENLAYOUT_LAYOUTDIR_SHIFT

        val UI_MODE_TYPE_APPLIANCE = 0x05
        val UI_MODE_TYPE_WATCH = 0x06

        val DENSITY_DPI_NONE = 0xffff
    }

    @JvmStatic
    fun <T> createIntent(ctx: Context, clazz: Class<out T>, params: Array<out Pair<String, Any>>): Intent {
        val intent = Intent(ctx, clazz)
        if (params.isNotEmpty()) fillIntentArguments(intent, params)
        return intent
    }

    @JvmStatic
    fun internalStartActivity(
            ctx: Context,
            activity: Class<out Activity>,
            params: Array<out Pair<String, Any>>
    ) {
        ctx.startActivity(createIntent(ctx, activity, params))
    }

    @JvmStatic
    fun internalStartActivityForResult(
            act: Activity,
            activity: Class<out Activity>,
            requestCode: Int,
            params: Array<out Pair<String, Any>>
    ) {
        act.startActivityForResult(createIntent(act, activity, params), requestCode)
    }

    @JvmStatic
    fun internalStartService(
            ctx: Context,
            activity: Class<out Service>,
            params: Array<out Pair<String, Any>>
    ) {
        ctx.startService(createIntent(ctx, activity, params))
    }

    @JvmStatic
    private fun fillIntentArguments(intent: Intent, params: Array<out Pair<String, Any>>) {
        params.forEach {
            val value = it.second
            when (value) {
                is Int -> intent.putExtra(it.first, value)
                is Long -> intent.putExtra(it.first, value)
                is CharSequence -> intent.putExtra(it.first, value)
                is String -> intent.putExtra(it.first, value)
                is Float -> intent.putExtra(it.first, value)
                is Double -> intent.putExtra(it.first, value)
                is Char -> intent.putExtra(it.first, value)
                is Short -> intent.putExtra(it.first, value)
                is Boolean -> intent.putExtra(it.first, value)
                is Serializable -> intent.putExtra(it.first, value)
                is Bundle -> intent.putExtra(it.first, value)
                is Parcelable -> intent.putExtra(it.first, value)
                is Array<*> -> when {
                    value.isArrayOf<CharSequence>() -> intent.putExtra(it.first, value)
                    value.isArrayOf<String>() -> intent.putExtra(it.first, value)
                    value.isArrayOf<Parcelable>() -> intent.putExtra(it.first, value)
                    else -> throw AnkoException("Intent extra ${it.first} has wrong type ${value.javaClass.name}")
                }
                is IntArray -> intent.putExtra(it.first, value)
                is LongArray -> intent.putExtra(it.first, value)
                is FloatArray -> intent.putExtra(it.first, value)
                is DoubleArray -> intent.putExtra(it.first, value)
                is CharArray -> intent.putExtra(it.first, value)
                is ShortArray -> intent.putExtra(it.first, value)
                is BooleanArray -> intent.putExtra(it.first, value)
                else -> throw AnkoException("Intent extra ${it.first} has wrong type ${value.javaClass.name}")
            }
        }
    }

    // SQLiteDatabase is not closeable in older versions of Android
    @JvmStatic
    inline fun <T> useDatabase(db: SQLiteDatabase, f: (SQLiteDatabase) -> T) : T {
        try {
            return f(db)
        } finally {
            try {
                db.close()
            } catch (e: Exception) {
                // Do nothing
            }
        }
    }

    // Cursor is not closeable in older versions of Android
    @JvmStatic
    inline fun <T> useCursor(cursor: Cursor, f: (Cursor) -> T) : T {
        try {
            return f(cursor)
        } finally {
            try {
                cursor.close()
            } catch (e: Exception) {
                // Do nothing
            }
        }
    }

    @JvmStatic
    fun <T : View> initiateView(ctx: Context, viewClass: Class<T>): T {
        fun getConstructor1() = viewClass.getConstructor(Context::class.java)
        fun getConstructor2() = viewClass.getConstructor(Context::class.java, AttributeSet::class.java)

        try {
            return getConstructor1().newInstance(ctx)
        } catch (e: NoSuchMethodException) {
            try {
                return getConstructor2().newInstance(ctx, null)
            }
            catch (e: NoSuchMethodException) {
                throw AnkoException("Can't initiate View of class ${viewClass.name}: can't find proper constructor")
            }
        }

    }

    @JvmStatic
    fun testConfiguration(
            ctx: Context,
            screenSize: ScreenSize?,
            density: ClosedRange<Int>?,
            language: String?,
            orientation: Orientation?,
            long: Boolean?,
            fromSdk: Int?,
            sdk: Int?,
            uiMode: UiMode?,
            nightMode: Boolean?,
            rightToLeft: Boolean?,
            smallestWidth: Int?
    ): Boolean {
        val config = ctx.resources?.configuration

        if (screenSize != null) {
            if (config == null) return false
            val currentScreenSize = config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
            when (currentScreenSize) {
                Configuration.SCREENLAYOUT_SIZE_UNDEFINED -> {}
                Configuration.SCREENLAYOUT_SIZE_SMALL -> if (screenSize != ScreenSize.SMALL) return false
                Configuration.SCREENLAYOUT_SIZE_NORMAL -> if (screenSize != ScreenSize.NORMAL) return false
                Configuration.SCREENLAYOUT_SIZE_LARGE -> if (screenSize != ScreenSize.LARGE) return false
                Configuration.SCREENLAYOUT_SIZE_XLARGE -> if (screenSize != ScreenSize.XLARGE) return false
            }
        }

        if (density != null) {
            val currentDensityDpi = ctx.resources?.displayMetrics?.densityDpi ?: return false
            if (currentDensityDpi !in density || currentDensityDpi == density.endInclusive) return false
        }

        if (language != null) {
            val locale = Locale.getDefault()
            val currentLanguage = if (language.indexOf('_') >= 0) locale.toString() else locale.language
            if (currentLanguage != language) return false
        }

        if (orientation != null) {
            if (config == null) return false
            when (config.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> if (orientation != Orientation.LANDSCAPE) return false
                Configuration.ORIENTATION_PORTRAIT -> if (orientation != Orientation.PORTRAIT) return false
                Configuration.ORIENTATION_SQUARE -> if (orientation != Orientation.SQUARE) return false
            }
        }

        if (long != null) {
            if (config == null) return false
            val currentLong = config.screenLayout and Configuration.SCREENLAYOUT_LONG_MASK
            if (currentLong == Configuration.SCREENLAYOUT_LONG_YES && !long) return false
            if (currentLong == Configuration.SCREENLAYOUT_LONG_NO && long) return false
        }

        if (fromSdk != null) {
            if (Build.VERSION.SDK_INT < fromSdk) return false
        }

        if (sdk != null) {
            if (Build.VERSION.SDK_INT != sdk) return false
        }

        if (uiMode != null) {
            if (config == null) return false
            when (config.uiMode and Configuration.UI_MODE_TYPE_MASK) {
                Configuration.UI_MODE_TYPE_NORMAL -> if (uiMode != UiMode.NORMAL) return false
                Configuration.UI_MODE_TYPE_DESK -> if (uiMode != UiMode.DESK) return false
                Configuration.UI_MODE_TYPE_CAR -> if (uiMode != UiMode.CAR) return false
                Configuration.UI_MODE_TYPE_TELEVISION -> if (uiMode != UiMode.TELEVISION) return false
                InternalConfiguration.UI_MODE_TYPE_APPLIANCE -> if (uiMode != UiMode.APPLIANCE) return false
                InternalConfiguration.UI_MODE_TYPE_WATCH -> if (uiMode != UiMode.WATCH) return false
            }
        }

        if (nightMode != null) {
            val uiModeManager = ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager ?: return false

            val currentMode = uiModeManager.nightMode
            if (currentMode == UiModeManager.MODE_NIGHT_YES && !nightMode) return false
            if (currentMode == UiModeManager.MODE_NIGHT_NO && nightMode) return false
        }

        if (rightToLeft != null) {
            if (config == null) return false
            val rtlMode = (config.screenLayout and
                    InternalConfiguration.SCREENLAYOUT_LAYOUTDIR_MASK) == InternalConfiguration.SCREENLAYOUT_LAYOUTDIR_RTL
            if (rtlMode != rightToLeft) return false
        }

        if (smallestWidth != null) {
            if (config == null) return false

            if (config.smallestScreenWidthDp == Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED) {
                if (smallestWidth != Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED) return false
            }
            else if (config.smallestScreenWidthDp < smallestWidth) return false
        }

        return true
    }

}