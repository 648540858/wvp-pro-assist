package top.panll.assist.dto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author lin
 */
@Component
public class UserSettings {

    @Value("${userSettings.id}")
    private String id;

    @Value("${userSettings.record-temp:./recordTemp}")
    private String recordTempPath;

    @Value("${userSettings.record-temp-day:7}")
    private int recordTempDay;

    @Value("${userSettings.ffmpeg}")
    private String ffmpeg;

    @Value("${userSettings.ffprobe}")
    private String ffprobe;

    @Value("${userSettings.threads:2}")
    private int threads;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFfmpeg() {
        return ffmpeg;
    }

    public void setFfmpeg(String ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    public String getFfprobe() {
        return ffprobe;
    }

    public void setFfprobe(String ffprobe) {
        this.ffprobe = ffprobe;
    }


    public int getRecordTempDay() {
        return recordTempDay;
    }

    public void setRecordTempDay(int recordTempDay) {
        this.recordTempDay = recordTempDay;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public String getRecordTempPath() {
        return recordTempPath;
    }

    public void setRecordTempPath(String recordTempPath) {
        this.recordTempPath = recordTempPath;
    }
}
