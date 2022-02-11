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

package pan.alexander.tordnscrypt.utils.dns;

class DnsMessage {
    static final int OpCodeQuery = 0;
    static final int OpCodeIQuery = 1;
    static final int OpCodeStatus = 2;
    static final int OpCodeUpdate = 5;

    protected short messageId = 0;
    protected int opCode = OpCodeQuery;
    protected int rd = 1;
    protected int ra = 0;

    int getMessageId() {
        return messageId;
    }

    int getOpCode() {
        return opCode;
    }

    int getRD() {
        return rd;
    }

    int getRA() {
        return ra;
    }
}
