package top.panll.assist.dto;

import java.io.File;
import java.util.Date;

/**
 * 视频文件
 */
public class VideoFile {

    /**
     * 文件对象
     */
    private File file;

    /**
     * 文件开始时间
     */
    private Date startTime;

    /**
     * 文件结束时间
     */
    private Date endTime;


    /**
     * 时长, 单位：秒
     */
    private long duration;


    /**
     * 是否是目标格式
     */
    private boolean targetFormat;

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public boolean isTargetFormat() {
        return targetFormat;
    }

    public void setTargetFormat(boolean targetFormat) {
        this.targetFormat = targetFormat;
    }
}
