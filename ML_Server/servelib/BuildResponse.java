package servelib;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import servelib.log.LOGH;
import servelib.log.LOGX;

public class BuildResponse {
    private Socket socket;
    private OutputStream OutputStream;
    private log log = new log();

    // Map(fileTypes) By use in Method(output_File_Fast_path)
    private static Map<String, String> fileTypes = new HashMap<>();
    private static Map<String, String> fileflag = new HashMap<>();

    public static void init() {
        // 一些初始化
        fileTypes.put("/data/HTML", "text/html");
        fileTypes.put("/data/IMG", "image/png");
        fileTypes.put("/data/MSC", "audio/mp3");
        fileTypes.put("/data/MP4", "video/mpeg4");
        fileTypes.put("/data/JS", "application/x-javascript");
        fileTypes.put("/data/CSS", "text/css");
        fileflag.put("html", "text/html");
        fileflag.put("png", "image/png");
        fileflag.put("mp3", "audio/mp3");
        fileflag.put("mp4", "video/mpeg4");
        fileflag.put("js", "application/x-javascript");
        fileflag.put("css", "text/css");
    }

    public BuildResponse(Socket socket) throws IOException {
        if (socket.isClosed() || !socket.isConnected()) {
            log.print(LOGH.SYSTEM, LOGX.INFO,"Socket is already closed or disconnected AT BuildResponse");
        }
        this.socket = socket;
        this.OutputStream = this.socket.getOutputStream();
    }

    public void output_HTML(String Data) throws IOException {
        if (Data != null) {
            OutputStream.write("HTTP/1.1 200 OK\r\n".getBytes());
            OutputStream.write("content-type:text/html;charset=utf-8\r\n\r\n".getBytes());// content-type:text/html
            OutputStream.write(Data.getBytes());
        }
    }

    public void output_Data(String Data) throws IOException {
        if (Data != null) {
            OutputStream.write(Data.getBytes());
        }
    }

    // 带有一定保护的文件响应
    public void output_File(String contentType, String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            // TODO 404 没有这个文件

            log.print("file not exits");
            return;
        }
        if (filePath.contains("./")) {
            // TODO 企图访问以本目录为根以外的文件 - 非法请求文件
            return;
        }

        InputStream in = new FileInputStream(filePath);
        byte[] bytes = new byte[1024 * 14];// 14436
        int len;
        OutputStream.write("HTTP/1.1 200 OK\r\n".getBytes("UTF-8"));
        OutputStream.write(contentType.getBytes("UTF-8"));
        OutputStream.write(("Content-Length: " + file.length() + "\r\n").getBytes());
        OutputStream.write("\r\n".getBytes("UTF-8"));
        while ((len = in.read(bytes)) != -1) {
            OutputStream.write(bytes, 0, len);
        }
        OutputStream.close();
        in.close();
    }

    // More Convenient Method > use path or file name parser
    // 更加安全 但由于硬编码 不利于维护

    public void output_File_Fast_path(String filePath) {
        // 定义一个Map来映射文件路径前缀到 ContentType 和 file_dir

        String ContentType = null;
        String file_dir = null;
        int offset = 0;

        if (filePath.startsWith("/data/File")) {
            // File文件请求需要动态处理
            ContentType = "application/octet-stream; Content-Disposition: attachment; filename=\""
                    + filePath.substring(filePath.lastIndexOf('/') + 1);
            file_dir = "data/File/";
            offset = 10;
        } else {
            // 遍历Map查找匹配的前缀
            for (Entry<String, String> entry : fileTypes.entrySet()) {
                if (filePath.startsWith(entry.getKey())) {
                    ContentType = entry.getValue();
                    file_dir = entry.getKey().substring(1);
                    break;
                }
            }
        }

        if (file_dir != null) {
            offset = file_dir.length() + 1;
            // 如果匹配到相应的路径前缀，动态计算 offset

            // 响应文件
            try {
                output_File("Content-Type: " + ContentType + ";charset=utf-8\r\n",
                        file_dir + filePath.substring(offset));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 方便快捷 适用于所有情况
    public void output_File_Fast_suffix(String filePath) {
        if (filePath.endsWith(".html")) {

        } else if (filePath.endsWith(".css")) {

        } else if (filePath.endsWith(".js")) {

        } else if (filePath.endsWith(".mp3")) {

        } else if (filePath.endsWith(".mp4")) {

        } else if (filePath.endsWith(".png")) {

        }
        for (Entry<String, String> entry : fileflag.entrySet()) {
            if (filePath.startsWith(entry.getKey())) {
                String ContentType = entry.getValue();
                String file_dir = entry.getKey().substring(1);
                break;
            }
        }
    }
}