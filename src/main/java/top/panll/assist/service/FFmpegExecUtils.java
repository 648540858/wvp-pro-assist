package top.panll.assist.service;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFprobe;

public class FFmpegExecUtils {

    private static FFmpegExecUtils instance;

    public FFmpegExecUtils() {
    }

    public static FFmpegExecUtils getInstance(){
        if(instance==null){
            synchronized (FFmpegExecUtils.class){
                if(instance==null){
                    instance=new FFmpegExecUtils();
                }
            }
        }
        return instance;
    }

    public FFprobe ffprobe;
    public FFmpeg ffmpeg;

}
