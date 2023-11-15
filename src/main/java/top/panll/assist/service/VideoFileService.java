package top.panll.assist.service;

import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.progress.Progress;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import top.panll.assist.controller.bean.ControllerException;
import top.panll.assist.controller.bean.ErrorCode;
import top.panll.assist.dto.*;
import top.panll.assist.utils.RedisUtil;
import top.panll.assist.utils.DateUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class VideoFileService {

    private final static Logger logger = LoggerFactory.getLogger(VideoFileService.class);

    @Autowired
    private UserSettings userSettings;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private FFmpegExecUtils ffmpegExecUtils;



    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat simpleDateFormatForTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public List<File> getAppList(Boolean sort) {
        File recordFile = new File(userSettings.getRecordTempPath());
        if (recordFile.isDirectory()) {
            File[] files = recordFile.listFiles((File dir, String name) -> {
                File currentFile = new File(dir.getAbsolutePath() + File.separator + name);
                return  currentFile.isDirectory() && !name.equals("recordTemp");
            });
            List<File> result = Arrays.asList(files);
            if (sort != null && sort) {
                Collections.sort(result);
            }
            return result;
        }else {
            return null;
        }
    }

    public SpaceInfo getSpaceInfo(){
        File recordFile = new File(userSettings.getRecordTempPath());
        SpaceInfo spaceInfo = new SpaceInfo();
        spaceInfo.setFree(recordFile.getFreeSpace());
        spaceInfo.setTotal(recordFile.getTotalSpace());
        return spaceInfo;
    }


    public List<File> getStreamList(File appFile, Boolean sort) {
        if (appFile != null && appFile.isDirectory()) {
            File[] files = appFile.listFiles((File dir, String name) -> {
                File currentFile = new File(dir.getAbsolutePath() + File.separator + name);
                return  currentFile.isDirectory();
            });
            List<File> result = Arrays.asList(files);
            if (sort != null && sort) {
                Collections.sort(result);
            }
            return result;
        }else {
            return null;
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

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HHmmss");
        SimpleDateFormat formatterForDate = new SimpleDateFormat("yyyy-MM-dd");
        String startTimeStr = null;
        String endTimeStr = null;
        if (startTime != null) {
            startTimeStr = formatter.format(startTime);
        }
        if (endTime != null) {
            endTimeStr = formatter.format(endTime);
        }

        logger.debug("获取[app: {}, stream: {}, statime: {}, endTime: {}]的视频", app, stream,
                startTimeStr, endTimeStr);

        File recordFile = new File(userSettings.getRecordTempPath());
        File streamFile = new File(recordFile.getAbsolutePath() + File.separator + app + File.separator + stream + File.separator);
        if (!streamFile.exists()) {
            logger.warn("获取[app: {}, stream: {}, statime: {}, endTime: {}]的视频时未找到目录： {}", app, stream,
                    startTimeStr, endTimeStr, stream);
            return null;
        }

        File[] dateFiles = streamFile.listFiles((File dir, String name) -> {
            Date fileDate = null;
            Date startDate = null;
            Date endDate = null;
            if (new File(dir + File.separator + name).isFile()) {
                return false;
            }
            if (startTime != null) {
                startDate = new Date(startTime.getTime() - ((startTime.getTime() + 28800000) % (86400000)));
            }
            if (endTime != null) {
                endDate = new Date(endTime.getTime() - ((endTime.getTime() + 28800000) % (86400000)));
            }
            try {
                fileDate = formatterForDate.parse(name);
            } catch (ParseException e) {
                logger.error("过滤日期文件时异常： {}-{}", name, e.getMessage());
                return false;
            }
            boolean filterResult = true;

            if (startDate != null) {
                filterResult = filterResult &&  DateUtils.getStartOfDay(startDate).compareTo(fileDate) <= 0;
            }

            if (endDate != null) {
                filterResult = filterResult &&  DateUtils.getEndOfDay(endDate).compareTo(fileDate) >= 0;
            }

            return filterResult ;
        });

        if (dateFiles != null && dateFiles.length > 0) {
            for (File dateFile : dateFiles) {
                File[] files = dateFile.listFiles((File dir, String name) ->{
                    File currentFile = new File(dir + File.separator + name);
                    VideoFile videoFile = VideoFileFactory.createFile(ffmpegExecUtils, currentFile);
                    if (videoFile == null ) {
                        return false;
                    }else {
                        if (!videoFile.isTargetFormat()) {
                            return false;
                        }
                        if (startTime == null && endTime == null) {
                           return true;
                        }else if (startTime == null && endTime != null) {
                            return videoFile.getEndTime().before(endTime)
                                    || videoFile.getEndTime().equals(endTime)
                                    || (videoFile.getEndTime().after(endTime) && videoFile.getStartTime().before(endTime));
                        }else if (startTime != null && endTime == null) {
                            return videoFile.getStartTime().after(startTime)
                                    || videoFile.getStartTime().equals(startTime)
                                    || (videoFile.getStartTime().before(startTime) && videoFile.getEndTime().after(startTime));
                        }else {
                            return videoFile.getStartTime().after(startTime)
                                    || videoFile.getStartTime().equals(startTime)
                                    || (videoFile.getStartTime().before(startTime) && videoFile.getEndTime().after(startTime))
                                    || videoFile.getEndTime().before(endTime)
                                    || videoFile.getEndTime().equals(endTime)
                                    || (videoFile.getEndTime().after(endTime) && videoFile.getStartTime().before(endTime));
                        }
                    }
                });
                if (files != null && files.length > 0) {
                    result.addAll(Arrays.asList(files));
                }
            }
        }
        if (!result.isEmpty()) {
            result.sort((File f1, File f2) -> {
                VideoFile videoFile1 = VideoFileFactory.createFile(ffmpegExecUtils, f1);
                VideoFile videoFile2 = VideoFileFactory.createFile(ffmpegExecUtils, f2);
                if (videoFile1 == null || !videoFile1.isTargetFormat() || videoFile2 == null || !videoFile2.isTargetFormat()) {
                    logger.warn("[根据时间获取视频文件] 排序错误，文件错误： {}/{}", f1.getName(), f2.getName());
                    return 0;
                }
                return videoFile1.getStartTime().compareTo(videoFile2.getStartTime());
            });
        }
        return result;
    }


    public String mergeOrCut(VideoTaskInfo videoTaskInfo) {
        assert videoTaskInfo.getFilePathList() != null;
        assert !videoTaskInfo.getFilePathList().isEmpty();
        String taskId = DigestUtils.md5DigestAsHex(String.valueOf(System.currentTimeMillis()).getBytes());
        logger.info("[录像合并] 开始合并， 任务ID：{}: ", taskId);
        List<File> fileList = new ArrayList<>();
        for (String filePath : videoTaskInfo.getFilePathList()) {
            File file = new File(filePath);
            if (!file.exists()) {
                logger.info("[录像合并] 失败， 任务ID：{}, 文件不存在: {}", taskId, filePath);
                throw new ControllerException(ErrorCode.ERROR100.getCode(), filePath + "文件不存在");
            }
            logger.info("[录像合并] 添加文件， 任务ID：{}, 文件: {}", taskId, filePath);
            fileList.add(file);
        }

        File recordFile = new File(userSettings.getRecordTempPath() );
        if (!recordFile.exists()) {
            if (!recordFile.mkdirs()) {
                logger.info("[录像合并] 失败， 任务ID：{}, 创建临时目录失败", taskId);
                throw new ControllerException(ErrorCode.ERROR100.getCode(), "创建临时目录失败");
            }
        }
        MergeOrCutTaskInfo mergeOrCutTaskInfo = new MergeOrCutTaskInfo();
        mergeOrCutTaskInfo.setId(taskId);
        mergeOrCutTaskInfo.setApp(videoTaskInfo.getApp());
        mergeOrCutTaskInfo.setStream(videoTaskInfo.getStream());
        mergeOrCutTaskInfo.setCallId(videoTaskInfo.getCallId());
        mergeOrCutTaskInfo.setStartTime(videoTaskInfo.getStartTime());
        mergeOrCutTaskInfo.setEndTime(videoTaskInfo.getEndTime());
        mergeOrCutTaskInfo.setCreateTime(simpleDateFormatForTime.format(System.currentTimeMillis()));
        if (fileList.size() == 1) {

            // 文件只有一个则不合并，直接复制过去
            mergeOrCutTaskInfo.setPercentage("1");
            // 处理文件路径
            String recordFileResultPath = recordFile.getAbsolutePath() + File.separator + taskId + ".mp4";
            String relativize = taskId + ".mp4";
            try {
                Files.copy(fileList.get(0).toPath(), Paths.get(recordFileResultPath));
            } catch (IOException e) {
                logger.info("[录像合并] 失败， 任务ID：{}", taskId, e);
                throw new ControllerException(ErrorCode.ERROR100.getCode(), e.getMessage());
            }
            mergeOrCutTaskInfo.setRecordFile("/download/" + relativize.toString());
            if (videoTaskInfo.getRemoteHost() != null) {
                mergeOrCutTaskInfo.setDownloadFile(videoTaskInfo.getRemoteHost() + "/download.html?url=download/" + relativize);
                mergeOrCutTaskInfo.setPlayFile(videoTaskInfo.getRemoteHost() + "/download/" + relativize);
            }
            String key = String.format("%S_%S_%S", AssistConstants.MERGEORCUT , userSettings.getId(), mergeOrCutTaskInfo.getId());
            redisUtil.set(key, mergeOrCutTaskInfo);
            logger.info("[录像合并] 成功， 任务ID：{}", taskId);
        }else {
            ffmpegExecUtils.mergeOrCutFile(fileList, recordFile, taskId, (status, percentage, result)->{
                // 发出redis通知
                if (status.equals(Progress.Status.END.name())) {
                    mergeOrCutTaskInfo.setPercentage("1");

                    // 处理文件路径
                    String relativize = new File(result).getName();
                    mergeOrCutTaskInfo.setRecordFile(relativize.toString());
                    if (videoTaskInfo.getRemoteHost() != null) {
                        mergeOrCutTaskInfo.setDownloadFile(videoTaskInfo.getRemoteHost() + "/download.html?url=download/" + relativize);
                        mergeOrCutTaskInfo.setPlayFile(videoTaskInfo.getRemoteHost() + "/download/" + relativize);
                    }
                    logger.info("[录像合并] 成功， 任务ID：{}", taskId);
                }else {
                    mergeOrCutTaskInfo.setPercentage(percentage + "");
                }
                String key = String.format("%S_%S_%S", AssistConstants.MERGEORCUT, userSettings.getId(), mergeOrCutTaskInfo.getId());
                redisUtil.set(key, mergeOrCutTaskInfo);
            });
        }

        return taskId;
    }

    public List<File> getDateList(File streamFile, Integer year, Integer month, Boolean sort) {
        if (!streamFile.exists() && streamFile.isDirectory()) {
            logger.warn("获取[]的视频时未找到目录： {}",streamFile.getName());
            return null;
        }
        File[] dateFiles = streamFile.listFiles((File dir, String name)->{
            File currentFile = new File(dir.getAbsolutePath() + File.separator + name);
            if (!currentFile.isDirectory()){
                return false;
            }
            Date date = null;
            try {
                date = simpleDateFormat.parse(name);
            } catch (ParseException e) {
                logger.error("格式化时间{}错误", name);
                return false;
            }
            Calendar c = Calendar.getInstance();
            c.setTime(date);
            int y = c.get(Calendar.YEAR);
            int m = c.get(Calendar.MONTH) + 1;
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
        if (dateFiles == null) {
            return new ArrayList<>();
        }
        List<File> dateFileList = Arrays.asList(dateFiles);
        if (sort != null && sort) {
            dateFileList.sort((File f1, File f2)->{
                int sortResult = 0;

                try {
                    sortResult = simpleDateFormat.parse(f1.getName()).compareTo(simpleDateFormat.parse(f2.getName()));
                } catch (ParseException e) {
                    logger.error("格式化时间{}/{}错误", f1.getName(), f2.getName());
                }
                return sortResult;
            });
        }

        return dateFileList;
    }

    public List<MergeOrCutTaskInfo> getTaskListForDownload(Boolean idEnd, String taskId) {
        ArrayList<MergeOrCutTaskInfo> result = new ArrayList<>();
        if (taskId == null) {
            taskId = "*";
        }
        List<Object> taskCatch = redisUtil.scan(String.format("%S_%S_%S", AssistConstants.MERGEORCUT,
                userSettings.getId(), taskId));
        for (int i = 0; i < taskCatch.size(); i++) {
            String keyItem = taskCatch.get(i).toString();
            MergeOrCutTaskInfo mergeOrCutTaskInfo = (MergeOrCutTaskInfo)redisUtil.get(keyItem);
            if (mergeOrCutTaskInfo != null && mergeOrCutTaskInfo.getPercentage() != null){
                if (idEnd != null ) {
                    if (idEnd) {
                        if (Double.parseDouble(mergeOrCutTaskInfo.getPercentage()) == 1){
                            result.add(mergeOrCutTaskInfo);
                        }
                    }else {
                        if (Double.parseDouble(mergeOrCutTaskInfo.getPercentage()) < 1){
                            result.add((MergeOrCutTaskInfo)redisUtil.get(keyItem));
                        }
                    }
                }else {
                    result.add((MergeOrCutTaskInfo)redisUtil.get(keyItem));
                }
            }
        }
        result.sort((MergeOrCutTaskInfo m1, MergeOrCutTaskInfo m2)->{
            int sortResult = 0;
            try {
                sortResult = simpleDateFormatForTime.parse(m1.getCreateTime()).compareTo(simpleDateFormatForTime.parse(m2.getCreateTime()));
                if (sortResult == 0) {
                    sortResult = simpleDateFormatForTime.parse(m1.getCreateTime()).compareTo(simpleDateFormatForTime.parse(m2.getCreateTime()));
                }
                if (sortResult == 0) {
                    sortResult = simpleDateFormatForTime.parse(m1.getCreateTime()).compareTo(simpleDateFormatForTime.parse(m2.getCreateTime()));
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return sortResult * -1;
        });

        return result;
    }

    public boolean collection(String app, String stream, String type) {
        File streamFile = new File(userSettings.getRecordTempPath() + File.separator + app + File.separator + stream);
        boolean result = false;
        if (streamFile.exists() && streamFile.isDirectory() && streamFile.canWrite()) {
            File signFile = new File(streamFile.getAbsolutePath() + File.separator + type + ".sign");
            try {
                result = signFile.createNewFile();
            } catch (IOException e) {
                logger.error("[收藏文件]失败，{}/{}", app, stream);
            }
        }
        return result;
    }

    public boolean removeCollection(String app, String stream, String type) {
        File signFile = new File(userSettings.getRecordTempPath() + File.separator + app + File.separator + stream + File.separator + type + ".sign");
        boolean result = false;
        if (signFile.exists() && signFile.isFile()) {
            result = signFile.delete();
        }
        return result;
    }

    public List<SignInfo> getCollectionList(String app, String stream, String type) {
        List<File> appList = this.getAppList(true);
        List<SignInfo> result = new ArrayList<>();
        if (appList.size() > 0) {
            for (File appFile : appList) {
                if (app != null) {
                    if (!app.equals(appFile.getName())) {
                        continue;
                    }
                }
                List<File> streamList = getStreamList(appFile, true);
                if (streamList.size() > 0) {
                    for (File streamFile : streamList) {
                        if (stream != null) {
                            if (!stream.equals(streamFile.getName())) {
                                continue;
                            }
                        }

                        if (type != null) {
                            File signFile = new File(streamFile.getAbsolutePath() + File.separator + type + ".sign");
                            if (signFile.exists()) {
                                SignInfo signInfo = new SignInfo();
                                signInfo.setApp(appFile.getName());
                                signInfo.setStream(streamFile.getName());
                                signInfo.setType(type);
                                result.add(signInfo);
                            }
                        }else {
                            streamFile.listFiles((File dir, String name) -> {
                                File currentFile = new File(dir.getAbsolutePath() + File.separator + name);
                                if (currentFile.isFile() && name.endsWith(".sign")){
                                    String currentType = name.substring(0, name.length() - ".sign".length());
                                    SignInfo signInfo = new SignInfo();
                                    signInfo.setApp(appFile.getName());
                                    signInfo.setStream(streamFile.getName());
                                    signInfo.setType(currentType);
                                    result.add(signInfo);
                                }
                                return false;
                            });
                        }
                    }
                }
            }
        }
        return result;
    }

    public long fileDuration(String app, String stream) {
        List<File> allFiles = getFilesInTime(app, stream, null, null);
        long durationResult = 0;
        if (allFiles != null && allFiles.size() > 0) {
            for (File file : allFiles) {
                try {
                    durationResult += ffmpegExecUtils.duration(file);
                } catch (IOException e) {
                    logger.error("获取{}视频时长错误：{}", file.getAbsolutePath(), e.getMessage());
                }
            }
        }
        return durationResult;
    }

    public int deleteFile(List<String> filePathList) {
        assert filePathList != null;
        assert filePathList.isEmpty();
        int deleteResult = 0;
        for (String filePath : filePathList) {
            File file = new File(filePath);
            if (file.exists()) {
                if (file.delete()) {
                    deleteResult ++;
                }
            }else {
                logger.warn("[删除文件] 文件不存在，{}", filePath);
            }
        }
        if (deleteResult == 0) {
            throw new ControllerException(ErrorCode.ERROR100.getCode(), "未删除任何文件");
        }
        return deleteResult;
    }
}
