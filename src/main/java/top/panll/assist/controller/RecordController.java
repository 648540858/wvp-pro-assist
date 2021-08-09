package top.panll.assist.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.panll.assist.controller.bean.WVPResult;
import top.panll.assist.dto.MergeOrCutTaskInfo;
import top.panll.assist.dto.SpaceInfo;
import top.panll.assist.service.VideoFileService;
import top.panll.assist.utils.PageInfo;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
    @GetMapping(value = "/list")
    @ResponseBody
    public WVPResult<PageInfo<Map<String, String>>> getList(@RequestParam int page,
                                                  @RequestParam int count){
        WVPResult<PageInfo<Map<String, String>>> result = new WVPResult<>();
        List<Map<String, String>> appList = videoFileService.getList();
        result.setCode(0);
        result.setMsg("success");

        PageInfo<Map<String, String>> stringPageInfo = new PageInfo<>(appList);
        stringPageInfo.startPage(page, count);
        result.setData(stringPageInfo);
        return result;
    }

    /**
     * 获取app文件夹列表
     * @return
     */
    @GetMapping(value = "/app/list")
    @ResponseBody
    public WVPResult<PageInfo<String>> getAppList(@RequestParam int page,
                                                  @RequestParam int count){
        WVPResult<PageInfo<String>> result = new WVPResult<>();
        List<String> resultData = new ArrayList<>();
        List<File> appList = videoFileService.getAppList();
        for (File file : appList) {
            resultData.add(file.getName());
        }
        result.setCode(0);
        result.setMsg("success");

        PageInfo<String> stringPageInfo = new PageInfo<>(resultData);
        stringPageInfo.startPage(page, count);
        result.setData(stringPageInfo);
        return result;
    }

    /**
     * 获取stream文件夹列表
     * @return
     */
    @GetMapping(value = "/stream/list")
    @ResponseBody
    public WVPResult<PageInfo<String>> getStreamList(@RequestParam int page,
                                                     @RequestParam int count,
                                                     @RequestParam String app ){
        WVPResult<PageInfo<String>> result = new WVPResult<>();
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
        PageInfo<String> stringPageInfo = new PageInfo<>(resultData);
        stringPageInfo.startPage(page, count);
        result.setData(stringPageInfo);
        return result;
    }

    /**
     * 获取日期文件夹列表
     * @return
     */
    @GetMapping(value = "/date/list")
    @ResponseBody
    public WVPResult<List<String>> getDateList( @RequestParam(required = false) Integer year,
                                                @RequestParam(required = false) Integer month,
                                                 @RequestParam String app,
                                                 @RequestParam String stream ){
        WVPResult<List<String>> result = new WVPResult<>();
        List<String> resultData = new ArrayList<>();
        if (app == null) {
            result.setCode(400);
            result.setMsg("app不能为空");
            return result;
        };
        if (stream == null) {
            result.setCode(400);
            result.setMsg("stream不能为空");
            return result;
        }
        List<File> dateList = videoFileService.getDateList(app, stream, year, month);
        for (File file : dateList) {
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
    public WVPResult<PageInfo<String>> getRecordList(@RequestParam int page,
                                                     @RequestParam int count,
                                                     @RequestParam String app,
                                                     @RequestParam String stream,
                                                     @RequestParam String startTime,
                                                     @RequestParam String endTime
    ){

        WVPResult<PageInfo<String>> result = new WVPResult<>();
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
            PageInfo<String> stringPageInfo = new PageInfo<>(recordList);
            stringPageInfo.startPage(page, count);
            result.setData(stringPageInfo);
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
    @GetMapping(value = "/file/download/task/add")
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
        result.setMsg(id!= null?"success":"error： 可能未找到视频文件");
        result.setData(id);
        return result;
    }

    /**
     * 查询视频裁剪合并任务列表
     * @return
     */
    @GetMapping(value = "/file/download/task/list")
    @ResponseBody
    public WVPResult<List<MergeOrCutTaskInfo>> getTaskListForDownload(@RequestParam Boolean isEnd){
        if (isEnd == null) {
            isEnd = false;
        }
        List<MergeOrCutTaskInfo> taskList = videoFileService.getTaskListForDownload(isEnd);
        WVPResult<List<MergeOrCutTaskInfo>> result = new WVPResult<>();
        result.setCode(0);
        result.setMsg(taskList !=  null?"success":"error");
        result.setData(taskList);
        return result;
    }

    /**
     * TODO 中止视频裁剪合并任务列表
     * @return
     */
    @GetMapping(value = "/file/download/task/stop")
    @ResponseBody
    public WVPResult<String> stopTaskForDownload(@RequestParam String taskId){
//        WVPResult<String> result = new WVPResult<>();
//        if (taskId == null) {
//            result.setCode(400);
//            result.setMsg("taskId 不能为空");
//            return result;
//        }
//        boolean stopResult = videoFileService.stopTask(taskId);
//        result.setCode(0);
//        result.setMsg(stopResult ? "success": "fail");
        return null;
    }

    /**
     * 录制完成的通知, 对用zlm的hook
     * @return
     */
    @ResponseBody
    @PostMapping(value = "/on_record_mp4", produces = "application/json;charset=UTF-8")
    public ResponseEntity<String> onRecordMp4(@RequestBody JSONObject json) {
        JSONObject ret = new JSONObject();
        ret.put("code", 0);
        ret.put("msg", "success");
        String file_path = json.getString("file_path");
        logger.debug("ZLM 录制完成，参数：" + file_path);
        if (file_path == null) return new ResponseEntity<String>(ret.toString(), HttpStatus.OK);
        videoFileService.handFile(new File(file_path));

        return new ResponseEntity<String>(ret.toString(), HttpStatus.OK);
    }

    /**
     * 磁盘空间查询
     * @return
     */
    @ResponseBody
    @GetMapping(value = "/space", produces = "application/json;charset=UTF-8")
    public ResponseEntity<String> getSpace() {
        JSONObject ret = new JSONObject();
        ret.put("code", 0);
        ret.put("msg", "success");
        SpaceInfo spaceInfo = videoFileService.getSpaceInfo();
        ret.put("data", JSON.toJSON(spaceInfo));
        return new ResponseEntity<>(ret.toString(), HttpStatus.OK);
    }
}
