package top.panll.assist.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.panll.assist.controller.bean.WVPResult;
import top.panll.assist.service.VideoFileService;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@CrossOrigin
@RestController
@RequestMapping("/api/record")
public class RecordController {

    private final static Logger logger = LoggerFactory.getLogger(RecordController.class);

    @Autowired
    private VideoFileService videoFileService;

    private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 获取app文件夹列表
     * @return
     */
    @GetMapping(value = "/app/list")
    @ResponseBody
    public WVPResult<List<String>> getAppList(){
        WVPResult<List<String>> result = new WVPResult<>();
        List<String> resultData = new ArrayList<>();
        List<File> appList = videoFileService.getAppList();
        for (File file : appList) {
            resultData.add(file.getName());
        }
        result.setCode(0);
        result.setMsg("success");
        result.setData(resultData);
        return result;
    }

    /**
     * 获取stream文件夹列表
     * @return
     */
    @GetMapping(value = "/stream/list")
    @ResponseBody
    public WVPResult<List<String>> getStreamList(@RequestParam String app ){
        WVPResult<List<String>> result = new WVPResult<>();
        List<String> resultData = new ArrayList<>();
        if (app == null) {
            result.setCode(400);
            result.setMsg("app不能为空");
            return result;
        }
        List<File> streamList = videoFileService.getStreamList(app);
        for (File file : streamList) {
            resultData.add(file.getName());
        }
        result.setCode(0);
        result.setMsg("success");
        result.setData(resultData);
        return result;
    }

    /**
     * 获取视频文件列表
     * @return
     */
    @GetMapping(value = "/file/list")
    @ResponseBody
    public WVPResult<List<String>> getRecordList(@RequestParam String app,
                                      @RequestParam String stream,
                                      @RequestParam String startTime,
                                      @RequestParam String endTime
    ){

        WVPResult<List<String>> result = new WVPResult<>();
        // TODO 暂时开始时间与结束时间为必传， 后续可不传或只传其一
        List<String> recordList = new ArrayList<>();
        try {
            Date startTimeDate  = null;
            Date endTimeDate  = null;
            if (startTime != null ) {
                startTimeDate = formatter.parse(startTime);
            }
            if (endTime != null ) {
                endTimeDate = formatter.parse(endTime);
            }

            List<File> filesInTime = videoFileService.getFilesInTime(app, stream, startTimeDate, endTimeDate);
            if (filesInTime != null && filesInTime.size() > 0) {
                for (File file : filesInTime) {
                    recordList.add(file.getName());
                }
            }
            result.setCode(0);
            result.setMsg("success");
            result.setData(recordList);
        } catch (ParseException e) {
            logger.error("错误的开始时间[{}]或结束时间[{}]", startTime, endTime);
            result.setCode(400);
            result.setMsg("错误的开始时间或结束时间");
        }
        return result;
    }


    /**
     * 添加视频裁剪合并任务
     * @param app
     * @param stream
     * @param startTime
     * @param endTime
     * @return
     */
    @GetMapping(value = "/file/download/task")
    @ResponseBody
    public WVPResult<String> addTaskForDownload(@RequestParam String app,
                                      @RequestParam String stream,
                                      @RequestParam String startTime,
                                      @RequestParam String endTime
    ){
        WVPResult<String> result = new WVPResult<>();
        Date startTimeDate  = null;
        Date endTimeDate  = null;
        try {
            if (startTime != null ) {
                startTimeDate = formatter.parse(startTime);
            }
            if (endTime != null ) {
                    endTimeDate = formatter.parse(endTime);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        String id = videoFileService.mergeOrCut(app, stream, startTimeDate, endTimeDate);
        result.setCode(0);
        result.setMsg(id!= null?"success":"error");
        result.setData(id);
        return result;
    }

    /**
     * 录制完成的通知
     * @return
     */
    @GetMapping(value = "/end")
    @ResponseBody
    public WVPResult<String> recordEnd(@RequestParam String path
    ){
        File file = new File(path);
        WVPResult<String> result = new WVPResult<>();
        if (file.exists()) {
            try {
                videoFileService.handFile(file);
                result.setCode(0);
                result.setMsg("success");
            } catch (ParseException e) {
                e.printStackTrace();
                result.setCode(500);
                result.setMsg("error");
            }
        }else {
            result.setCode(400);
            result.setMsg("路径不存在");
        }
        return result;
    }
}
