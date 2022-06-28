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

package pan.alexander.tordnscrypt.utils.parsers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CancellationException;

import javax.inject.Inject;

import kotlin.Pair;
import pan.alexander.tordnscrypt.domain.bridges.ParseBridgesResult;

public class TorBridgesParser {

    @Inject
    public TorBridgesParser() {
    }

    public Pair<Bitmap, String> parseCaptchaImage(InputStream inputStream) throws IOException {

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {

            Bitmap codeImage = null;
            final String captcha_challenge_field_value;
            String inputLine;
            boolean imageFound = false;
            boolean keywordFound = false;


            while ((inputLine = bufferedReader.readLine()) != null
                    && !Thread.currentThread().isInterrupted()) {

                if (inputLine.contains("data:image/jpeg;base64") && !imageFound) {
                    String[] imgCodeBase64 = inputLine.replace("data:image/jpeg;base64,", "").split("\"");

                    if (imgCodeBase64.length < 4) {
                        throw new IllegalStateException("Tor Project web site error");
                    }

                    byte[] data = Base64.decode(imgCodeBase64[3], Base64.DEFAULT);

                    codeImage = BitmapFactory.decodeByteArray(data, 0, data.length);

                    imageFound = true;

                    if (codeImage == null) {
                        throw new IllegalStateException("Tor Project web site error");
                    }


                } else if (inputLine.contains("captcha_challenge_field")) {
                    keywordFound = true;
                } else if (inputLine.contains("value") && keywordFound) {

                    String[] secretCodeArr = inputLine.split("\"");
                    if (secretCodeArr.length > 1) {
                        captcha_challenge_field_value = secretCodeArr[1];

                        return new Pair<>(codeImage, captcha_challenge_field_value);

                    } else {
                        throw new IllegalStateException("Tor Project website error");
                    }

                }

            }
        }

        throw new CancellationException("Possible Tor Project website data scheme changed");
    }

    public ParseBridgesResult parseBridges(InputStream inputStream) throws IOException {

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////If wrong image code try again/////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {


            Bitmap codeImage = null;
            final String captcha_challenge_field_value;

            String inputLine;
            boolean keyWordBridge = false;
            boolean wrongImageCode = false;
            boolean imageFound = false;
            boolean keywordCaptchaFound = false;
            List<String> newBridges = new LinkedList<>();

            final StringBuilder sb = new StringBuilder();
            while ((inputLine = bufferedReader.readLine()) != null
                    && !Thread.currentThread().isInterrupted()) {

                if (inputLine.contains("id=\"bridgelines\"") && !wrongImageCode) {
                    keyWordBridge = true;
                } else if (inputLine.contains("<br />") && keyWordBridge && !wrongImageCode) {
                    newBridges.add(inputLine.replace("<br />", "").trim());
                } else if (!inputLine.contains("<br />") && keyWordBridge && !wrongImageCode) {
                    break;
                } else if (inputLine.contains("captcha-submission-container")) {
                    wrongImageCode = true;
                } else if (wrongImageCode) {
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////If wrong image code try again/////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

                    if (inputLine.contains("data:image/jpeg;base64") && !imageFound) {
                        String[] imgCodeBase64 = inputLine.replace("data:image/jpeg;base64,", "").split("\"");

                        if (imgCodeBase64.length < 4) {
                            throw new IllegalStateException("Tor Project web site error");
                        }

                        byte[] data = Base64.decode(imgCodeBase64[3], Base64.DEFAULT);

                        codeImage = BitmapFactory.decodeByteArray(data, 0, data.length);

                        imageFound = true;

                        if (codeImage == null) {
                            throw new IllegalStateException("Tor Project web site error");
                        }

                    } else if (inputLine.contains("captcha_challenge_field")) {
                        keywordCaptchaFound = true;
                    } else if (inputLine.contains("value") && keywordCaptchaFound) {
                        String[] secretCodeArr = inputLine.split("\"");
                        if (secretCodeArr.length > 1 && codeImage != null) {
                            captcha_challenge_field_value = secretCodeArr[1];

                            return new ParseBridgesResult.RecaptchaChallenge(
                                    codeImage,
                                    captcha_challenge_field_value
                            );
                        } else {
                            throw new IllegalStateException("Tor Project web site error!");
                        }

                    }
                }
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            }

            if (keyWordBridge && !wrongImageCode) {

                for (String bridge : newBridges) {
                    sb.append(bridge).append((char) 10);
                }

                return new ParseBridgesResult.BridgesReady(sb.toString());

            } else if (!keyWordBridge && !wrongImageCode) {
                throw new IllegalStateException("Tor Project web site error!");
            }
        }

        throw new CancellationException("Possible Tor Project website data scheme changed");
    }
}
