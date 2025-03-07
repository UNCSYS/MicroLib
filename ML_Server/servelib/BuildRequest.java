package servelib;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class BuildRequest {
    private InputStream inputStream;
    private InputStreamReader inputStreamReader;
    private BufferedInputStream bufferedInputStream;
    private BufferedReader bufferedReader;
    private Socket socket;

    private boolean Frp = true;// RequestProcess_Frp 方法自带判断是否为frp
    private String clientIp = new String();
    private String request = new String();

    private String[] requestArr;
    private String requestType;
    private String requestBody;
    private String requestVersion;
    private String requestParameter;
    private Map<String, String> headers; // 新增字段

    public BuildRequest(Socket socket) throws IOException {
        this.socket = socket;// 这个没问题
        inputStream = socket.getInputStream();// 一级使用
        inputStreamReader = new InputStreamReader(inputStream);// 二级
        bufferedInputStream = new BufferedInputStream(inputStream);

        bufferedReader = new BufferedReader(inputStreamReader);// 三级

        if (Frp) {
            RequestProcess_Frp();
        } else {
            RequestProcess_General();
        }
        requestArr = URLDecoder.decode(request, "utf-8").split(" ");
        if (requestArr.length >= 3) {
            requestType = requestArr[0];
            requestBody = requestArr[1];
            requestVersion = requestArr[2];
        }
    }

    // 定义常量，避免硬编码
    private static final byte[] PATTERN = new byte[] { 0x21, 0x11, 0x00, 0x0C }; // "2111000C" 的字节表示
    private static final int IP_OFFSET = 8; // IP地址的起始偏移量
    private static final int IP_LENGTH = 4; // IP地址的长度（4字节）
    private static final int REQUEST_OFFSET = 16; // 请求内容的起始偏移量 32

    private void RequestProcess_Frp() throws IOException {
        // 虽然明面上只似乎只支持frp 但实际支持了frp / General
        int byteData;
        byteData = bufferedInputStream.read();
        if (byteData != 13) {
            StringBuilder requestBuilder = new StringBuilder();
            while (byteData != 10 && byteData != -1) {
                requestBuilder.append((char) byteData); // 直接将字节转换为字符
                byteData = bufferedInputStream.read();
            }
            request = requestBuilder.toString();

            if (!request.contains(" ")) {
                // 没有空格说明是非法请求
                return;
            }

            StringBuilder headersBuilder = new StringBuilder();
            while ((byteData = bufferedInputStream.read()) != -1) {
                headersBuilder.append((char) byteData);
                if (byteData == '\n' && headersBuilder.toString().endsWith("\r\n\r\n")) {
                    break;
                }
            }
            String headersString = headersBuilder.toString();

            headers = parseHeaders(headersString);

        } else if (byteData == 13) {
            while (true) {
                // 读取字节数据并解析
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                while ((byteData = bufferedInputStream.read()) != -1) {
                    if (byteData == 0x0A) { // 换行符（0A）表示结束
                        break;
                    }
                    buffer.write(byteData); // 将字节写入缓冲区
                }

                // 将缓冲区转换为字节数组
                byte[] data = buffer.toByteArray();

                // 查找模式 "2111000C" 的位置
                int patternIndex = indexOf(data, PATTERN);
                if (patternIndex != -1) {
                    // 解析客户端IP
                    byte[] ipBytes = Arrays.copyOfRange(data, patternIndex + IP_OFFSET,
                            patternIndex + IP_OFFSET + IP_LENGTH);
                    clientIp = bytesToIp(ipBytes); // 将字节数组转换为IP地址

                    // 解析请求内容
                    byte[] requestBytes = Arrays.copyOfRange(data, patternIndex + REQUEST_OFFSET, data.length);
                    request = new String(requestBytes, StandardCharsets.UTF_8); // 将字节数组转换为字符串

                    if (!request.contains(" ")) {
                        return;
                        // 根据标准 请求格式应为 (Type) (Body) (version)
                        // 如 POST /html/index.html
                        // 如果其中没有空格则为非法请求
                    }
                    // get headers
                    StringBuilder headersBuilder = new StringBuilder();
                    while ((byteData = bufferedInputStream.read()) != -1) {
                        headersBuilder.append((char) byteData);
                        if (byteData == '\n' && headersBuilder.toString().endsWith("\r\n\r\n")) {
                            break;
                        }
                    }
                    String headersString = headersBuilder.toString();

                    headers = parseHeaders(headersString);
                    return;
                }
            }
        }
    }

    private void RequestProcess_General() throws IOException {
        request = bufferedReader.readLine();
        clientIp = socket.getInetAddress().getHostAddress();

        if (!request.contains(" ")) {
            return;
        }

        String TEMP = new String();
        StringBuilder heads = new StringBuilder();

        TEMP = bufferedReader.readLine();
        while (!TEMP.equals("")) {
            heads.append(TEMP + "\n");
            TEMP = bufferedReader.readLine();
        }
        headers = parseHeaders(heads.toString());
    }

    // 工具方法：查找字节数组匹配内容
    private static int indexOf(byte[] source, byte[] pattern) {
        for (int i = 0; i <= source.length - pattern.length; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length; j++) {
                if (source[i + j] != pattern[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }

    // 工具方法：将字节数组转换为IP地址
    private static String bytesToIp(byte[] bytes) {
        return String.format("%d.%d.%d.%d", bytes[0] & 0xFF, bytes[1] & 0xFF, bytes[2] & 0xFF, bytes[3] & 0xFF);
    }

    // 工具方法：将request head部分转换为Map
    private static Map<String, String> parseHeaders(String rawData) {
        Map<String, String> headers = new HashMap<>();
        String[] lines = rawData.split("\\r?\\n"); // 按行分割

        for (String line : lines) {
            // 跳过空行和不符合键值对格式的行
            if (line.isEmpty() || !line.contains(":")) {
                continue;
            }

            // 找到冒号的位置，分割成键值对
            int colonIndex = line.indexOf(":");
            String key = line.substring(0, colonIndex).trim();
            String value = line.substring(colonIndex + 1).trim();

            // 存储键值对，多个相同的键可以拼接其值
            if (headers.containsKey(key)) {
                // 如果有重复的键，追加其值
                headers.put(key, headers.get(key) + ", " + value);
            } else {
                headers.put(key, value);
            }
        }
        return headers;
    }
    // Getters and setters

    public String getRequestType() {
        return requestType;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public String getRequestVersion() {
        return requestVersion;
    }

    public String getRequestParameter() {
        return requestParameter;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getClientIp() {
        return clientIp;
    }

    public BufferedInputStream getBufferedInputStream() {
        return bufferedInputStream;
    }
}