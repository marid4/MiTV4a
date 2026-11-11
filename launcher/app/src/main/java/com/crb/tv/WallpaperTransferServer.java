package com.crb.tv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Locale;

/**
 * Server Transfer Background Wi-Fi (Port 8888) untuk Crb Launcher:
 * Mendukung upload satu atau banyak file (.jpg, .png, .gif, .mp4) sekaligus dari HP
 * untuk dijadikan Background tunggal maupun koleksi Rolling Background (Slideshow)!
 */
public final class WallpaperTransferServer {

    public interface OnMediaReceivedListener {
        void onMediaFileReceived(File savedFile, String ext);
    }

    private final Context context;
    private final int port;
    private final OnMediaReceivedListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public WallpaperTransferServer(Context context, int port, OnMediaReceivedListener listener) {
        this.context = context.getApplicationContext();
        this.port = port;
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;
        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(port);
                    while (running) {
                        final Socket client = serverSocket.accept();
                        handleClient(client);
                    }
                } catch (Exception ignored) {
                } finally {
                    running = false;
                }
            }
        }, "WallpaperTransferServer");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {
            }
            serverSocket = null;
        }
    }

    private void handleClient(Socket client) {
        try {
            client.setSoTimeout(30000);
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            int b;
            int state = 0;
            while ((b = in.read()) != -1) {
                headerBuf.write(b);
                if (state == 0 && b == '\r') state = 1;
                else if (state == 1 && b == '\n') state = 2;
                else if (state == 2 && b == '\r') state = 3;
                else if (state == 3 && b == '\n') {
                    break;
                } else {
                    state = 0;
                }
                if (headerBuf.size() > 16384) break;
            }

            String headerStr = new String(headerBuf.toByteArray(), StandardCharsets.UTF_8);
            String firstLine = headerStr.split("\r\n")[0];

            if (firstLine.startsWith("POST /upload")) {
                int contentLength = 0;
                String ext = "jpg";
                for (String line : headerStr.split("\r\n")) {
                    String lower = line.toLowerCase(Locale.US);
                    if (lower.startsWith("content-length:")) {
                        try {
                            contentLength = Integer.parseInt(line.substring(15).trim());
                        } catch (Exception ignored) {
                        }
                    } else if (lower.startsWith("x-file-ext:")) {
                        String rawExt = line.substring(11).trim().toLowerCase(Locale.US);
                        if ("png".equals(rawExt) || "gif".equals(rawExt) || "mp4".equals(rawExt)
                                || "webp".equals(rawExt) || "jpg".equals(rawExt) || "jpeg".equals(rawExt)) {
                            ext = "jpeg".equals(rawExt) ? "jpg" : rawExt;
                        }
                    }
                }

                if (contentLength > 0 && contentLength <= 150 * 1024 * 1024) {
                    final String finalExt = ext;
                    File bgDir = new File(context.getFilesDir(), "backgrounds");
                    if (!bgDir.exists()) bgDir.mkdirs();

                    final File dest = new File(bgDir, "bg_" + System.currentTimeMillis() + "." + finalExt);
                    BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(dest));
                    byte[] buf = new byte[16384];
                    int remaining = contentLength;
                    while (remaining > 0) {
                        int read = in.read(buf, 0, Math.min(buf.length, remaining));
                        if (read == -1) break;
                        fos.write(buf, 0, read);
                        remaining -= read;
                    }
                    fos.flush();
                    fos.close();

                    String respBody = "{\"status\":\"ok\",\"file\":\"" + dest.getName() + "\"}";
                    sendHttpResponse(out, "200 OK", "application/json", respBody.getBytes(StandardCharsets.UTF_8));

                    if (listener != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onMediaFileReceived(dest, finalExt);
                            }
                        });
                    }
                } else {
                    sendHttpResponse(out, "400 Bad Request", "text/plain", "Ukuran file tidak valid".getBytes(StandardCharsets.UTF_8));
                }
            } else {
                String html = buildUploadPageHtml();
                sendHttpResponse(out, "200 OK", "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private void sendHttpResponse(OutputStream out, String status, String contentType, byte[] body) throws Exception {
        String header = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    private String buildUploadPageHtml() {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>Crb Launcher - Transfer & Rolling Background</title>"
                + "<style>"
                + "body{background:#090D16;color:#F8FAFC;font-family:sans-serif;padding:24px;text-align:center;}"
                + ".card{max-width:460px;margin:20px auto;background:#1E293B;padding:24px;border-radius:16px;border:1px solid #38BDF8;}"
                + "h2{color:#38BDF8;margin-top:0;} p{color:#CBD5E1;font-size:14px;}"
                + "input[type=file]{display:block;width:100%;margin:18px 0;padding:12px;background:#0F172A;color:#fff;border-radius:8px;box-sizing:border-box;}"
                + "button{background:#10B981;color:#fff;border:none;padding:14px 24px;font-size:16px;font-weight:bold;border-radius:10px;width:100%;cursor:pointer;}"
                + "#status{margin-top:16px;font-weight:bold;color:#FDE047;}"
                + "</style></head><body>"
                + "<div class='card'>"
                + "<h2>Crb Launcher - Background TV</h2>"
                + "<p>Pilih 1 atau banyak file (<b>.JPG, .PNG, .GIF, atau .MP4</b>) sekaligus dari HP untuk dijadikan Background atau koleksi <b>Rolling Background</b>:</p>"
                + "<input type='file' id='fileInput' multiple accept='.jpg,.jpeg,.png,.gif,.mp4,image/*,video/mp4'>"
                + "<button onclick='uploadFiles()'>Kirim ke Crb Launcher</button>"
                + "<div id='status'></div>"
                + "</div>"
                + "<script>"
                + "async function uploadFiles(){"
                + "  var files=document.getElementById('fileInput').files;"
                + "  var st=document.getElementById('status');"
                + "  if(!files || files.length===0){st.innerText='Pilih file terlebih dahulu!';return;}"
                + "  for(var i=0;i<files.length;i++){"
                + "    var f=files[i];"
                + "    var name=f.name.toLowerCase();"
                + "    var ext='jpg';"
                + "    if(name.endsWith('.png'))ext='png';"
                + "    else if(name.endsWith('.gif'))ext='gif';"
                + "    else if(name.endsWith('.mp4'))ext='mp4';"
                + "    else if(name.endsWith('.webp'))ext='webp';"
                + "    st.style.color='#FDE047';"
                + "    st.innerText='Mengirim ('+(i+1)+'/'+files.length+'): '+f.name+'...';"
                + "    try{"
                + "      await fetch('/upload',{method:'POST',headers:{'X-File-Ext':ext},body:f});"
                + "    }catch(e){st.style.color='#F87171';st.innerText='Gagal mengirim: '+e;return;}"
                + "  }"
                + "  st.style.color='#10B981';"
                + "  st.innerText='Berhasil! '+files.length+' file background sudah tersimpan di Crb Launcher.';"
                + "}"
                + "</script></body></html>";
    }

    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                if (nif.isLoopback() || !nif.isUp()) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip != null && !ip.startsWith("169.254")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }
}
