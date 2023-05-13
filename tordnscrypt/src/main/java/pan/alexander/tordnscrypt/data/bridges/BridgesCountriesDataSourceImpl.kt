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

    Copyright 2019-2023 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.data.bridges

import pan.alexander.tordnscrypt.settings.PathVars
import java.io.File
import javax.inject.Inject

class BridgesCountriesDataSourceImpl @Inject constructor(
    private val pathVars: PathVars
) : BridgesCountriesDataSource {
    override fun getGeoipFile(): File = File(pathVars.torGeoipPath)
    override fun getGeoip6File(): File = File(pathVars.torGeoip6Path)
}
