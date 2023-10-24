package top.panll.assist.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import top.panll.assist.dto.CloudRecordItem;
import top.panll.assist.mapper.CloudRecordServiceMapper;
import top.panll.assist.utils.DateUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于启动检查环境
 */
@Component
@Order(value=10)
public class StartConfig implements CommandLineRunner {

    private final static Logger logger = LoggerFactory.getLogger(StartConfig.class);

    @Value("${user-settings.record}")
    private String record;

    @Value("${user-settings.media-server-id}")
    private String mediaServerId;

    @Autowired
    DataSourceTransactionManager dataSourceTransactionManager;

    @Autowired
    TransactionDefinition transactionDefinition;

    @Autowired
    private CloudRecordServiceMapper cloudRecordServiceMapper;


    @Override
    public void run(String... args) throws IOException {
        if (!record.endsWith(File.separator)) {
            record = record + File.separator;
        }

        File recordFile = new File(record);
        if (!recordFile.exists()){
            logger.warn("{}路径不存在", record);
            System.exit(1);
        }
        logger.info("开始搜集数据");
        File[] appFiles = recordFile.listFiles();
        if (appFiles == null) {
            logger.warn("{}路径下没有录像", record);
            System.exit(1);
        }
        if (appFiles.length == 0) {
            logger.warn("{}路径下没有录像", record);
            System.exit(1);
        }
        List<CloudRecordItem> cloudRecordItemList = new ArrayList<>();
        Map<String, String> renameMap = new HashMap<>();
        Map<String, List<CloudRecordItem>> dateVideoFileIndexList = new HashMap<>();
        List<String> streamFileList = new ArrayList<>();
        // 搜集数据
        for (File appFile : appFiles) {
            if (!appFile.isDirectory()) {
                continue;
            }
            String app = appFile.getName();
            File[] streamFiles = appFile.listFiles();
            if (streamFiles == null || streamFiles.length == 0) {
                continue;
            }
            for (File streamFile : streamFiles) {
                String stream = streamFile.getName();
                if ("rtp".equals(app)) {

                }else {
                    if (stream.indexOf("_") > 0) {
                        String[] streamInfoArray = stream.split("_");
                        if (streamInfoArray.length != 2) {
                            logger.warn("无法识别 {}/{}", app, stream);
                            continue;
                        }
                        stream = streamInfoArray[0];
                        String callId = streamInfoArray[1];
                        boolean collect = false;
                        boolean reserve = false;
                        File[] signFiles = streamFile.listFiles(File::isFile);
                        if (signFiles.length > 0) {
                            for (File signFile : signFiles) {
                                if (signFile.getName().equals("a.sign")) {
                                    reserve = true; // 关联
                                }else if (signFile.getName().equals("b.sign")) {
                                    collect = true; // 归档
                                }
                            }
                        }

                        File[] dateFiles = streamFile.listFiles(File::isDirectory);
                        if (dateFiles == null || dateFiles.length == 0) {
                            continue;
                        }
                        streamFileList.add(streamFile.getAbsolutePath());

                        for (File dateFile : dateFiles) {
                            if (dateFile.isDirectory()) {
                                // 检验是否是日期格式
                                if (!DateUtils.checkDateFormat(dateFile.getName())) {
                                    continue;
                                }
                                String date = dateFile.getName();
                                File[] videoFiles = dateFile.listFiles();
                                if (videoFiles == null || videoFiles.length == 0) {
                                    continue;
                                }


                                for (int i = 0; i < videoFiles.length; i++) {
                                    File videoFile = videoFiles[i];
                                    if (!videoFile.getName().endsWith(".mp4") && !videoFile.getName().contains("-")) {
                                        continue;
                                    }
                                    String[] videoInfoArray = videoFile.getName().split("-");
                                    if (videoInfoArray.length != 3) {
                                        logger.info("非目标视频文件格式，忽略此文件： {}", videoFile.getAbsolutePath() );
                                        continue;
                                    }
                                    if (!DateUtils.checkDateTimeFormat(date + " " + videoInfoArray[0])
                                            || !DateUtils.checkDateTimeFormat(date + " " + videoInfoArray[1]) ) {
                                        logger.info("目标视频文件明明异常，忽略此文件： {}", videoFile.getName() );
                                        continue;
                                    }
                                    String startTime = date + " "  + videoInfoArray[0];
                                    String endTime = date + " " + videoInfoArray[1];
                                    Long startTimeStamp = DateUtils.yyyy_MM_dd_HH_mm_ssToTimestamp(startTime) * 1000;
                                    Long endTimeStamp = DateUtils.yyyy_MM_dd_HH_mm_ssToTimestamp(endTime) * 1000;

                                    long timeLength = Long.parseLong(videoInfoArray[2].substring(0, videoInfoArray[2].length() - 4));

                                    String dataPath = appFile.getAbsolutePath() + File.separator +  stream + File.separator + dateFile.getName();
                                    if (dateVideoFileIndexList.get(dataPath) == null) {
                                        dateVideoFileIndexList.put(dataPath, new ArrayList<>());
                                    }

                                    CloudRecordItem cloudRecordItem = new CloudRecordItem();
                                    cloudRecordItem.setApp(app);
                                    cloudRecordItem.setStream(stream);
                                    cloudRecordItem.setCallId(callId);
                                    cloudRecordItem.setStartTime(startTimeStamp);
                                    cloudRecordItem.setEndTime(endTimeStamp);
                                    cloudRecordItem.setCollect(collect);
                                    cloudRecordItem.setReserve(reserve);
                                    cloudRecordItem.setMediaServerId(mediaServerId);
                                    cloudRecordItem.setFileName(DateUtils.getTimeStr(startTimeStamp) + "-" + dateVideoFileIndexList.get(dataPath).size() + ".mp4");
                                    cloudRecordItem.setFolder(streamFile.getAbsolutePath());
                                    cloudRecordItem.setFileSize(videoFile.length());
                                    cloudRecordItem.setTimeLen(timeLength);
                                    cloudRecordItem.setFilePath(appFile.getAbsolutePath() + File.separator +  stream + File.separator + dateFile.getName() + File.separator + cloudRecordItem.getFileName());
                                    cloudRecordItemList.add(cloudRecordItem);
                                    dateVideoFileIndexList.get(dataPath).add(cloudRecordItem);
                                    renameMap.put(videoFile.getAbsolutePath(), cloudRecordItem.getFilePath());
                                }
                            }
                        }
                    }
                }
            }
            logger.info("数据收集完成， 待处理数据为： {}条", cloudRecordItemList.size());
            if (cloudRecordItemList.size() == 0) {
                System.exit(1);
            }
            logger.info("开始将数据写入数据库");
            // 检查记录是否存在，存在则不写入
            TransactionStatus transactionStatus = dataSourceTransactionManager.getTransaction(transactionDefinition);
            if (!cloudRecordItemList.isEmpty()) {
                for (CloudRecordItem cloudRecordItem : cloudRecordItemList) {
                    CloudRecordItem cloudRecordItemInDb = cloudRecordServiceMapper.query(cloudRecordItem.getApp(), cloudRecordItem.getStream(),cloudRecordItem.getCallId(), cloudRecordItem.getFilePath());
                    if (cloudRecordItemInDb == null) {
                        int length = cloudRecordServiceMapper.add(cloudRecordItem);
                        if (length == 0) {
                            logger.info("数据写入数据库失败");
                            dataSourceTransactionManager.rollback(transactionStatus);
                            System.exit(1);
                        }
                    }
                }
                dataSourceTransactionManager.commit(transactionStatus);
            }
            logger.info("数据写入数据库完成");
            logger.info("开始修改磁盘文件");
            for (String oldFileName : renameMap.keySet()) {
                File oldFile = new File(oldFileName);
                File newFile = new File(renameMap.get(oldFileName));
                if (!newFile.getParentFile().exists()) {
                    newFile.getParentFile().mkdirs();
                }
                boolean result = oldFile.renameTo(new File(renameMap.get(oldFileName)));
                if (result) {
                    logger.info("重命名成功： " + oldFileName + "===" + renameMap.get(oldFileName));
                }else {
                    logger.info("重命名失败： " + oldFileName + "===" + renameMap.get(oldFileName));
                }
            }
            logger.info("修改磁盘文件完成");
            logger.info("清理失效文件夹");
            for (String streamFileStr : streamFileList) {
                File deleteFile = new File(streamFileStr);
                deleteFile(deleteFile);
            }
            logger.info("清理失效文件夹结束");
            System.exit(1);
        }


    }


    public void deleteFile(File file) {
        logger.warn("[删除文件] {} ", file.getAbsolutePath());
        if (!file.exists()) {
            logger.warn("[删除文件] {} 不存在 ", file.getAbsolutePath());
        }else {
            if (file.isFile()) {
                file.delete();
                return;
            }
            File[] files = file.listFiles();
            if (files.length > 0) {
                for (File childFile : files) {
                    deleteFile(childFile);
                }
            }
            file.delete();
        }
    }

}
