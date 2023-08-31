package top.panll.assist.service;

import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.panll.assist.dto.VideoFile;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class VideoFileFactory {

    private final static Logger logger = LoggerFactory.getLogger(VideoFileFactory.class);

    public static VideoFile createFile(FFmpegExecUtils ffmpegExecUtils, File file){
        if (!file.exists()) {
            return null;
        }
        if (!file.isFile()){
            return null;
        }
        if (!file.getName().endsWith(".mp4")){
            return null;
        }
        if (file.isHidden()){
            return null;
        }
        String date = file.getParentFile().getName();
        if (file.getName().indexOf(":") > 0) {
            // 格式为 HH:mm:ss-HH:mm:ss-时长

            String[] split = file.getName().split("-");
            if (split.length != 3) {
                return null;
            }
            String startTimeStr = date + " " + split[0];
            String endTimeStr = date + " " + split[1];
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            VideoFile videoFile = new VideoFile();
            videoFile.setFile(file);
            videoFile.setTargetFormat(false);
            try {
                Date startTimeDate = simpleDateFormat.parse(startTimeStr);
                videoFile.setStartTime(startTimeDate);
                Date endTimeDate = simpleDateFormat.parse(endTimeStr);
                videoFile.setEndTime(endTimeDate);
                videoFile.setDuration((endTimeDate.getTime() - startTimeDate.getTime()));
            } catch (ParseException e) {
                logger.error("[构建视频文件对象] 格式化时间失败, file:{}", file.getAbsolutePath(), e);
                return null;
            }
            return videoFile;

        }else if (getStrCountInStr(file.getName(), "-") == 3){

            // 格式为zlm的录制格式 HH-mm-ss-序号
            String startStr = file.getName().substring(0, file.getName().lastIndexOf("-"));
            String startTimeStr = date  + " " + startStr;
            VideoFile videoFile = null;
            try {
                FFmpegProbeResult fFmpegProbeResult = ffmpegExecUtils.getFfprobe().probe(file.getAbsolutePath());
                double duration = fFmpegProbeResult.getFormat().duration * 1000;
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
                Date startTimeDate = simpleDateFormat.parse(startTimeStr);
                Date endTimeDate = new Date(startTimeDate.getTime() + new Double(duration).longValue());
                videoFile = new VideoFile();
                videoFile.setTargetFormat(false);
                videoFile.setFile(file);
                videoFile.setStartTime(startTimeDate);
                videoFile.setEndTime(endTimeDate);
                videoFile.setDuration((endTimeDate.getTime() - startTimeDate.getTime())/1000);
            } catch (IOException e) {
                logger.error("[构建视频文件对象] 获取视频时长失败, file:{}", file.getAbsolutePath(), e);
                return null;
            } catch (ParseException e) {
                logger.error("[构建视频文件对象] 格式化时间失败, file:{}", file.getAbsolutePath(), e);
                return null;
            }
            return videoFile;
        }else if (getStrCountInStr(file.getName(), "-") == 2 && file.getName().length() == 10 ){
            // 格式为zlm的录制格式 HH-mm-ss
            String startTimeStr = date  + " " + file.getName();
            VideoFile videoFile = null;
            try {
                FFmpegProbeResult fFmpegProbeResult = ffmpegExecUtils.getFfprobe().probe(file.getAbsolutePath());
                double duration = fFmpegProbeResult.getFormat().duration * 1000;
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
                Date startTimeDate = simpleDateFormat.parse(startTimeStr);
                Date endTimeDate = new Date(startTimeDate.getTime() + new Double(duration).longValue());
                videoFile = new VideoFile();
                videoFile.setTargetFormat(false);
                videoFile.setFile(file);
                videoFile.setStartTime(startTimeDate);
                videoFile.setEndTime(endTimeDate);
                videoFile.setDuration((endTimeDate.getTime() - startTimeDate.getTime())/1000);
            } catch (IOException e) {
                logger.error("[构建视频文件对象] 获取视频时长失败, file:{}", file.getAbsolutePath(), e);
                return null;
            } catch (ParseException e) {
                logger.warn("[构建视频文件对象] 格式化时间失败, file:{}", file.getAbsolutePath(), e);
                return null;
            }
            return videoFile;
        }else if (getStrCountInStr(file.getName(), "-") == 1 ){
            // 格式为zlm的录制格式 HH-mm-ss
            // 格式为 HH:mm:ss-HH:mm:ss-时长

            String[] split = file.getName().split("-");
            if (split.length != 2) {
                return null;
            }
            String startTimeStr = date + " " + split[0];
            String endTimeStr = date + " " + split[1];
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HHmmss");
            VideoFile videoFile = new VideoFile();
            videoFile.setTargetFormat(true);
            videoFile.setFile(file);
            try {
                Date startTimeDate = simpleDateFormat.parse(startTimeStr);
                videoFile.setStartTime(startTimeDate);
                Date endTimeDate = simpleDateFormat.parse(endTimeStr);
                videoFile.setEndTime(endTimeDate);
                videoFile.setDuration((endTimeDate.getTime() - startTimeDate.getTime()));
            } catch (ParseException e) {
                logger.error("[构建视频文件对象] 格式化时间失败, file:{}", file.getAbsolutePath(), e);
                return null;
            }
            return videoFile;
        }else {
            return null;
        }
    }


    public static int getStrCountInStr(String sourceStr, String content) {
        int index = sourceStr.indexOf(content);
        if (index < 0) {
            return 0;
        }
        int count = 1;
        int lastIndex = sourceStr.lastIndexOf(content);
        while (index != lastIndex) {
            index = sourceStr.indexOf(content, index + 1);
            count++;
        }
        return count;
    }

}
