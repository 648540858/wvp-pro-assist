package top.panll.assist.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.panll.assist.controller.bean.ControllerException;
import top.panll.assist.controller.bean.ErrorCode;
import top.panll.assist.controller.bean.RecordFile;
import top.panll.assist.controller.bean.WVPResult;
import top.panll.assist.dto.*;
import top.panll.assist.service.VideoFileService;
import top.panll.assist.utils.PageInfo;
import top.panll.assist.utils.RedisUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Tag(name = "录像管理", description = "录像管理")
@CrossOrigin
@RestController
@RequestMapping("/api/record")
public class RecordController {

    private final static Logger logger = LoggerFactory.getLogger(RecordController.class);

    @Autowired
    private VideoFileService videoFileService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private UserSettings userSettings;

    private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    /**
     * 获取Assist服务配置信息
     */
    @Operation(summary ="获取Assist服务配置信息")
    @GetMapping(value = "/info")
    @ResponseBody
    public UserSettings getInfo(){
        return userSettings;
    }

    /**
     * 获取app+stream列表
     * @return
     */
    @Operation(summary ="分页获取app+stream的列表")
    @Parameter(name = "page", description = "当前页", required = true)
    @Parameter(name = "count", description = "每页查询数量", required = true)
    @GetMapping(value = "/list")
    @ResponseBody
    public PageInfo<Map<String, String>> getList(@RequestParam int page,
                                                 @RequestParam int count){
        List<Map<String, String>> appList = videoFileService.getList();

        PageInfo<Map<String, String>> stringPageInfo = new PageInfo<>(appList);
        stringPageInfo.startPage(page, count);
        return stringPageInfo;
    }

    /**
     * 分页获取app列表
     * @return
     */
    @Operation(summary ="分页获取app列表")
    @Parameter(name = "page", description = "当前页", required = true)
    @Parameter(name = "count", description = "每页查询数量", required = true)
    @GetMapping(value = "/app/list")
    @ResponseBody
    public PageInfo<String> getAppList(@RequestParam int page,
                                                  @RequestParam int count){
        List<String> resultData = new ArrayList<>();
        List<File> appList = videoFileService.getAppList(true);
        if (appList.size() > 0) {
            for (File file : appList) {
                resultData.add(file.getName());
            }
        }
        Collections.sort(resultData);

        PageInfo<String> stringPageInfo = new PageInfo<>(resultData);
        stringPageInfo.startPage(page, count);
        return stringPageInfo;
    }

    /**
     * 分页stream列表
     * @return
     */
    @Operation(summary ="分页stream列表")
    @Parameter(name = "page", description = "当前页", required = true)
    @Parameter(name = "count", description = "每页查询数量", required = true)
    @Parameter(name = "app", description = "应用名", required = true)
    @GetMapping(value = "/stream/list")
    @ResponseBody
    public PageInfo<String> getStreamList(@RequestParam int page,
                                                     @RequestParam int count,
                                                     @RequestParam String app ){
        List<String> resultData = new ArrayList<>();
        if (app == null) {
            throw new ControllerException(ErrorCode.ERROR400.getCode(), "app不能为空");
        }
        List<File> streamList = videoFileService.getStreamList(app, true);
        if (streamList != null) {
            for (File file : streamList) {
                resultData.add(file.getName());
            }
        }
        PageInfo<String> stringPageInfo = new PageInfo<>(resultData);
        stringPageInfo.startPage(page, count);
        return stringPageInfo;
    }

    /**
     * 获取日期文件夹列表
     * @return
     */
    @Operation(summary ="获取日期文件夹列表")
    @Parameter(name = "year", description = "月", required = true)
    @Parameter(name = "month", description = "年", required = true)
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @GetMapping(value = "/date/list")
    @ResponseBody
    public List<String> getDateList( @RequestParam(required = false) Integer year,
                                                @RequestParam(required = false) Integer month,
                                                 @RequestParam String app,
                                                 @RequestParam String stream ){
        List<String> resultData = new ArrayList<>();
        if (app == null) {
            throw new ControllerException(ErrorCode.ERROR400.getCode(), "app不能为空");
        };
        if (stream == null) {
            throw new ControllerException(ErrorCode.ERROR400.getCode(), "stream不能为空");
        }
        List<File> dateList = videoFileService.getDateList(app, stream, year, month, true);
        for (File file : dateList) {
            resultData.add(file.getName());
        }
        return resultData;
    }

    /**
     * 获取视频文件列表
     * @return
     */
    @Operation(summary ="获取视频文件列表")
    @Parameter(name = "page", description = "当前页", required = true)
    @Parameter(name = "count", description = "每页查询数量", required = true)
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @Parameter(name = "startTime", description = "开始时间(yyyy-MM-dd HH:mm:ss)", required = true)
    @Parameter(name = "endTime", description = "结束时间(yyyy-MM-dd HH:mm:ss)", required = true)
    @GetMapping(value = "/file/list")
    @ResponseBody
    public PageInfo<String> getRecordList(@RequestParam int page,
                                                     @RequestParam int count,
                                                     @RequestParam String app,
                                                     @RequestParam String stream,
                                                     @RequestParam(required = false) String startTime,
                                                     @RequestParam(required = false) String endTime
    ){

        // 开始时间与结束时间可不传或只传其一
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
            PageInfo<String> stringPageInfo = new PageInfo<>(recordList);
            stringPageInfo.startPage(page, count);
            return stringPageInfo;
        } catch (ParseException e) {
            logger.error("错误的开始时间[{}]或结束时间[{}]", startTime, endTime);
            throw new ControllerException(ErrorCode.ERROR400.getCode(), "错误的开始时间或结束时间, e=" + e.getMessage());
        }
    }

    /**
     * 获取视频文件列表
     * @return
     */
    @Operation(summary ="获取视频文件列表")
    @Parameter(name = "page", description = "当前页", required = true)
    @Parameter(name = "count", description = "每页查询数量", required = true)
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @Parameter(name = "startTime", description = "开始时间(yyyy-MM-dd HH:mm:ss)", required = true)
    @Parameter(name = "endTime", description = "结束时间(yyyy-MM-dd HH:mm:ss)", required = true)
    @GetMapping(value = "/file/listWithDate")
    @ResponseBody
    public PageInfo<RecordFile> getRecordListWithDate(@RequestParam int page,
                                                      @RequestParam int count,
                                                      @RequestParam String app,
                                                      @RequestParam String stream,
                                                      @RequestParam(required = false) String startTime,
                                                      @RequestParam(required = false) String endTime
    ){

        // 开始时间与结束时间可不传或只传其一
        List<RecordFile> recordList = new ArrayList<>();
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
                    recordList.add(RecordFile.instance(app, stream, file.getName(), file.getParentFile().getName()));
                }
            }
            PageInfo<RecordFile> stringPageInfo = new PageInfo<>(recordList);
            stringPageInfo.startPage(page, count);
            return stringPageInfo;
        } catch (ParseException e) {
            logger.error("错误的开始时间[{}]或结束时间[{}]", startTime, endTime);
            throw new ControllerException(ErrorCode.ERROR400.getCode(), "错误的开始时间或结束时间, e=" + e.getMessage());
        }
    }


    /**
     * 添加视频裁剪合并任务
     */
    @Operation(summary ="添加视频裁剪合并任务")
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @Parameter(name = "startTime", description = "开始时间(yyyy-MM-dd HH:mm:ss)", required = true)
    @Parameter(name = "endTime", description = "结束时间(yyyy-MM-dd HH:mm:ss)", required = true)
    @Parameter(name = "remoteHost", description = "服务的IP：端口（用于直接返回完整播放地址以及下载地址）", required = true)
    @GetMapping(value = "/file/download/task/add")
    @ResponseBody
    public String addTaskForDownload(@RequestParam String app,
                                                @RequestParam String stream,
                                                @RequestParam(required = false) String startTime,
                                                @RequestParam(required = false) String endTime,
                                                @RequestParam(required = false) String remoteHost
    ){
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
        String id = videoFileService.mergeOrCut(app, stream, startTimeDate, endTimeDate, remoteHost);
        if (id== null) {
            throw new ControllerException(ErrorCode.ERROR100.getCode(), "可能未找到视频文件");
        }
        return id;
    }

    /**
     * 查询视频裁剪合并任务列表
     */
    @Operation(summary ="查询视频裁剪合并任务列表")
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @Parameter(name = "taskId", description = "任务ID", required = true)
    @Parameter(name = "isEnd", description = "是否结束", required = true)
    @GetMapping(value = "/file/download/task/list")
    @ResponseBody
    public List<MergeOrCutTaskInfo> getTaskListForDownload(
            @RequestParam(required = false) String app,
            @RequestParam(required = false) String stream,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) Boolean isEnd){
        List<MergeOrCutTaskInfo> taskList = videoFileService.getTaskListForDownload(isEnd, app, stream, taskId);
        if (taskList == null) {
            throw new ControllerException(ErrorCode.ERROR100);
        }
        return taskList;
    }

    /**
     * 收藏录像（被收藏的录像不会被清理任务清理）
     */
    @Operation(summary ="收藏录像（被收藏的录像不会被清理任务清理）")
    @Parameter(name = "type", description = "类型", required = true)
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @GetMapping(value = "/file/collection/add")
    @ResponseBody
    public void collection(
            @RequestParam(required = true) String type,
            @RequestParam(required = true) String app,
            @RequestParam(required = true) String stream){

        boolean collectionResult = videoFileService.collection(app, stream, type);
        if (!collectionResult) {
            throw new ControllerException(ErrorCode.ERROR100);
        }
    }

    /**
     * 移除收藏录像
     */
    @Operation(summary ="移除收藏录像")
    @Parameter(name = "type", description = "类型", required = true)
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @GetMapping(value = "/file/collection/remove")
    @ResponseBody
    public void removeCollection(
            @RequestParam(required = true) String type,
            @RequestParam(required = true) String app,
            @RequestParam(required = true) String stream){

        boolean collectionResult = videoFileService.removeCollection(app, stream, type);
        if (!collectionResult) {
            throw new ControllerException(ErrorCode.ERROR100);
        }
    }

    /**
     * 收藏录像列表
     */
    @Operation(summary ="收藏录像列表")
    @Parameter(name = "type", description = "类型", required = false)
    @Parameter(name = "app", description = "应用名", required = false)
    @Parameter(name = "stream", description = "流ID", required = false)
    @GetMapping(value = "/file/collection/list")
    @ResponseBody
    public List<SignInfo> collectionList(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String app,
            @RequestParam(required = false) String stream){

        List<SignInfo> signInfos = videoFileService.getCollectionList(app, stream, type);
        return signInfos;
    }

    /**
     * 中止视频裁剪合并任务列表
     */
    @Operation(summary ="中止视频裁剪合并任务列表(暂不支持)")
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

        String app = json.getString("app");
        String stream = json.getString("stream");
        logger.debug("ZLM 录制完成，文件路径：" + file_path);

        if (file_path == null) {
            return new ResponseEntity<String>(ret.toString(), HttpStatus.OK);
        }
        if (userSettings.getRecordDay() <= 0) {
            logger.info("录像保存事件为{}天，直接删除: {}", userSettings.getRecordDay(), file_path);
            FileUtils.deleteQuietly(new File(file_path));
        }else {
            videoFileService.handFile(new File(file_path), app, stream);
        }

        return new ResponseEntity<String>(ret.toString(), HttpStatus.OK);
    }

    /**
     * 磁盘空间查询
     */
    @Operation(summary ="磁盘空间查询")
    @ResponseBody
    @GetMapping(value = "/space", produces = "application/json;charset=UTF-8")
    public SpaceInfo getSpace() {
        return videoFileService.getSpaceInfo();
    }

    /**
     * 增加推流的鉴权信息，用于录像存储使用
     */
    @Operation(summary ="增加推流的鉴权信息")
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @Parameter(name = "callId", description = "录像自鉴权ID", required = true)
    @ResponseBody
    @GetMapping(value = "/addStreamCallInfo", produces = "application/json;charset=UTF-8")
    @PostMapping(value = "/addStreamCallInfo", produces = "application/json;charset=UTF-8")
    public void addStreamCallInfo(String app, String stream, String callId) {
        String key = AssistConstants.STREAM_CALL_INFO + userSettings.getId() + "_" + app + "_" + stream;
        redisUtil.set(key, callId, -1);
    }

    /**
     * 录像文件的时长
     */
    @Operation(summary ="录像文件的时长")
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @Parameter(name = "recordIng", description = "是否录制中", required = true)
    @ResponseBody
    @GetMapping(value = "/file/duration", produces = "application/json;charset=UTF-8")
    @PostMapping(value = "/file/duration", produces = "application/json;charset=UTF-8")
    public long fileDuration( @RequestParam String app, @RequestParam String stream) {
        return videoFileService.fileDuration(app, stream);
    }
}
