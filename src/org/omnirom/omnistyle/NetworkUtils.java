/*
 *  Copyright (C) 2018 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.omnirom.omnistyle;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

public class NetworkUtils {
    private static final boolean DEBUG = false;
    private static final String TAG = "NetworkUtils";

    private static final int HTTP_READ_TIMEOUT = 60000;
    private static final int HTTP_CONNECTION_TIMEOUT = 60000;

    public static HttpsURLConnection setupHttpsRequest(String urlStr) {
        URL url;
        HttpsURLConnection urlConnection = null;
        try {
            url = new URL(urlStr);
            urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(HTTP_CONNECTION_TIMEOUT);
            urlConnection.setReadTimeout(HTTP_READ_TIMEOUT);
            urlConnection.setRequestMethod("GET");
            urlConnection.setDoInput(true);
            urlConnection.connect();
            int code = urlConnection.getResponseCode();
            if (code != HttpsURLConnection.HTTP_OK) {
                Log.d(TAG, "response:" + code);
                urlConnection.disconnect();
                return null;
            }
            return urlConnection;
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect to server", e);
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            return null;
        }
    }

    public static boolean downloadUrlFile(String url, File f) {
        if (DEBUG) Log.d(TAG, "download:" + url);

        HttpsURLConnection urlConnection = null;

        if (f.exists())
            f.delete();

        try {
            urlConnection = setupHttpsRequest(url);
            if (urlConnection == null) {
                return false;
            }
            long len = urlConnection.getContentLengthLong();
            if (len < 4L * 1024L * 1024L * 1024L) {
                byte[] buffer = new byte[262144];
                long written = 0;
                try (InputStream is = urlConnection.getInputStream();
                        FileOutputStream os = new FileOutputStream(f, false)) {
                    int r;
                    while ((r = is.read(buffer)) > 0) {
                        os.write(buffer, 0, r);
                        written += r;
                    }
                }
                return written > 0 && (len < 0 || written == len);
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "", e);
            return false;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    public static String downloadUrlMemoryAsString(String url) {
        if (DEBUG) Log.d(TAG, "download: " + url);

        HttpsURLConnection urlConnection = null;
        try {
            urlConnection = setupHttpsRequest(url);
            if (urlConnection == null) {
                return null;
            }

            try (InputStream is = urlConnection.getInputStream();
                    ByteArrayOutputStream byteArray = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = is.read(buffer)) != -1) {
                    byteArray.write(buffer, 0, count);
                }
                return byteArray.toString(StandardCharsets.UTF_8.name());
            }
        } catch (Exception e) {
            Log.e(TAG, "", e);
            return null;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }
}
