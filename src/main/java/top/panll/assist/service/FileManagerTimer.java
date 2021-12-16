package top.panll.assist.service;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.panll.assist.dto.MergeOrCutTaskInfo;
import top.panll.assist.dto.UserSettings;
import top.panll.assist.utils.RedisUtil;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Component
public class FileManagerTimer {

    private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat simpleDateFormatForTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final static Logger logger = LoggerFactory.getLogger(FileManagerTimer.class);

    @Autowired
    private UserSettings userSettings;

    @Autowired
    private VideoFileService videoFileService;

    @Autowired
    private RedisUtil redisUtil;

    private final String keyStr = "MERGEORCUT";

//    @Scheduled(fixedDelay = 2000)   //测试 20秒执行一次
    @Scheduled(cron = "0 0 0 * * ?")   //每天的0点执行
    public void execute(){
        int recordDay = userSettings.getRecordDay();
        Date lastDate=new Date();
        Calendar lastCalendar = Calendar.getInstance();
        lastCalendar.setTime(lastDate);
        lastCalendar.add(Calendar.DAY_OF_MONTH, 0 - recordDay);
        lastDate = lastCalendar.getTime();
        logger.info("[录像巡查]移除 {} 之前的文件", formatter.format(lastDate));
        File recordFileDir = new File(userSettings.getRecord());
        if (recordFileDir.canWrite()) {
            List<File> appList = videoFileService.getAppList(false);
            if (appList != null && appList.size() > 0) {
                for (File appFile : appList) {
                    if ("download.html".equals(appFile.getName())) {
                        continue;
                    }
                    List<File> streamList = videoFileService.getStreamList(appFile, false);
                    if (streamList != null && streamList.size() > 0) {
                        for (File streamFile : streamList) {
                            // 带有sig标记文件的为收藏文件，不被自动清理任务移除
                            File signFile = new File(streamFile.getAbsolutePath() + File.separator + "sign");
                            if (signFile.exists()) {
                                continue;
                            }
                            List<File> dateList = videoFileService.getDateList(streamFile, null, null, false);
                            if (dateList != null && dateList.size() > 0) {
                                for (File dateFile : dateList) {
                                    try {
                                        Date parse = formatter.parse(dateFile.getName());
                                        if (parse.before(lastDate)) {
                                            boolean result = FileUtils.deleteQuietly(dateFile);
                                            if (result) {
                                                logger.info("[录像巡查]成功移除 {} ", dateFile.getAbsolutePath());
                                            }else {
                                                logger.info("[录像巡查]移除失败 {} ", dateFile.getAbsolutePath());
                                            }
                                        }
                                    } catch (ParseException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                            if (streamFile.listFiles() == null || streamFile.listFiles().length == 0) {
                                boolean result = FileUtils.deleteQuietly(streamFile);
                                if (result) {
                                    logger.info("[录像巡查]成功移除 {} ", streamFile.getAbsolutePath());
                                }else {
                                    logger.info("[录像巡查]移除失败 {} ", streamFile.getAbsolutePath());
                                }
                            }
                        }
                    }
                    if (appFile.listFiles() == null || appFile.listFiles().length == 0) {
                        boolean result = FileUtils.deleteQuietly(appFile);
                        if (result) {
                            logger.info("[录像巡查]成功移除 {} ", appFile.getAbsolutePath());
                        }else {
                            logger.info("[录像巡查]移除失败 {} ", appFile.getAbsolutePath());
                        }
                    }
                }
            }
        }
        // 清理任务临时文件
        int recordTempDay = userSettings.getRecordTempDay();
        Date lastTempDate = new Date();
        Calendar lastTempCalendar = Calendar.getInstance();
        lastTempCalendar.setTime(lastTempDate);
        lastTempCalendar.add(Calendar.DAY_OF_MONTH, 0 - recordTempDay);
        lastTempDate = lastTempCalendar.getTime();
        logger.info("[录像巡查]移除合并任务临时文件 {} 之前的文件", formatter.format(lastTempDate));
        File recordTempFile = new File(recordFileDir.getParentFile().getAbsolutePath() + File.separator +  "recordTemp");
        if (recordTempFile.exists() && recordTempFile.isDirectory() && recordTempFile.canWrite()) {
            File[] tempFiles = recordTempFile.listFiles();
            for (File tempFile : tempFiles) {
                if (tempFile.isDirectory() && new Date(tempFile.lastModified()).before(lastTempDate)) {
                    boolean result = FileUtils.deleteQuietly(tempFile);
                    if (result) {
                        logger.info("[录像巡查]成功移除合并任务临时文件 {} ", tempFile.getAbsolutePath());
                    }else {
                        logger.info("[录像巡查]合并任务临时文件移除失败 {} ", tempFile.getAbsolutePath());
                    }
                }
            }
        }
        // 清理redis记录
        String key = String.format("%S_*_*_*", keyStr);
        List<Object> taskKeys = redisUtil.scan(key);
        for (Object taskKeyObj : taskKeys) {
            String taskKey = (String) taskKeyObj;
            MergeOrCutTaskInfo mergeOrCutTaskInfo = (MergeOrCutTaskInfo)redisUtil.get(taskKey);
            try {
                if (StringUtils.isEmpty(mergeOrCutTaskInfo.getCreateTime())
                        || simpleDateFormatForTime.parse(mergeOrCutTaskInfo.getCreateTime()).before(lastTempDate)) {
                    redisUtil.del(taskKey);
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
    }
}
