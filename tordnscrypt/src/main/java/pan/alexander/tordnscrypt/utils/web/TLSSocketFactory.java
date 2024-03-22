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

package pan.alexander.tordnscrypt.utils.web;

import android.content.Context;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import pan.alexander.tordnscrypt.R;

public class TLSSocketFactory extends SSLSocketFactory {

    private final Context appContext;
    private final SSLSocketFactory delegate;

    public TLSSocketFactory(Context appContext) throws KeyManagementException, NoSuchAlgorithmException,
            CertificateException, KeyStoreException, IOException {
        this.appContext = appContext;
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, getTrustManagerFactory().getTrustManagers(), null);
        delegate = context.getSocketFactory();
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return enableTLSOnSocket(delegate.createSocket());
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return enableTLSOnSocket(delegate.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return enableTLSOnSocket(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return enableTLSOnSocket(delegate.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return enableTLSOnSocket(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return enableTLSOnSocket(delegate.createSocket(address, port, localAddress, localPort));
    }

    private Socket enableTLSOnSocket(Socket socket) {
        if ((socket instanceof SSLSocket)) {
            ((SSLSocket) socket).setEnabledProtocols(new String[]{"TLSv1.1", "TLSv1.2"});
        }
        return socket;
    }

    public TrustManagerFactory getTrustManagerFactory() throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException {

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate isgCertificateX1 = cf.generateCertificate(appContext.getResources().openRawResource(R.raw.isrg_root_x1));
        Certificate isgCertificateX2 = cf.generateCertificate(appContext.getResources().openRawResource(R.raw.isrg_root_x2));

        // Create a KeyStore containing our trusted CAs
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("isrg_root_x1", isgCertificateX1);
        keyStore.setCertificateEntry("isrg_root_x2", isgCertificateX2);

        //Default TrustManager to get device trusted CA
        TrustManagerFactory defaultTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        defaultTmf.init((KeyStore) null);

        X509TrustManager trustManager = (X509TrustManager) defaultTmf.getTrustManagers()[0];
        int number = 0;
        for (Certificate cert : trustManager.getAcceptedIssuers()) {
            keyStore.setCertificateEntry(Integer.toString(number), cert);
            number++;
        }

        // Create a TrustManager that trusts the CAs in our KeyStore
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        return tmf;
    }
}
