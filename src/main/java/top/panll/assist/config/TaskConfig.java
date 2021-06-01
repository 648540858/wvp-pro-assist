package top.panll.assist.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.panll.assist.service.VideoFileService;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Component
@EnableScheduling
public class TaskConfig {

    private final static Logger logger = LoggerFactory.getLogger(TaskConfig.class);

    @Autowired
    private VideoFileService videoFileService;

    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

    @Scheduled(cron = "0 0 0 * * ?")
    private void configureTasks() {
        logger.info("录像过期自检任务执行");
        List<File> appList = videoFileService.getAppList();
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, - 7);
        Date monday = calendar.getTime();
        if (appList != null && appList.size() > 0) {
            for (File appFile : appList) {
                List<File> streamList = videoFileService.getStreamList(appFile.getName());
                if (streamList != null && streamList.size() > 0) {
                    for (File streamFile : streamList) {
                        File[] recordDateFileList = streamFile.listFiles();
                        if (recordDateFileList != null && recordDateFileList.length > 0) {
                            for (File recordDateFile : recordDateFileList) {
                                try {
                                    Date fileDaye = simpleDateFormat.parse(recordDateFile.getName());
                                    if (fileDaye.before(monday)){
                                        logger.debug("移除文件[{}]", recordDateFile.getAbsolutePath());
                                        recordDateFile.delete();
                                    }
                                } catch (ParseException e) {
                                    logger.error("无法格式化[{}]为日期, 直接移除", recordDateFile.getName());
                                    recordDateFile.delete();
                                }
                            }
                        }
                        if (streamFile.listFiles().length == 0) {
                            streamFile.delete();
                        }
                    }
                }
                if (appFile.listFiles().length == 0) {
                    appFile.delete();
                }
            }
        }
    }
}
