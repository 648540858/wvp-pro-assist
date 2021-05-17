package top.panll.assist.service;

import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.progress.Progress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import top.panll.assist.config.RedisUtil;
import top.panll.assist.dto.UserSettings;
import top.panll.assist.utils.DateUtils;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class VideoFileService {

    private final static Logger logger = LoggerFactory.getLogger(VideoFileService.class);

    @Autowired
    private UserSettings userSettings;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private ThreadPoolExecutor processThreadPool;


    @Bean("iniThreadPool")
    private ThreadPoolExecutor iniThreadPool() {

        int processThreadNum = Runtime.getRuntime().availableProcessors() * 10;
        LinkedBlockingQueue<Runnable> processQueue = new LinkedBlockingQueue<Runnable>(10000);
        processThreadPool = new ThreadPoolExecutor(processThreadNum,processThreadNum,
                0L, TimeUnit.MILLISECONDS,processQueue,
                new ThreadPoolExecutor.CallerRunsPolicy());
        return processThreadPool;
    }


    public List<File> getAppList() {
        File recordFile = new File(userSettings.getRecord());
        if (recordFile != null) {
            File[] files = recordFile.listFiles();
            return Arrays.asList(files);
        }else {
            return null;
        }
    }

    public List<File> getStreamList(String app) {
        File appFile = new File(userSettings.getRecord() + File.separator + app);
        if (appFile != null) {
            File[] files = appFile.listFiles();
            return Arrays.asList(files);
        }else {
            return null;
        }
    }

    /**
     * 对视频文件重命名， 00：00：00-00：00：00
     * @param file
     * @throws ParseException
     */
    public void handFile(File file) throws ParseException {
        FFprobe ffprobe = FFmpegExecUtils.getInstance().ffprobe;
        if(file.isFile() && !file.getName().startsWith(".")&& file.getName().endsWith(".mp4") && file.getName().indexOf(":") < 0) {
            try {
                FFmpegProbeResult in = null;
                in = ffprobe.probe(file.getAbsolutePath());
                double duration = in.getFormat().duration * 1000;
                String endTimeStr = file.getName().replace(".mp4", "");

                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");

                File dateFile = new File(file.getParent());

                long startTime = formatter.parse(dateFile.getName() + " " + endTimeStr).getTime();
                long durationLong = new Double(duration).longValue();
                long endTime = startTime + durationLong;
                endTime = endTime - endTime%1000;

                String newName = file.getAbsolutePath().replace(file.getName(),
                        simpleDateFormat.format(startTime) + "-" + simpleDateFormat.format(endTime) + "-" + durationLong + ".mp4");
                file.renameTo(new File(newName));
                System.out.println(newName);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }

    /**
     * 获取制定推流的指定时间段内的推流
     * @param app
     * @param stream
     * @param startTime
     * @param endTime
     * @return
     */
    public List<File> getFilesInTime(String app, String stream, Date startTime, Date endTime){

        List<File> result = new ArrayList<>();

        if (app == null || stream == null) {
            return result;
        }

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat formatterForDate = new SimpleDateFormat("yyyy-MM-dd");
        String startTimeStr = formatter.format(startTime);
        String endTimeStr = formatter.format(endTime);
        logger.debug("获取[app: {}, stream: {}, statime: {}, endTime: {}]的视频", app, stream,
                startTimeStr, endTimeStr);

        File recordFile = new File(userSettings.getRecord());
        File streamFile = new File(recordFile.getAbsolutePath() + File.separator + app + File.separator + stream);
        if (!streamFile.exists()) {
            logger.warn("获取[app: {}, stream: {}, statime: {}, endTime: {}]的视频时未找到目录： {}", app, stream,
                    startTimeStr, endTimeStr, stream);
            return result;
        }
        File[] dateFiles = streamFile.listFiles((File dir, String name) -> {
            Date fileDate = null;
            try {
                fileDate = formatterForDate.parse(name);
            } catch (ParseException e) {
                logger.error("过滤日期文件时异常： {}-{}", name, e.getMessage());
                return false;
            }
            return (DateUtils.getStartOfDay(fileDate).compareTo(startTime) >= 0
                    && DateUtils.getStartOfDay(fileDate).compareTo(endTime) <= 0) ;
        });

        if (dateFiles != null && dateFiles.length > 0) {
            for (File dateFile : dateFiles) {
                // TODO 按时间获取文件
                File[] files = dateFile.listFiles((File dir, String name) ->{
                    boolean filterResult = false;
                    if (name.contains(":") && name.endsWith(".mp4") && !name.startsWith(".")){
                        String[] timeArray = name.split("-");
                        if (timeArray.length == 3){
                            String fileStartTimeStr = dateFile.getName() + " " + timeArray[0];
                            String fileEndTimeStr = dateFile.getName() + " " + timeArray[1];
                            try {
                                filterResult = formatter.parse(fileStartTimeStr).after(startTime) && formatter.parse(fileEndTimeStr).before(endTime);
                            } catch (ParseException e) {
                                logger.error("过滤视频文件时异常： {}-{}", name, e.getMessage());
                                return false;
                            }
                        }
                    }
                    return filterResult;
                });

                List<File> fileList = Arrays.asList(files);
                result.addAll(fileList);
            }
        }
        if (result.size() > 0) {
            result.sort((File f1, File f2) -> {
                int sortResult = 0;
                String[] timeArray1 = f1.getName().split("-");
                String[] timeArray2 = f2.getName().split("-");
                if (timeArray1.length == 3 && timeArray2.length == 3){
                    File dateFile1 = f1.getParentFile();
                    File dateFile2 = f2.getParentFile();
                    String fileStartTimeStr1 = dateFile1.getName() + " " + timeArray1[0];
                    String fileStartTimeStr2 = dateFile2.getName() + " " + timeArray2[0];
                    try {
                        sortResult = formatter.parse(fileStartTimeStr1).compareTo(formatter.parse(fileStartTimeStr2));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
                return sortResult;
            });
        }
        return result;
    }


    public String mergeOrCut(String app, String stream, Date startTime, Date endTime) {
        List<File> filesInTime = this.getFilesInTime(app, stream, startTime, endTime);
        File recordFile = new File(new File(userSettings.getRecord()).getParentFile().getAbsolutePath()  + File.separator + "recordTemp");
        if (!recordFile.exists()) recordFile.mkdirs();

        String temp = DigestUtils.md5DigestAsHex(String.valueOf(System.currentTimeMillis()).getBytes());
        processThreadPool.execute(() -> {
            FFmpegExecUtils.getInstance().mergeOrCutFile(filesInTime, recordFile, temp, (String status, double percentage, String result)->{
                Map<String, String> data = new HashMap<>();
                data.put("id", temp);
                // 发出redis通知
                if (status.equals(Progress.Status.END.name())) {
                    data.put("percentage", "1");
                    data.put("recordFile", result);
                    redisUtil.set(app + "_" + stream + "_" + temp, data, 3*60*60);
                    stringRedisTemplate.convertAndSend("topic_mergeorcut_end",  data);
                }else {
                    data.put("percentage", percentage + "");
                    redisUtil.set(app + "_" + stream + "_" + temp, data, 3*60*60);
                    stringRedisTemplate.convertAndSend("topic_mergeorcut_continue",  data);
                }
            });
        });
        return temp;
    }
}
