/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.utils

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.view.isGone

object EdgeToEdgeUtils {

    const val STATUS_BAR_TAG = "status_bar"
    const val NAVIGATION_BAR_TAG = "navigation_bar"

    fun Activity.applyEdgeToEdgeInsets(statusBarColor: Int, navigationBarColor: Int) {
        val view = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
            )

            val statusBarHeight = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars()
            ).top
            if (statusBarHeight > 0) {
                applyStatusBarColor(window, statusBarColor, true, statusBarHeight)
            }
            WindowCompat.getInsetsController(
                window,
                window.decorView
            ).isAppearanceLightStatusBars = false

            val navigationBarHeight = windowInsets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            ).bottom
            if (navigationBarHeight > 0) {
                applyNavigationBarColor(window, navigationBarColor, true, navigationBarHeight)
            }
            WindowCompat.getInsetsController(
                window,
                window.decorView
            ).isAppearanceLightNavigationBars = !ThemeUtils.isNightMode(v.context)

            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
            )

            windowInsets
        }
    }

    fun applyStatusBarColor(
        window: Window,
        statusBarColor: Int,
        isDecor: Boolean,
        height: Int
    ): View {
        val parent = if (isDecor) {
            window.decorView as ViewGroup
        } else {
            window.findViewById<View>(android.R.id.content) as ViewGroup
        }
        var fakeStatusBarView = parent.findViewWithTag<View>(STATUS_BAR_TAG)
        if (fakeStatusBarView != null) {
            if (fakeStatusBarView.isGone) {
                fakeStatusBarView.visibility = View.VISIBLE
            }
            fakeStatusBarView.setBackgroundColor(statusBarColor)
        } else {
            fakeStatusBarView = createStatusBarView(window.context, statusBarColor, height)
            parent.addView(fakeStatusBarView)
        }
        return fakeStatusBarView
    }

    private fun createStatusBarView(context: Context, statusBarColor: Int, height: Int): View {
        val statusBarView = View(context)
        statusBarView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, height
        )
        statusBarView.setBackgroundColor(statusBarColor)
        statusBarView.tag = STATUS_BAR_TAG
        return statusBarView
    }

    fun applyNavigationBarColor(
        window: Window,
        navigationBarColor: Int,
        isDecor: Boolean,
        height: Int
    ): View {
        val parent = if (isDecor) {
            window.decorView as ViewGroup
        } else {
            window.findViewById<View>(android.R.id.content) as ViewGroup
        }
        var fakeNavigationBarView = parent.findViewWithTag<View>(NAVIGATION_BAR_TAG)
        if (fakeNavigationBarView != null) {
            if (fakeNavigationBarView.isGone) {
                fakeNavigationBarView.visibility = View.VISIBLE
            }
            fakeNavigationBarView.setBackgroundColor(navigationBarColor)
        } else {
            fakeNavigationBarView =
                createNavigationBarView(window.context, navigationBarColor, height)
            parent.addView(fakeNavigationBarView)
        }
        return fakeNavigationBarView
    }

    private fun createNavigationBarView(
        context: Context,
        navigationBarColor: Int,
        height: Int
    ): View {
        val frameLayout = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }
        val navigationBarView = View(context)
        navigationBarView.setBackgroundColor(navigationBarColor)
        val navigationBarLayoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            height
        )
        navigationBarView.tag = NAVIGATION_BAR_TAG
        frameLayout.addView(navigationBarView, navigationBarLayoutParams)
        return frameLayout
    }
}
