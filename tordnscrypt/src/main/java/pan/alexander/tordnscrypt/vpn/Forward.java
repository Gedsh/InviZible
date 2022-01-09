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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

@Keep
public class Forward {
    public int protocol;
    public String daddr;
    public int dport;
    public String raddr;
    public int rport;
    public int ruid;

    @NonNull
    @Override
    public String toString() {
        return "protocol=" + protocol + " daddr " + daddr + " port " + dport + " to " + raddr + "/" + rport + " uid " + ruid;
    }
}
