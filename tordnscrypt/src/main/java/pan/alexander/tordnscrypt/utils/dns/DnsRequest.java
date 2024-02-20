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

package pan.alexander.tordnscrypt.utils.dns;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.IDN;

class DnsRequest extends DnsMessage {

    final private int recordType;
    final private String host;

    int getRecordType() {
        return recordType;
    }

    String getHost() {
        return host;
    }

    DnsRequest(short messageId, int recordType, String host) {
        this(messageId, 0, 1, recordType, host);
    }

    DnsRequest(short messageId, int opCode, int rd, int recordType, String host) {
        this.messageId = messageId;
        this.opCode = opCode;
        this.rd = rd;
        this.recordType = recordType;
        this.host = host;
    }

    byte[] toDnsQuestionData() throws IOException {
        if (host == null || host.length() == 0) {
            throw new IOException("host can not empty");
        }

        if (opCode != OpCodeQuery && opCode != OpCodeIQuery && opCode != OpCodeStatus && opCode != OpCodeUpdate) {
            throw new IOException("opCode is not valid");
        }

        if (rd != 0 && rd != 1) {
            throw new IOException("rd is not valid");
        }

        if (recordType != Record.TYPE_A
                && recordType != Record.TYPE_AAAA
                && recordType != Record.TYPE_CNAME
                && recordType != Record.TYPE_PTR
                && recordType != Record.TYPE_TXT) {
            throw new IOException("recordType is not valid");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
        DataOutputStream dos = new DataOutputStream(baos);
        // 16 bit id
        dos.writeShort(messageId);
        // |00|01|02|03|04|05|06|07|
        // |QR|  OPCODE   |AA|TC|RD|
        dos.writeByte((opCode << 3) + rd);
        // |00|01|02|03|04|05|06|07|
        // |RA|r1|r2|r3| RCODE     |
        dos.writeByte(0x00);
        dos.writeByte(0x00);
        dos.writeByte(0x01); // QDCOUNT (number of entries in the question section)
        dos.writeByte(0x00);
        dos.writeByte(0x00);  // ANCOUNT
        dos.writeByte(0x00);
        dos.writeByte(0x00);  // NSCOUNT
        dos.writeByte(0x00);
        dos.writeByte(0x00);  // ARCOUNT

        for (String s : host.split("[.\u3002\uFF0E\uFF61]")) {
            if (s.length() > 63) {
                throw new IOException("host part is too long");
            }
            byte[] buffer = IDN.toASCII(s).getBytes();
            dos.write(buffer.length);
            dos.write(buffer, 0, buffer.length); //
        }
        dos.writeByte(0x00); /* terminating zero */
        dos.writeByte(0x00);
        dos.writeByte(recordType);
        dos.writeByte(0x00);
        dos.writeByte(0x01); /* IN - "the Internet" */

        return baos.toByteArray();
    }
}
