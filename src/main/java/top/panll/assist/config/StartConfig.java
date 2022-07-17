package top.panll.assist.config;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFprobe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.panll.assist.dto.UserSettings;
import top.panll.assist.service.FFmpegExecUtils;
import top.panll.assist.service.VideoFileService;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 用于启动检查环境
 */
@Component
@Order(value=1)
public class StartConfig implements CommandLineRunner {

    private final static Logger logger = LoggerFactory.getLogger(StartConfig.class);

    @Value("${server.port}")
    private String port;

    @Autowired
    private UserSettings userSettings;

    @Autowired
    private VideoFileService videoFileService;


    @Override
    public void run(String... args) {
        String record = userSettings.getRecord();
        if (!record.endsWith(File.separator)) {
            userSettings.setRecord(userSettings.getRecord() + File.separator);
        }
        File recordFile = new File(record);
        if (!recordFile.exists() || !recordFile.isDirectory()) {
            logger.error("[userSettings.record]配置错误，请检查路径是否存在");
            System.exit(1);
        }
        if (!recordFile.canRead()) {
            logger.error("[userSettings.record]路径无法读取");
            System.exit(1);
        }
        if (!recordFile.canWrite()) {
            logger.error("[userSettings.record]路径无法写入");
            System.exit(1);
        }
        // 在zlm目录写入assist下载页面
        writeAssistDownPage(recordFile);
        try {

//            FFmpegExecUtils.getInstance().ffmpeg = ffmpeg;
//            FFmpegExecUtils.getInstance().ffprobe = ffprobe;
            // 对目录进行预整理
            File[] appFiles = recordFile.listFiles();
            if (appFiles != null && appFiles.length > 0) {
                for (File appFile : appFiles) {
                    File[] streamFiles = appFile.listFiles();
                    if (streamFiles != null && streamFiles.length > 0) {
                        for (File streamFile : streamFiles) {
                            File[] dateFiles = streamFile.listFiles();
                            if (dateFiles != null && dateFiles.length > 0) {
                                for (File dateFile : dateFiles) {
                                    File[] files = dateFile.listFiles();
                                    if (files != null && files.length > 0) {
                                        for (File file : files) {
                                            videoFileService.handFile(file, appFile.getName(), streamFile.getName());
                                        }
                                    }
                                }
                            }

                        }
                    }

                }
            }

        } catch (Exception exception){
            exception.printStackTrace();
            logger.error("环境错误： " + exception.getMessage());
        }
    }

    private void writeAssistDownPage(File recordFile) {
        try {
            File file = new File(recordFile.getParentFile().getAbsolutePath(), "download.html");
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();
            FileOutputStream fs = new FileOutputStream(file);
            StringBuffer stringBuffer = new StringBuffer();
            String content = "<!DOCTYPE html>\n" +
                    "<html lang=\"en\">\n" +
                    "<head>\n" +
                    "    <meta charset=\"UTF-8\">\n" +
                    "    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\n" +
                    "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                    "    <title>下载</title>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <a id=\"download\" download />\n" +
                    "    <script>\n" +
                    "        (function(){\n" +
                    "            let searchParams = new URLSearchParams(location.search);\n" +
                    "            var download = document.getElementById(\"download\");\n" +
                    "            download.setAttribute(\"href\", searchParams.get(\"url\"))\n" +
                    "            download.click()\n" +
                    "            setTimeout(()=>{\n" +
                    "                window.location.href=\"about:blank\";\n" +
                    "\t\t\t          window.close();\n" +
                    "            },200)\n" +
                    "        })();\n" +
                    "       \n" +
                    "    </script>\n" +
                    "</body>\n" +
                    "</html>";
            fs.write(content.getBytes(StandardCharsets.UTF_8));
            logger.info("已写入html配置页面： " + file.getAbsolutePath());
        } catch (FileNotFoundException e) {
            logger.error("写入html页面错误", e);
        } catch (IOException e) {
            logger.error("写入html页面错误", e);
        }


    }
}
