package top.panll.assist.service;

import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.progress.Progress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.panll.assist.config.StartConfig;
import top.panll.assist.dto.UserSettings;
import top.panll.assist.utils.DateUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class VideoFileService {

    private final static Logger logger = LoggerFactory.getLogger(VideoFileService.class);

    @Autowired
    private UserSettings userSettings;


    /**
     * 对视频文件重命名， 00：00：00-00：00：00
     * @param file
     * @throws ParseException
     */
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
            return DateUtils.getStartOfDay(fileDate).after(startTime) && DateUtils.getEndOfDay(fileDate).before(endTime);
        });

        if (dateFiles != null && dateFiles.length > 0) {
            for (File dateFile : dateFiles) {
                // TODO 按时间获取文件
                File[] files = dateFile.listFiles((File dir, String name) ->{
                    boolean filterResult = false;
                    if (name.contains(":") && name.endsWith(".mp4") && !name.startsWith(".")){
                        String[] timeArray = name.split("-");
                        if (timeArray.length == 2){
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
                boolean sortResult = false;
                String[] timeArray1 = f1.getName().split("-");
                String[] timeArray2 = f2.getName().split("-");
                if (timeArray1.length == 2 && timeArray2.length == 2){
                    File dateFile1 = f1.getParentFile();
                    File dateFile2 = f2.getParentFile();
                    String fileStartTimeStr1 = dateFile1.getName() + " " + timeArray1[0];
                    String fileStartTimeStr2 = dateFile2.getName() + " " + timeArray1[0];
                    try {
                        sortResult = formatter.parse(fileStartTimeStr1).before(formatter.parse(fileStartTimeStr2)) ;
                    } catch (ParseException e) {
                        logger.error("排序视频文件时异常： {}-{}", fileStartTimeStr1, fileStartTimeStr2);
                    }
                }
                return sortResult?1 : 0;
            });
        }
        return result;
    }


    public void mergeOrCut(String app, String stream, Date startTime, Date endTime) {
        List<File> filesInTime = this.getFilesInTime(app, stream, startTime, endTime);
        File recordFile = new File(userSettings.getRecord());
        File streamFile = new File(recordFile.getAbsolutePath() + File.separator + app + File.separator + stream);
        FFmpegExecUtils.getInstance().mergeOrCutFile(filesInTime, streamFile, (String status, double percentage, String result)->{
            if (status.equals(Progress.Status.END.name())) {

            }
        });
    }

}
