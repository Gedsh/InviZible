package pan.alexander.tordnscrypt.language;
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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import androidx.preference.PreferenceManager;
import android.util.Log;

import java.util.Locale;

import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;

public class Language {
    private static final Locale mOriginalLocale;

    static {
        // save the original default locale so that we can reference it later
        mOriginalLocale = Locale.getDefault();
    }

    /**
     * Sets the language/locale for the current application and its process from the given preference
     *
     * @param context the Application instance that you call this from
     * @param languagePreferenceKey the key of the `LanguagePreference`, `ListPreference` or `EditTextPreference` that contains the desired language's code
     */
    public static void setFromPreference(final ContextWrapper context, final String languagePreferenceKey) {
        setFromPreference(context, languagePreferenceKey, false);
    }

    /**
     * Sets the language/locale for the current application and its process from the given preference
     *
     * @param context the Application instance that you call this from
     * @param languagePreferenceKey the key of the `LanguagePreference`, `ListPreference` or `EditTextPreference` that contains the desired language's code
     * @param forceUpdate whether to force an update when the default language (empty language code) is requested
     */
    public static void setFromPreference(final ContextWrapper context, final String languagePreferenceKey, final boolean forceUpdate) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        setFromPreference(context, languagePreferenceKey, forceUpdate, prefs);
    }

    /**
     * Sets the language/locale for the current application and its process from the given preference
     *
     * @param context the Application instance that you call this from
     * @param languagePreferenceKey the key of the `LanguagePreference`, `ListPreference` or `EditTextPreference` that contains the desired language's code
     * @param forceUpdate whether to force an update when the default language (empty language code) is requested
     * @param prefs a SharedPreferences instance that should be re-used
     */
    private static void setFromPreference(final ContextWrapper context, final String languagePreferenceKey, final boolean forceUpdate, final SharedPreferences prefs) {
        final String languageCode = prefs.getString(languagePreferenceKey, "");
        if (languageCode != null) {
            set(context, languageCode, forceUpdate);
        }
    }

    /**
     * Sets the language/locale for the current application and its process to the given language code
     *
     * @param context the `ContextWrapper` instance to get a `Resources` instance from
     * @param languageCode the language code in the form `[a-z]{2}` (e.g. `es`) or `[a-z]{2}-r?[A-Z]{2}` (e.g. `pt-rBR`)
     */
    public static void set(final ContextWrapper context, final String languageCode) {
        set(context, languageCode, false);
    }

    /**
     * Sets the language/locale for the current application and its process to the given language code
     *
     * @param context the `ContextWrapper` instance to get a `Resources` instance from
     * @param languageCode the language code in the form `[a-z]{2}` (e.g. `es`) or `[a-z]{2}-r?[A-Z]{2}` (e.g. `pt-rBR`)
     * @param forceUpdate whether to force an update when the default language (empty language code) is requested
     */
    public static void set(final ContextWrapper context, final String languageCode, final boolean forceUpdate) {
        // if a custom language is requested (non-empty language code) or a forced update is requested
        if (!languageCode.equals("") || forceUpdate) {
            try {
                // create a new Locale instance
                final Locale newLocale;

                // if the default language is requested (empty language code)
                if (languageCode.equals("")) {
                    // set the new Locale instance to the default language
                    newLocale = mOriginalLocale;
                }
                // if a custom language is requested (non-empty language code)
                else {
                    // if the language code does also contain a region
                    if (languageCode.contains("-r") || languageCode.contains("-")) {
                        // split the language code into language and region
                        final String[] language_region = languageCode.split("-(r)?");
                        // construct a new Locale object with the specified language and region
                        newLocale = new Locale(language_region[0], language_region[1]);
                    }
                    // if the language code does not contain a region
                    else {
                        // simply construct a new Locale object from the given language code
                        newLocale = new Locale(languageCode);
                    }
                }

                if (newLocale != null) {
                    // update the app's configuration to use the new Locale
                    final Resources resources = context.getBaseContext().getResources();
                    final Configuration conf = resources.getConfiguration();

                    conf.locale = newLocale;

                    conf.setLayoutDirection(conf.locale);

                    resources.updateConfiguration(conf, resources.getDisplayMetrics());

                    // overwrite the default Locale
                    Locale.setDefault(newLocale);
                }
            }
            catch (Exception e) {
                Log.e(LOG_TAG, "Language Set exception " + e.getMessage() + " " + e.getCause());
            }
        }
    }

    /**
     * Returns the original Locale instance that was in use before any custom selection may have been applied
     *
     * @return the original Locale instance
     */
    public static Locale getOriginalLocale() {
        return mOriginalLocale;
    }
}
