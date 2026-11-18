package com.anonusal.casio991;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 极简 HTTP 服务器：只监听 127.0.0.1 随机端口，把内置站点文件供给 WebView。
 * 目的：让 fetch()/WebAssembly 走同源 http，绕开 file:// 的 CORS 限制。
 * 不依赖任何第三方库。
 */
final class LocalServer implements Closeable {
    private final File root;
    private final ServerSocket server;
    private final Thread thread;
    private volatile boolean running = true;

    LocalServer(File root) throws IOException {
        this.root = root.getCanonicalFile();
        this.server = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
        this.thread = new Thread(this::loop, "asset-http");
        this.thread.setDaemon(true);
    }

    int port() {
        return server.getLocalPort();
    }

    void start() {
        thread.start();
    }

    private void loop() {
        while (running) {
            try {
                Socket client = server.accept();
                new Thread(() -> handle(client), "asset-req").start();
            } catch (IOException e) {
                if (running) {
                    // 单次 accept 失败不终止服务
                    continue;
                }
                break;
            }
        }
    }

    private void handle(Socket client) {
        try (client;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1));
             OutputStream out = client.getOutputStream()) {

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2 || !"GET".equals(parts[0]) && !"HEAD".equals(parts[0])) {
                out.write("HTTP/1.1 405 Method Not Allowed\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        .getBytes(StandardCharsets.US_ASCII));
                return;
            }

            // 去掉 query / fragment，URL 解码
            String rawPath = parts[1].split("[?#]", 2)[0];
            String decoded = URLDecoder.decode(rawPath, "UTF-8");
            if (!decoded.startsWith("/")) decoded = "/" + decoded;
            if (decoded.equals("/")) decoded = "/index.html";

            File file = new File(root, decoded).getCanonicalFile();
            // 防目录穿越
            if (!file.getPath().startsWith(root.getPath() + File.separator)) {
                sendStatus(out, 403, "Forbidden");
                return;
            }
            if (!file.isFile()) {
                sendStatus(out, 404, "Not Found");
                return;
            }

            byte[] body = readAll(file);
            String mime = mimeOf(file.getName());
            StringBuilder head = new StringBuilder();
            head.append("HTTP/1.1 200 OK\r\n")
                .append("Content-Type: ").append(mime).append("\r\n")
                .append("Content-Length: ").append(body.length).append("\r\n")
                .append("Cache-Control: no-store\r\n")
                .append("Access-Control-Allow-Origin: *\r\n")
                .append("Connection: close\r\n\r\n");
            out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
            if (!"HEAD".equals(parts[0])) out.write(body);
            out.flush();
        } catch (Exception ignored) {
            // 客户端断开属于正常情况
        }
    }

    private static void sendStatus(OutputStream out, int code, String text) throws IOException {
        String s = "HTTP/1.1 " + code + " " + text
                + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        out.write(s.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private static byte[] readAll(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private static String mimeOf(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        switch (ext) {
            case "html": case "htm": return "text/html; charset=utf-8";
            case "js": case "mjs": return "application/javascript; charset=utf-8";
            case "wasm": return "application/wasm";
            case "css": return "text/css; charset=utf-8";
            case "json": return "application/json; charset=utf-8";
            case "png": return "image/png";
            case "jpg": case "jpeg": return "image/jpeg";
            case "svg": return "image/svg+xml";
            case "lua": case "txt": return "text/plain; charset=utf-8";
            case "bin": default: return "application/octet-stream";
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            server.close();
        } catch (IOException ignored) {
        }
    }
}
