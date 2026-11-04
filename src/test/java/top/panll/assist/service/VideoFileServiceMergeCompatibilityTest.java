package top.panll.assist.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import top.panll.assist.controller.bean.ControllerException;
import top.panll.assist.dto.MergeOrCutTaskInfo;
import top.panll.assist.dto.UserSettings;
import top.panll.assist.dto.VideoTaskInfo;
import top.panll.assist.utils.RedisUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VideoFileServiceMergeCompatibilityTest {
    @TempDir Path temp;
    VideoFileService service;
    AccurateClipService accurate;
    FFmpegExecUtils merger;
    RedisUtil redis;
    Path output;

    @BeforeEach void setup() {
        service=new VideoFileService();
        accurate=mock(AccurateClipService.class);
        merger=mock(FFmpegExecUtils.class);
        redis=mock(RedisUtil.class);
        UserSettings settings=new UserSettings();
        settings.setId("test");
        output=temp.resolve("output");
        settings.setRecordTempPath(output.toString());
        ReflectionTestUtils.setField(service,"accurateClipService",accurate);
        ReflectionTestUtils.setField(service,"ffmpegExecUtils",merger);
        ReflectionTestUtils.setField(service,"userSettings",settings);
        ReflectionTestUtils.setField(service,"redisUtil",redis);
    }

    @Test void withoutTimesCopiesOrdinaryNamedSingleFileUnchanged() throws Exception {
        Path input=temp.resolve("camera.mp4");
        byte[] bytes={1,2,3,4,5};
        Files.write(input,bytes);
        VideoTaskInfo request=request();
        request.setFilePathList(Collections.singletonList(input.toString()));

        assertNotNull(service.mergeOrCut(request));

        assertArrayEquals(bytes,Files.readAllBytes(output.resolve("camera_call.mp4")));
        ArgumentCaptor<MergeOrCutTaskInfo> task=ArgumentCaptor.forClass(MergeOrCutTaskInfo.class);
        verify(redis).set(anyString(),task.capture());
        assertEquals("1",task.getValue().getPercentage());
        assertEquals("/download/camera_call.mp4",task.getValue().getRecordFile());
        verifyNoInteractions(accurate,merger);
    }

    @Test void withoutTimesPreservesProvidedOrderForOrdinaryNamedFiles() throws Exception {
        Path first=Files.write(temp.resolve("z.mp4"),new byte[]{1});
        Path second=Files.write(temp.resolve("a.mp4"),new byte[]{2});
        VideoTaskInfo request=request();
        request.setFilePathList(Arrays.asList(first.toString(),second.toString()));

        assertNotNull(service.mergeOrCut(request));

        verify(merger).mergeOrCutFile(argThat(files->files.size()==2
                        && files.get(0).toPath().equals(first) && files.get(1).toPath().equals(second)),
                eq(output.toFile()),eq("camera_call"),any());
        verifyNoInteractions(accurate);
    }

    @Test void singleTimeBoundaryRemainsAnExplicitRejectedClipRequest() {
        VideoTaskInfo request=request();
        request.setStartTime("2026-10-13 00:00:01");
        when(accurate.submit(request)).thenThrow(new ControllerException(100,"开始和结束时间必须同时提供"));

        ControllerException error=assertThrows(ControllerException.class,()->service.mergeOrCut(request));

        assertEquals("开始和结束时间必须同时提供",error.getMsg());
        verifyNoInteractions(merger,redis);
    }

    private VideoTaskInfo request() {
        VideoTaskInfo request=new VideoTaskInfo();
        request.setApp("rtp");request.setStream("camera");request.setCallId("call");
        return request;
    }
}
