package top.panll.assist.service;

import com.alibaba.fastjson.JSONObject;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.progress.Progress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import top.panll.assist.dto.SpaceInfo;
import top.panll.assist.utils.RedisUtil;
import top.panll.assist.dto.MergeOrCutTaskInfo;
import top.panll.assist.dto.UserSettings;
import top.panll.assist.utils.DateUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
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

    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private SimpleDateFormat simpleDateFormatForTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private String keyStr = "MERGEORCUT";

    @Bean("threadPoolExecutor")
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
            List<File> result = Arrays.asList(files);
            Collections.sort(result);
            return result;
        }else {
            return null;
        }
    }

    public SpaceInfo getSpaceInfo(){
        File recordFile = new File(userSettings.getRecord());
        SpaceInfo spaceInfo = new SpaceInfo();
        spaceInfo.setFree(recordFile.getFreeSpace());
        spaceInfo.setTotal(recordFile.getTotalSpace());
        return spaceInfo;
    }

//    public String getPrintSize(long size) {
//        // 如果字节数少于1024，则直接以B为单位，否则先除于1024，后3位因太少无意义
//        if (size < 1024) {
//            return String.valueOf(size) + "B";
//        } else {
//            size = size / 1024;
//        }
//        // 如果原字节数除于1024之后，少于1024，则可以直接以KB作为单位
//        // 因为还没有到达要使用另一个单位的时候
//        // 接下去以此类推
//        if (size < 1024) {
//            return String.valueOf(size) + "KB";
//        } else {
//            size = size / 1024;
//        }
//        if (size < 1024) {
//            // 因为如果以MB为单位的话，要保留最后1位小数，
//            // 因此，把此数乘以100之后再取余
//            size = size * 100;
//            return String.valueOf((size / 100)) + "."
//                    + String.valueOf((size % 100)) + "MB";
//        } else {
//            // 否则如果要以GB为单位的，先除于1024再作同样的处理
//            size = size * 100 / 1024;
//            return String.valueOf((size / 100)) + "."
//                    + String.valueOf((size % 100)) + "GB";
//        }
//    }

    public List<File> getStreamList(String app) {
        File appFile = new File(userSettings.getRecord() + File.separator + app);
        if (appFile != null) {
            File[] files = appFile.listFiles();
            List<File> result = Arrays.asList(files);
            Collections.sort(result);
            return result;
        }else {
            return null;
        }
    }

    /**
     * 对视频文件重命名， 00：00：00-00：00：00
     * @param file
     * @throws ParseException
     */
    public void handFile(File file) {
        FFprobe ffprobe = FFmpegExecUtils.getInstance().ffprobe;
        if(file.exists() && file.isFile() && !file.getName().startsWith(".")&& file.getName().endsWith(".mp4") && file.getName().indexOf(":") < 0) {
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
            } catch (IOException e) {
                logger.warn("文件可能以损坏[{}]", file.getAbsolutePath());
//                e.printStackTrace();
            } catch (ParseException e) {
                logger.error("时间格式化失败", e.getMessage());
            }
        }
    }

    public List<Map<String, String>> getList() {

        List<Map<String, String>> result = new ArrayList<>();

        List<File> appList = getAppList();
        if (appList != null && appList.size() > 0) {
            for (File appFile : appList) {
                if (appFile.isDirectory()) {
                    List<File> streamList = getStreamList(appFile.getName());
                    if (streamList != null && streamList.size() > 0) {
                        for (File streamFile : streamList) {
                            Map<String, String> data = new HashMap<>();
                            data.put("app", appFile.getName());
                            data.put("stream", streamFile.getName());

                            BasicFileAttributes bAttributes = null;
                            try {
                                bAttributes = Files.readAttributes(streamFile.toPath(),
                                        BasicFileAttributes.class);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            data.put("time", simpleDateFormatForTime.format(new Date(bAttributes.lastModifiedTime().toMillis())));
                            result.add(data);
                        }
                    }
                }
            }
        }
        result.sort((Map f1, Map f2)->{
            Date time1 = null;
            Date time2 = null;
            try {
                time1 = simpleDateFormatForTime.parse(f1.get("time").toString());
                time2 = simpleDateFormatForTime.parse(f2.get("time").toString());
            } catch (ParseException e) {
                logger.error("时间格式化失败", e.getMessage());
            }
            return time1.compareTo(time2) * -1;
        });
        return result;
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
        File streamFile = new File(recordFile.getAbsolutePath() + File.separator + app + File.separator + stream + File.separator);
        if (!streamFile.exists()) {
            logger.warn("获取[app: {}, stream: {}, statime: {}, endTime: {}]的视频时未找到目录： {}", app, stream,
                    startTimeStr, endTimeStr, stream);
            return result;
        }
        File[] dateFiles = streamFile.listFiles((File dir, String name) -> {
            Date fileDate = null;
            Date startDate = new Date(startTime.getTime() - ((startTime.getTime() + 28800000) % (86400000)));
            Date endDate = new Date(endTime.getTime() - ((endTime.getTime() + 28800000) % (86400000)));
            try {
                fileDate = formatterForDate.parse(name);
            } catch (ParseException e) {
                logger.error("过滤日期文件时异常： {}-{}", name, e.getMessage());
                return false;
            }
            return (DateUtils.getStartOfDay(startDate).compareTo(fileDate) <= 0
                    || DateUtils.getEndOfDay(endDate).compareTo(fileDate) >= 0) ;
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
        if (filesInTime== null || filesInTime.size() == 0){
            logger.info("此时间段未未找到视频文件");
            return null;
        }
        File recordFile = new File(new File(userSettings.getRecord()).getParentFile().getAbsolutePath()  + File.separator + "recordTemp");
        if (!recordFile.exists()) recordFile.mkdirs();

        String taskId = DigestUtils.md5DigestAsHex(String.valueOf(System.currentTimeMillis()).getBytes());
        MergeOrCutTaskInfo mergeOrCutTaskInfo = new MergeOrCutTaskInfo();
        mergeOrCutTaskInfo.setId(taskId);
        mergeOrCutTaskInfo.setApp(app);
        mergeOrCutTaskInfo.setStream(stream);
        mergeOrCutTaskInfo.setStartTime(simpleDateFormatForTime.format(startTime));
        mergeOrCutTaskInfo.setEndTime(simpleDateFormatForTime.format(endTime));

        Runnable task = () -> {
            FFmpegExecUtils.getInstance().mergeOrCutFile(filesInTime, recordFile, taskId, (String status, double percentage, String result)->{

                // 发出redis通知
                if (status.equals(Progress.Status.END.name())) {
                    mergeOrCutTaskInfo.setPercentage("1");
                    mergeOrCutTaskInfo.setRecordFile(result);
                    stringRedisTemplate.convertAndSend("topic_mergeorcut_end", JSONObject.toJSONString(mergeOrCutTaskInfo));
                }else {
                    mergeOrCutTaskInfo.setPercentage(percentage + "");
                    stringRedisTemplate.convertAndSend("topic_mergeorcut_continue",  JSONObject.toJSONString(mergeOrCutTaskInfo));
                }
                String key = String.format("%S_%S_%S_%S", keyStr, app, stream, taskId);
                redisUtil.set(key, mergeOrCutTaskInfo);
            });
        };
        processThreadPool.execute(task);
        return taskId;
    }


    public List<File> getDateList(String app, String stream, Integer year, Integer month) {
        File recordFile = new File(userSettings.getRecord());
        File streamFile = new File(recordFile.getAbsolutePath() + File.separator + app + File.separator + stream);
        if (!streamFile.exists()) {
            logger.warn("获取[app: {}, stream: {}]的视频时未找到目录： {}", app, stream, stream);
            return null;
        }
        File[] dateFiles = streamFile.listFiles((File dir, String name)->{
            Date date = null;
            try {
                date = simpleDateFormat.parse(name);
            } catch (ParseException e) {
                logger.error("格式化时间{}错误", name);
            }
            Calendar c = Calendar.getInstance();
            c.setTime(date);
            int y = c.get(Calendar.YEAR);
            int m = c.get(Calendar.MONTH);
            if (year != null) {
                if (month != null) {
                    return  y == year && m == month;
                }else {
                    return  y == year;
                }
            }else {
                return true;
            }

        });
        List<File> dateFileList = Arrays.asList(dateFiles);

        dateFileList.sort((File f1, File f2)->{
            int sortResult = 0;

            try {
                sortResult = simpleDateFormat.parse(f1.getName()).compareTo(simpleDateFormat.parse(f2.getName()));
            } catch (ParseException e) {
                logger.error("格式化时间{}/{}错误", f1.getName(), f2.getName());
            }
            return sortResult;
        });
        return dateFileList;
    }

    public List<MergeOrCutTaskInfo> getTaskListForDownload(boolean idEnd) {
        ArrayList<MergeOrCutTaskInfo> result = new ArrayList<>();
        List<Object> taskCatch = redisUtil.scan(String.format("%S_*_*_*", keyStr));
        for (int i = 0; i < taskCatch.size(); i++) {
            String keyItem = taskCatch.get(i).toString();
            MergeOrCutTaskInfo mergeOrCutTaskInfo = (MergeOrCutTaskInfo)redisUtil.get(keyItem);
            if (mergeOrCutTaskInfo != null){
                if (idEnd) {
                    if (Double.parseDouble(mergeOrCutTaskInfo.getPercentage()) == 1){
                        result.add(mergeOrCutTaskInfo);
                    }
                }else {
                    if (Double.parseDouble(mergeOrCutTaskInfo.getPercentage()) < 1){
                        result.add((MergeOrCutTaskInfo)redisUtil.get(keyItem));
                    }
                }
            }
        }
        result.sort((MergeOrCutTaskInfo m1, MergeOrCutTaskInfo m2)->{
            int sortResult = 0;
            try {
                sortResult = simpleDateFormatForTime.parse(m1.getStartTime()).compareTo(simpleDateFormatForTime.parse(m2.getStartTime()));
                if (sortResult == 0) {
                    sortResult = simpleDateFormatForTime.parse(m1.getEndTime()).compareTo(simpleDateFormatForTime.parse(m2.getEndTime()));
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return sortResult * -1;
        });
        return result;
    }

    public boolean stopTask(String taskId) {
//        Runnable task = taskList.get(taskId);
//        boolean result = false;
//        if (task != null) {
//            processThreadPool.remove(task);
//            taskList.remove(taskId);
//            List<Object> taskCatch = redisUtil.scan(String.format("%S_*_*_%S", keyStr, taskId));
//            if (taskCatch.size() == 1) {
//                redisUtil.del((String) taskCatch.get(0));
//                result = true;
//            }
//        }
        return false;
    }
}
