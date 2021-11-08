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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

public class UdpResolver extends DnsResolver {
    private final int dnsUdpPort;

    @AssistedInject
    public UdpResolver(@Assisted String serverIP, @Assisted int dnsUdpPort) {
        super(serverIP);
        this.dnsUdpPort = dnsUdpPort;
    }

    @Override
    DnsResponse request(String server, String host, int recordType) throws IOException {
        double d = Math.random();
        short messageId = (short) (d * 0xFFFF);
        DnsRequest request = new DnsRequest(messageId, recordType, host);
        byte[] requestData = request.toDnsQuestionData();

        InetAddress address = InetAddress.getByName(server);
        try (DatagramSocket socket = new DatagramSocket()) {
            DatagramPacket packet = new DatagramPacket(requestData, requestData.length,
                    address, dnsUdpPort);
            socket.setSoTimeout(timeout * 1000);
            socket.send(packet);
            packet = new DatagramPacket(new byte[1500], 1500);
            socket.receive(packet);
            return new DnsResponse(server, Record.Source.Udp, request, packet.getData());
        }
    }
}
