package top.panll.assist.service;

import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.panll.assist.config.StartConfig;
import top.panll.assist.dto.UserSettings;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class VideoFileService {

    private final static Logger logger = LoggerFactory.getLogger(VideoFileService.class);

    @Autowired
    private UserSettings userSettings;


    public void handFile(File file) throws ParseException {
        FFprobe ffprobe = FFmpegExecUtils.getInstance().ffprobe;
        if(file.isFile() && !file.getName().startsWith(".") && file.getName().indexOf(":") < 0) {
            try {
                FFmpegProbeResult in = null;
                    in = ffprobe.probe(file.getAbsolutePath());
                double duration = in.getFormat().duration * 1000;
                String endTimeStr = file.getName().replace(".mp4", "");

                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");

                File dateFile = new File(file.getParent());

                long endTime = formatter.parse(dateFile.getName() + " " + endTimeStr).getTime();
                long startTime = endTime - new Double(duration).longValue();

                String newName = file.getAbsolutePath().replace(file.getName(),
                        simpleDateFormat.format(startTime) + "-" + simpleDateFormat.format(endTime) + ".mp4");
                file.renameTo(new File(newName));
                System.out.println(newName);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }

    public List<File> getFilesInTime(String app, String stream, Date startTime, Date endTime){

        List<File> result = new ArrayList<>();

        if (app == null || stream == null) {
            return result;
        }

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String startTimeStr = formatter.format(startTime);
        String endTimeStr = formatter.format(endTime);
        logger.debug("获取[app: {}, stream: {}, statime: {}, endTime: {}]的视频", app, stream,
                startTimeStr, endTimeStr);

        File file = new File(userSettings.getRecord());
        File[] appFiles = file.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return app.equals(name);
            }
        });
        if (appFiles.length == 0) {
            logger.warn("获取[app: {}, stream: {}, statime: {}, endTime: {}]的视频时未找到目录： {}", app, stream,
                    startTimeStr, endTimeStr, app);
            return result;
        }
        File appFile = appFiles[0];
        File[] streamFiles = appFile.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return stream.equals(name);
            }
        });
        if (streamFiles.length == 0) {
            logger.warn("获取[app: {}, stream: {}, statime: {}, endTime: {}]的视频时未找到目录： {}", app, stream,
                    startTimeStr, endTimeStr, stream);
            return result;
        }
        File streamFile = streamFiles[0];
        // TODO 按时间获取文件


        return result;
    }

}
