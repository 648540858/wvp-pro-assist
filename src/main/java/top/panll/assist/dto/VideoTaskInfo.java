package top.panll.assist.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "视频合并任务的信息")
public class VideoTaskInfo {

    @Schema(description = "视频文件路径列表")
    private List<String> filePathList;

    @Schema(description = "返回地址时的远程地址")
    private String remoteHost;

    public List<String> getFilePathList() {
        return filePathList;
    }

    public void setFilePathList(List<String> filePathList) {
        this.filePathList = filePathList;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }
}
