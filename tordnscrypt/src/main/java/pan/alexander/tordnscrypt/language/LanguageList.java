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

package pan.alexander.tordnscrypt.language;

import android.util.Base64;

import java.io.UnsupportedEncodingException;

public class LanguageList {
    private static final String CHARSET_DEFAULT = "UTF-8";
    private static final String STANDARD_OPTION_LABEL_DEFAULT = "Standard (recommended)";
    private static String sStandardOptionLabel = STANDARD_OPTION_LABEL_DEFAULT;
    private static final String[] HUMAN_RAW = {
            null,
            "QXJhYmljICjYp9mE2LnYsdio2YrYqSk=",
            "QnVsZ2FyaWFuICjQkdGK0LvQs9Cw0YDRgdC60Lgp",
            "Q2hpbmVzZSAo5Lit5paHKQ==",
            "RHV0Y2ggKE5lZGVybGFuZHNlKQ==",
            "RW5nbGlzaCAoRW5nbGlzaCk=",
            "RmlubmlzaCAoU3VvbWkp",
            "RnJlbmNoIChGcmFuw6dhaXMp",
            "R2VybWFuIChEZXV0c2NoKQ==",
            "R3JlZWsgKM6VzrvOu863zr3Ouc66zqwp",
            "SW5kb25lc2lhbiAoYmFoYXNhIEluZG9uZXNpYSk=",
            "SXRhbGlhbiAoSXRhbGlhbm8p",
            "SmFwYW5lc2UgKOaXpeacrOiqnik=",
            "UGVyc2lhbiAo2YHYp9ix2LPbjCk=",
            "UG9saXNoIChQb2xza2kp",
            "UG9ydHVndWVzZSAoUG9ydHVndcOqcyk=",
            "UG9ydHVndWVzZS1CUiAoUG9ydHVndcOqcy1CUik=",
            "UnVzc2lhbiAo0KDRg9GB0YHQutC40Lkp",
            "U3BhbmlzaCAoRXNwYcOxb2wp",
            "VHVya2lzaCAoVMO8cmvDp2Up",
            "VWtyYWluaWFuICjQo9C60YDQsNGX0L3RgdGM0LrQsCk="
    };
    private static final String[] MACHINE = {
            "",
            "ar",
            "bg",
            "zh",
            "nl",
            "en",
            "fi",
            "fr",
            "de",
            "el",
            "in",
            "it",
            "ja",
            "fa",
            "pl",
            "pt",
            "pt-rBR",
            "ru",
            "es",
            "tr",
            "uk"
    };
    private static String[] mHuman;

    static String[] getHumanReadable() {
        if (mHuman == null) {
            mHuman = new String[HUMAN_RAW.length];

            for (int i = 1; i < mHuman.length; i++) {
                try {
                    mHuman[i] = decodeBase64(HUMAN_RAW[i]);
                } catch (Exception e) {
                    mHuman[i] = MACHINE[i];
                }
            }
        }

        // update the label for the default option with the supplied string
        mHuman[0] = getStandardOptionLabel();

        return mHuman;
    }

    static String[] getMachineReadable() {
        return MACHINE;
    }

    private static String getStandardOptionLabel() {
        return sStandardOptionLabel;
    }

    public static void setStandardOptionLabel(final String label) {
        sStandardOptionLabel = label;
    }

    private static String decodeBase64(final String base64) throws IllegalArgumentException, UnsupportedEncodingException {
        final byte[] bytes = Base64.decode(base64, Base64.DEFAULT);

        return new String(bytes, CHARSET_DEFAULT);
    }
}
