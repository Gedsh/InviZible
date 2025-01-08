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

package pan.alexander.tordnscrypt.di.logreader

import android.content.Context
import dagger.BindsInstance
import dagger.Subcomponent
import pan.alexander.tordnscrypt.dnscrypt_fragment.DNSCryptFragmentPresenter
import pan.alexander.tordnscrypt.itpd_fragment.ITPDFragmentPresenter
import pan.alexander.tordnscrypt.modules.ModulesStateLoop
import pan.alexander.tordnscrypt.settings.PreferencesFastFragment
import pan.alexander.tordnscrypt.tor_fragment.TorFragmentPresenter
import javax.inject.Named

@LogReaderScope
@Subcomponent(modules = [LogReaderRepositoryModule::class, LogReaderInteractorsModule::class])
interface LogReaderSubcomponent {

    @Subcomponent.Factory
    interface Factory {
        fun create(@BindsInstance @Named(LOG_READER_CONTEXT) context: Context): LogReaderSubcomponent
    }

    fun inject(presenter: DNSCryptFragmentPresenter)
    fun inject(presenter: TorFragmentPresenter)
    fun inject(presenter: ITPDFragmentPresenter)
    fun inject(modulesStateLoop: ModulesStateLoop)
    fun inject(fragment: PreferencesFastFragment)

    companion object {
        const val LOG_READER_CONTEXT = "LogReaderContext"
    }
}
