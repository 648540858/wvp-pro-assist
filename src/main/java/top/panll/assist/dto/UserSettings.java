package top.panll.assist.dto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UserSettings {

    @Value("${userSettings.record}")
    private String record;

    @Value("${userSettings.ffmpeg}")
    private String ffmpeg;

    @Value("${userSettings.ffprobe}")
    private String ffprobe;

    public String getRecord() {
        return record;
    }

    public void setRecord(String record) {
        this.record = record;
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
}
