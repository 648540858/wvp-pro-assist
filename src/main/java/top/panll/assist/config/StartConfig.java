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
        if (!recordFile.exists()){
            logger.warn("[userSettings.record]路径不存在，开始创建");
            boolean mkResult = recordFile.mkdirs();
            if (!mkResult) {
                logger.info("[userSettings.record]目录创建失败");
                System.exit(1);
            }
        }else {
            if ( !recordFile.isDirectory()) {
                logger.warn("[userSettings.record]路径是文件，请修改为目录");
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
        }
    }
}
