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

package pan.alexander.tordnscrypt.utils.portchecker;

import static pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS;
import static pan.alexander.tordnscrypt.utils.Constants.NUMBER_REGEX;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;

import java.net.ConnectException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import javax.inject.Inject;

public class PortChecker {

    @Inject
    public PortChecker() {
    }

    public boolean isPortBusy(String port) {
        int portInt;
        if (port.matches(NUMBER_REGEX)) {
            portInt = Integer.parseInt(port);
        } else {
            return true;
        }
        return !isPortAvailable(portInt);
    }

    public boolean isPortAvailable(int port) {
        if (isTCPPortAvailable(port)) {
            return isUDPPortAvailable(port);
        }
        return false;
    }

    public String getFreePort(String port) {

        if (!port.matches(NUMBER_REGEX)) {
            return port;
        }

        int portInt = Integer.parseInt(port);

        for (int i = 0; i < 3; i++) {
            int freePort = portInt + i + 1;
            if (isPortAvailable(freePort)) {
                return String.valueOf(freePort);
            }
        }
        return port;
    }

    private boolean isTCPPortAvailable(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(LOOPBACK_ADDRESS, port), 200);
            socket.setSoTimeout(1);
            return false;
        } catch (ConnectException | SocketTimeoutException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isUDPPortAvailable(int port) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            socket.setSoTimeout(1);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }
}
