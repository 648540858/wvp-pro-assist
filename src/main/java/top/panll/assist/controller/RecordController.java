package top.panll.assist.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.panll.assist.controller.bean.*;
import top.panll.assist.dto.*;
import top.panll.assist.service.VideoFileService;
import top.panll.assist.utils.RedisUtil;

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
     * 添加视频裁剪合并任务
     */
    @Operation(summary ="添加视频裁剪合并任务")
    @Parameter(name = "videoTaskInfo", description = "视频合并任务的信息", required = true)
    @PostMapping(value = "/file/download/task/add")
    @ResponseBody
    public String addTaskForDownload(@RequestBody VideoTaskInfo videoTaskInfo ){
        if (videoTaskInfo.getFilePathList() == null || videoTaskInfo.getFilePathList().isEmpty()) {
            throw new ControllerException(ErrorCode.ERROR100.getCode(), "视频文件列表不可为空");
        }
        String id = videoFileService.mergeOrCut(videoTaskInfo);
        if (id== null) {
            throw new ControllerException(ErrorCode.ERROR100.getCode(), "可能未找到视频文件");
        }
        return id;
    }

    /**
     * 查询视频裁剪合并任务列表
     */
    @Operation(summary ="查询视频裁剪合并任务列表")
    @Parameter(name = "taskId", description = "任务ID", required = true)
    @Parameter(name = "isEnd", description = "是否结束", required = true)
    @GetMapping(value = "/file/download/task/list")
    @ResponseBody
    public List<MergeOrCutTaskInfo> getTaskListForDownload(
            @RequestParam(required = false) String app,
            @RequestParam(required = false) String stream,
            @RequestParam(required = false) String callId,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) Boolean isEnd){
        List<MergeOrCutTaskInfo> taskList = videoFileService.getTaskListForDownload(app, stream, callId, isEnd, taskId);
        if (taskList == null) {
            throw new ControllerException(ErrorCode.ERROR100);
        }
        return taskList;
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
     * 磁盘空间查询
     */
    @Operation(summary ="磁盘空间查询")
    @ResponseBody
    @GetMapping(value = "/space", produces = "application/json;charset=UTF-8")
    public SpaceInfo getSpace() {
        return videoFileService.getSpaceInfo();
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
