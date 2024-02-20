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

package pan.alexander.tordnscrypt.modules

import android.content.Context
import android.content.Intent
import android.os.Build
import pan.alexander.tordnscrypt.utils.Utils.isShowNotification
import pan.alexander.tordnscrypt.utils.app
import pan.alexander.tordnscrypt.utils.logger.Logger.loge

object ModulesActionSender {
    fun sendIntent(context: Context, action: String) = try {

        val intent = Intent(context, ModulesService::class.java)
        intent.action = action

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra("showNotification", true)

            if (context.app.isAppForeground) {
                try {
                    context.startService(intent)
                } catch (e: Exception) {
                    loge("ModulesActionSender sendIntent with action $action", e)
                    context.startForegroundService(intent)
                }
            } else {
                context.startForegroundService(intent)
            }
        } else {
            intent.putExtra("showNotification", isShowNotification(context))
            context.startService(intent)
        }
    } catch (e: Exception) {
        loge("ModulesActionSender sendIntent", e, true)
    }
}
