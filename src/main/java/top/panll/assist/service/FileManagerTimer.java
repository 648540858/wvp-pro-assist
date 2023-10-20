package top.panll.assist.service;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.panll.assist.dto.AssistConstants;
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

//    @Scheduled(fixedDelay = 2000)   //测试 20秒执行一次
    @Scheduled(cron = "0 0 0 * * ?")   //每天的0点执行
    public void execute(){
        if (userSettings.getRecord() == null) {
            return;
        }

        // 清理任务临时文件
        int recordTempDay = userSettings.getRecordTempDay();
        Date lastTempDate = new Date();
        Calendar lastTempCalendar = Calendar.getInstance();
        lastTempCalendar.setTime(lastTempDate);
        lastTempCalendar.add(Calendar.DAY_OF_MONTH, -recordTempDay);
        lastTempDate = lastTempCalendar.getTime();
        logger.info("[录像巡查]移除合并任务临时文件 {} 之前的文件", formatter.format(lastTempDate));
        File recordTempFile = new File(userSettings.getRecord());
        if (recordTempFile.exists() && recordTempFile.isDirectory() && recordTempFile.canWrite()) {
            File[] tempFiles = recordTempFile.listFiles();
            if (tempFiles != null) {
                for (File tempFile : tempFiles) {
                    if (tempFile.isFile() && tempFile.lastModified() < lastTempDate.getTime()) {
                        boolean result = FileUtils.deleteQuietly(tempFile);
                        if (result) {
                            logger.info("[录像巡查]成功移除合并任务临时文件 {} ", tempFile.getAbsolutePath());
                        }else {
                            logger.info("[录像巡查]合并任务临时文件移除失败 {} ", tempFile.getAbsolutePath());
                        }
                    }
                }
            }
        }
        // 清理redis记录
        String key = String.format("%S_%S_*", AssistConstants.MERGEORCUT, userSettings.getId());
        List<Object> taskKeys = redisUtil.scan(key);
        for (Object taskKeyObj : taskKeys) {
            String taskKey = (String) taskKeyObj;
            MergeOrCutTaskInfo mergeOrCutTaskInfo = (MergeOrCutTaskInfo)redisUtil.get(taskKey);
            try {
                if (StringUtils.hasLength(mergeOrCutTaskInfo.getCreateTime())
                        || simpleDateFormatForTime.parse(mergeOrCutTaskInfo.getCreateTime()).before(lastTempDate)) {
                    redisUtil.del(taskKey);
                }
            } catch (ParseException e) {
                logger.error("[清理过期的redis合并任务信息] 失败", e);
            }
        }
    }
}
