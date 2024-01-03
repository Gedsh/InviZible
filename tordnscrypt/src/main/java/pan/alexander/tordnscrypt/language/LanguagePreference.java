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

package pan.alexander.tordnscrypt.language;

import android.content.Context;
import android.os.Build;
import androidx.preference.ListPreference;
import android.util.AttributeSet;

public class LanguagePreference extends ListPreference {
    public LanguagePreference(Context context) {
        super(context);
        init();
    }

    public LanguagePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // use the list of human-readable language names for the displayed list
        setEntries(LanguageList.getHumanReadable());
        // use the list of machine-readable language names for the saved values
        setEntryValues(LanguageList.getMachineReadable());
        // use an empty language code (no custom language) as the default
        setDefaultValue("");
        // set the summary to be auto-updated to the selected value
        setSummary("%s");
    }

    @Override
    public void setValue(String value) {
        // if the API level is 19 or above
        if (Build.VERSION.SDK_INT >= 19) {
            // we can just call the default implementation
            super.setValue(value);
        }
        // if the API level is below 19
        else {
            // get the old value first
            String oldValue = getValue();
            // call the default implementation
            super.setValue(value);
            // if the new and old value differ
            if (!value.equals(oldValue)) {
                // notify the super class of the change
                notifyChanged();
            }
        }
    }
}
