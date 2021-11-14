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

package pan.alexander.tordnscrypt.utils.dns;

import java.util.Date;
import java.util.Locale;

public final class Record {
    public static final int TTL_MIN_SECONDS = 600;
    public static final int TTL_Forever = -1;

    public static class Source {
        public static final int Unknown = 0;
        public static final int Custom = 1;
        public static final int System = 3;
        public static final int Udp = 4;
        public static final int Doh = 5;
    }

    public static final int TYPE_A = 1;

    public static final int TYPE_AAAA = 28;

    public static final int TYPE_CNAME = 5;

    public static final int TYPE_PTR = 12;

    public static final int TYPE_TXT = 16;

    public final String value;

    public final int type;

    public final int ttl;

    public final long timeStamp;

    /**
     * Record source, httpDns or System
     * {@link Source}
     */
    public final int source;

    public final String server;

    public Record(String value, int type, int ttl) {
        this.value = value;
        this.type = type;
        this.ttl = ttl;
        this.timeStamp = new Date().getTime() / 1000;
        this.source = Source.Unknown;
        this.server = null;
    }

    public Record(String value, int type, int ttl, long timeStamp, int source) {
        this.value = value;
        this.type = type;
        this.ttl = Math.max(ttl, TTL_MIN_SECONDS);
        this.timeStamp = timeStamp;
        this.source = source;
        this.server = null;
    }

    public Record(String value, int type, int ttl, long timeStamp, int source, String server) {
        this.value = value;
        this.type = type;
        this.ttl = Math.max(ttl, TTL_MIN_SECONDS);
        this.timeStamp = timeStamp;
        this.source = source;
        this.server = server;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Record)) {
            return false;
        }
        Record another = (Record) o;
        return this.value.equals(another.value)
                && this.type == another.type
                && this.ttl == another.ttl
                && this.timeStamp == another.timeStamp;
    }

    public boolean isA() {
        return type == TYPE_A;
    }

    public boolean isAAAA() {
        return type == TYPE_AAAA;
    }

    public boolean isCname() {
        return type == TYPE_CNAME;
    }

    public boolean isPointer() {
        return type == TYPE_PTR;
    }

    public boolean isExpired() {
        return isExpired(System.currentTimeMillis() / 1000);
    }

    public boolean isExpired(long time) {
        if (ttl == TTL_Forever) {
            return false;
        }
        return timeStamp + ttl < time;
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(),"{type:%s, value:%s, source:%s, server:%s, timestamp:%d, ttl:%d}", type, value, source, server, timeStamp, ttl);
    }
}
