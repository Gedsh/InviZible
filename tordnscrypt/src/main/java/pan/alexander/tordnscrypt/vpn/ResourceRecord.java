package pan.alexander.tordnscrypt.vpn;
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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import androidx.annotation.NonNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ResourceRecord {
    public long Time;
    public String QName;
    public String AName;
    public String CName;
    public String HInfo;
    public String Resource;
    public int TTL;

    private static DateFormat formatter = SimpleDateFormat.getDateTimeInstance();

    public ResourceRecord() {
    }

    private String trimToNotASCIISymbols(String line) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        for (char ch : line.toCharArray()) {
            if (ch < 128) {
                result.append(ch);
            } else {
                break;
            }
        }

        return result.toString();
    }

    @NonNull
    @Override
    public String toString() {
        String result = "";

        if (!CName.isEmpty()) {
            result = formatter.format(new Date(Time).getTime()) +
                    " QName " + QName +
                    " AName " + AName +
                    " CName " + CName +
                    " TTL " + TTL +
                    " " + formatter.format(new Date(Time + TTL * 1000L).getTime());
        } else if (!Resource.isEmpty()){
            result = formatter.format(new Date(Time).getTime()) +
                    " QName " + QName +
                    " AName " + AName +
                    " Resource " + Resource +
                    " TTL " + TTL +
                    " " + formatter.format(new Date(Time + TTL * 1000L).getTime());
        } else if (!HInfo.isEmpty()){
            result = formatter.format(new Date(Time).getTime()) +
                    " QName " + QName +
                    " AName " + AName +
                    " HINFO " + trimToNotASCIISymbols(HInfo)+
                    " TTL " + TTL +
                    " " + formatter.format(new Date(Time + TTL * 1000L).getTime());
        }

        return result;
    }
}
