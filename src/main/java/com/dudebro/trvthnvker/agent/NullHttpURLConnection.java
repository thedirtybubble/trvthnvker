package com.dudebro.trvthnvker.agent;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class NullHttpURLConnection extends HttpsURLConnection {

    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final byte[] REALMS_ERROR_BYTES = "{\"errorCode\":503,\"errorMsg\":\"Realms service unavailable (stripped by agent)\"}".getBytes(StandardCharsets.UTF_8);
    private static final Certificate[] EMPTY_CERTS = new Certificate[0];

    private static final Principal DUMMY_PRINCIPAL = new Principal() {
        @Override
        public String getName() {
            return "CN=telemetry.sinkhole";
        }
    };

    private static final OutputStream DISCARD_OUTPUT_STREAM = new OutputStream() {
        @Override
        public void write(int b) {
        }

        @Override
        public void write(byte[] b, int off, int len) {
        }
    };

    private final boolean isRealms;

    public NullHttpURLConnection(URL url) {
        this(url, false);
    }

    public NullHttpURLConnection(URL url, boolean isRealms) {
        super(url);
        this.isRealms = isRealms;
        if (isRealms) {
            this.responseCode = 503;
            this.responseMessage = "Service Unavailable";
        } else {
            this.responseCode = 204;
            this.responseMessage = "No Content";
        }
        this.connected = true;
    }

    @Override
    public void connect() {
        this.connected = true;
    }

    @Override
    public void disconnect() {
        this.connected = false;
    }

    @Override
    public boolean usingProxy() {
        return false;
    }

    @Override
    public int getResponseCode() {
        return this.responseCode;
    }

    @Override
    public String getResponseMessage() {
        return this.responseMessage;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (this.responseCode >= 400) {
            throw new IOException("Server returned HTTP response code: " + this.responseCode + " for URL: " + this.url);
        }
        return new ByteArrayInputStream(EMPTY_BYTES);
    }

    @Override
    public OutputStream getOutputStream() {
        return DISCARD_OUTPUT_STREAM;
    }

    @Override
    public InputStream getErrorStream() {
        if (this.isRealms) {
            return new ByteArrayInputStream(REALMS_ERROR_BYTES);
        }
        return null;
    }

    @Override
    public int getContentLength() {
        return 0;
    }

    @Override
    public long getContentLengthLong() {
        return 0L;
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    @Override
    public String getHeaderField(String name) {
        if ("content-length".equalsIgnoreCase(name)) {
            return "0";
        }
        if ("content-type".equalsIgnoreCase(name)) {
            return "application/json";
        }
        return null;
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        return Collections.emptyMap();
    }

    @Override
    public String getHeaderField(int n) {
        return null;
    }

    @Override
    public String getHeaderFieldKey(int n) {
        return null;
    }

    @Override
    public String getCipherSuite() {
        return "TLS_AES_256_GCM_SHA384";
    }

    @Override
    public Certificate[] getLocalCertificates() {
        return null;
    }

    @Override
    public Certificate[] getServerCertificates() {
        return EMPTY_CERTS;
    }

    @Override
    public Principal getPeerPrincipal() {
        return DUMMY_PRINCIPAL;
    }

    @Override
    public Principal getLocalPrincipal() {
        return null;
    }
}
