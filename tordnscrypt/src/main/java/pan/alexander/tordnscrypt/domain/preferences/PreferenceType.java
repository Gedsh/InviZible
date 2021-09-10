package pan.alexander.tordnscrypt.domain.preferences;
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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import androidx.annotation.IntDef;

@IntDef({
        PreferenceType.BOOL_PREFERENCE,
        PreferenceType.INT_PREFERENCE,
        PreferenceType.FLOAT_PREFERENCE,
        PreferenceType.STRING_PREFERENCE,
        PreferenceType.STRING_SET_PREFERENCE
})

public @interface PreferenceType {
    int BOOL_PREFERENCE = 1;
    int INT_PREFERENCE = 2;
    int FLOAT_PREFERENCE = 3;
    int STRING_PREFERENCE = 4;
    int STRING_SET_PREFERENCE = 5;
}
