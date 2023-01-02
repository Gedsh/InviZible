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

package pan.alexander.tordnscrypt.vpn;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

@Keep
public class ResourceRecord implements Serializable {
    public long Time;
    public String QName = "";
    public String AName = "";
    public String CName = "";
    public String HInfo = "";
    public String Resource = "";
    public int Rcode;
    private static final long serialVersionUID = 1L;

    private static final DateFormat formatter = SimpleDateFormat.getDateTimeInstance();

    public ResourceRecord() {
    }

    public ResourceRecord deepCopy() {
        ResourceRecord deepClone = null;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {

            out.writeObject(this);

            try (ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
                 ObjectInputStream in = new ObjectInputStream(bis)) {

                deepClone = (ResourceRecord) in.readObject();

            }

        } catch (IOException | ClassNotFoundException ignored) {}

        return deepClone;
    }

    private String trimToNotASCIISymbols(String line) {
        StringBuilder result = new StringBuilder();
        for (char ch : line.toCharArray()) {
            if (ch < 128) {
                result.append(ch);
            } else {
                break;
            }
        }

        return result.toString();
    }

    private String rCodeToString(int Rcode) {
        String result = "";
        switch (Rcode) {
            case 0:
                result = "DNS Query completed successfully";
                break;
            case 1:
                result = "DNS Query Format Error";
                break;
            case 2:
                result = "Server failed to complete the DNS request";
                break;
            case 3:
                result = "Domain name does not exist";
                break;
            case 4:
                result = "Function not implemented";
                break;
            case 5:
                result = "The server refused to answer for the query";
                break;
            case 6:
                result = "Name that should not exist, does exist";
                break;
            case 7:
                result = "RRset that should not exist, does exist";
                break;
            case 8:
                result = "Server not authoritative for the zone";
                break;
            case 9:
                result = "Name not in zone";
                break;
        }
        return result;
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
                    " " + rCodeToString(Rcode);
        } else if (!Resource.isEmpty()) {
            result = formatter.format(new Date(Time).getTime()) +
                    " QName " + QName +
                    " AName " + AName +
                    " Resource " + Resource +
                    " " + rCodeToString(Rcode);
        } else if (!HInfo.isEmpty()) {
            result = formatter.format(new Date(Time).getTime()) +
                    " QName " + QName +
                    " AName " + AName +
                    " HINFO " + trimToNotASCIISymbols(HInfo) +
                    " " + rCodeToString(Rcode);
        }

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceRecord that = (ResourceRecord) o;
        return Time == that.Time &&
                Rcode == that.Rcode &&
                QName.equals(that.QName) &&
                AName.equals(that.AName) &&
                CName.equals(that.CName) &&
                HInfo.equals(that.HInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Time, QName, AName, CName, HInfo, Rcode);
    }
}
