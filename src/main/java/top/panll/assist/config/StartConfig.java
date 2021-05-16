package top.panll.assist.config;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.panll.assist.dto.UserSettings;
import top.panll.assist.service.FFmpegExecUtils;
import top.panll.assist.service.VideoFileService;

import java.io.File;
import java.io.IOException;

/**
 * 用于启动检查环境
 */
@Component
@Order(value=1)
public class StartConfig implements CommandLineRunner {

    private final static Logger logger = LoggerFactory.getLogger(StartConfig.class);

    @Autowired
    private UserSettings userSettings;

    @Autowired
    private VideoFileService videoFileService;

    @Override
    public void run(String... args) {
        String record = userSettings.getRecord();
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
        try {
            String ffmpegPath = userSettings.getFfmpeg();
            String ffprobePath = userSettings.getFfprobe();
            FFmpeg ffmpeg = new FFmpeg(ffmpegPath);
            FFprobe ffprobe = new FFprobe(ffprobePath);
            logger.info("wvp-pro辅助程序启动成功");
            FFmpegExecUtils.getInstance().ffmpeg = ffmpeg;
            FFmpegExecUtils.getInstance().ffprobe = ffprobe;
            // 对目录进行预整理
            File[] appFiles = recordFile.listFiles();
            for (File appFile : appFiles) {
                File[] streamFiles = appFile.listFiles();
                for (File streamFile : streamFiles) {
                    File[] dateFiles = streamFile.listFiles();
                    for (File dateFile : dateFiles) {
                        File[] files = dateFile.listFiles();
                        for (File file : files) {
                            videoFileService.handFile(file);
                        }
                    }
                }
            }
        }catch (IOException exception){
            System.out.println(exception.getMessage());
            if (exception.getMessage().indexOf("ffmpeg") > 0 ) {
                logger.error("[userSettings.ffmpeg]配置错误，请检查是否已安装ffmpeg并正确配置");
                System.exit(1);
            }
            if (exception.getMessage().indexOf("ffprobe") > 0 ) {
                logger.error("[userSettings.ffprobe]配置错误，请检查是否已安装ffprobe并正确配置");
                System.exit(1);
            }
        }catch (Exception exception){
            logger.error("环境错误： " + exception.getMessage());
        }
    }
}
