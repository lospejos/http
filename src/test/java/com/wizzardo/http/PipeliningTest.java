package com.wizzardo.http;

import com.wizzardo.http.request.Header;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by wizzardo on 20.06.15.
 */
public class PipeliningTest extends ServerTest {
    @Override
    public void setUp() throws NoSuchMethodException, ClassNotFoundException, NoSuchFieldException {
        super.setUp();
    }

    @Test
    public void test_1() throws IOException {
        handler = new UrlHandler()
                .append("/", (request, response) -> response.setBody("ok")) //length = 123
                .append("/foo", (request, response) -> response.setBody("foo")) //length = 124
                .append("/foobar", (request, response) -> response.setBody("foobar")) //length = 127
        ;

        int length = rawRequest("/").length() + rawRequest("/foo").length() + rawRequest("/foobar").length();

        String response = response(request("/") + request("/foo") + request("/foobar"), length);
        Assert.assertTrue(response.contains("\r\n\r\nok"));
        Assert.assertTrue(response.contains("\r\n\r\nfoo"));
        Assert.assertTrue(response.endsWith("\r\n\r\nfoobar"));
    }

    @Test
    public void test_2() throws IOException {
        handler = new UrlHandler()
                .append("/a", (request, response) -> {
                    response.setHeader(Header.KEY_CONNECTION, Header.VALUE_KEEP_ALIVE);
                    return response.setBody("ok");
                })
        ;
        int length = rawRequest("/a").length();

        StringBuilder sb = new StringBuilder();
        int n = 4000;
        for (int i = 0; i < n; i++) {
            sb.append(request("/a"));
        }
        String response = response(sb.toString(), n * length);
        Assert.assertEquals(n, response.split("\r\n\r\nok").length);
    }

    protected String response(String request, int limit) {
        try {
            Socket socket = new Socket("localhost", port);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            ByteArrayOutputStream response = new ByteArrayOutputStream(limit);
            AtomicBoolean wait = new AtomicBoolean(true);
            new Thread(() -> {
                int r;
                int total = 0;
                byte[] bytes = new byte[1024];
                try {
                    while (total < limit && (r = in.read(bytes)) != -1) {
                        total += r;
                        response.write(bytes, 0, r);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    assert false;
                }
                synchronized (PipeliningTest.this) {
                    wait.set(false);
                    PipeliningTest.this.notify();
                }
            }).start();
            out.write(request.getBytes());

            synchronized (PipeliningTest.this) {
                if (wait.get())
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
            }
            return new String(response.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    protected String request(String path) {
        return "GET " + path + " HTTP/1.1\r\n" +
                "Host: localhost:" + port + "\r\n" +
                "Connection: Keep-Alive\r\n" +
                "\r\n";
    }

}
