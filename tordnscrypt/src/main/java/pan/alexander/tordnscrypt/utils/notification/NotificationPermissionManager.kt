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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.utils.notification

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject

class NotificationPermissionManager @Inject constructor() {

    var onPermissionResultListener: OnPermissionResultListener? = null

    @RequiresApi(33)
    fun requestNotificationPermission(activity: FragmentActivity) {
        when {
            ContextCompat.checkSelfPermission(
                activity,
                POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED -> {
                onPermissionResultListener?.onAllowed()
            }
            shouldShowRequestPermissionRationale(
                activity,
                POST_NOTIFICATIONS
            ) -> {
                onPermissionResultListener?.onShowRationale()
            }
            else -> {
                onPermissionResultListener?.onShowRationale()
            }
        }
    }

    fun launchNotificationPermissionSystemDialog(launcher: ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT >= 33) {
            launcher.launch(POST_NOTIFICATIONS)
        }
    }

    fun getNotificationPermissionLauncher(activity: FragmentActivity) =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                onPermissionResultListener?.onAllowed()
            }
        }

    interface OnPermissionResultListener {
        fun onAllowed()
        fun onShowRationale()
        fun onDenied()
    }
}
