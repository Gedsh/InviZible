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

package pan.alexander.tordnscrypt.dialogs

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
import android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
import android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys
import javax.inject.Inject

class RequestIgnoreDataRestrictionDialog : ExtendedDialogFragment() {

    @Inject
    lateinit var preferenceRepository: dagger.Lazy<PreferenceRepository>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        App.instance.daggerComponent.inject(this)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun assignBuilder(): AlertDialog.Builder? {

        val activity = activity
        if (activity == null || activity.isFinishing) {
            return null
        }

        val builder = AlertDialog.Builder(activity)

        builder.setTitle(R.string.notification_exclude_data_restriction_title)
        builder.setMessage(R.string.notification_exclude_data_restriction_message)

        builder.setPositiveButton(R.string.ok) { _, _ ->
            context?.let {
                Intent(
                    Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                    Uri.parse("package:${it.packageName}")
                ).apply {
                    try {
                        it.startActivity(this)
                    } catch (e: Exception) {
                        loge("RequestIgnoreDataRestrictionDialog", e)
                    }
                }
            }
        }

        builder.setNeutralButton(R.string.dont_show) { _, _ ->
            preferenceRepository.get().setBoolPreference(
                PreferenceKeys.DO_NOT_SHOW_REQUEST_DATA_RESTRICTION_DIALOG, true
            )
        }

        builder.setNegativeButton(R.string.ask_later) { dialog, _ ->
            dialog.cancel()
        }

        return builder
    }

    companion object {
        @JvmStatic
        fun getInstance(
            context: Context,
            preferenceRepository: PreferenceRepository
        ): DialogFragment? {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && (!preferenceRepository.getBoolPreference(PreferenceKeys.DO_NOT_SHOW_REQUEST_DATA_RESTRICTION_DIALOG)
                        || preferences.getBoolean(PreferenceKeys.ALWAYS_SHOW_HELP_MESSAGES, false))
            ) {
                (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).apply {
                    when (restrictBackgroundStatus) {
                        RESTRICT_BACKGROUND_STATUS_ENABLED -> {
                            return RequestIgnoreDataRestrictionDialog()
                        }

                        RESTRICT_BACKGROUND_STATUS_WHITELISTED -> {
                            return null
                        }

                        RESTRICT_BACKGROUND_STATUS_DISABLED -> {
                            return null
                        }
                    }
                }
            }

            return null
        }

        const val TAG = "RequestIgnoreDataRestrictionDialog"
    }
}
