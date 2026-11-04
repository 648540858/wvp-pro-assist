package top.panll.assist.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import top.panll.assist.dto.*;
import top.panll.assist.utils.RedisUtil;
import top.panll.assist.controller.bean.ControllerException;
import com.alibaba.fastjson.JSON;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccurateClipServiceTest {
    @TempDir Path temp;
    AccurateClipService service;
    UserSettings settings;
    Path red,blue;
    Map<String,MergeOrCutTaskInfo> tasks=new ConcurrentHashMap<>();
    String ffmpeg=System.getenv().getOrDefault("TEST_FFMPEG","/usr/bin/ffmpeg");
    String ffprobe=System.getenv().getOrDefault("TEST_FFPROBE","/usr/bin/ffprobe");
    @BeforeEach void setup() throws Exception {
        Path root=Files.createDirectories(temp.resolve("record/2026-10-13"));
        red=root.resolve("2026-10-13-00-00-00-0.mp4");blue=root.resolve("2026-10-13-00-00-04-1.mp4");
        generate(red,"red");generate(blue,"blue");
        settings=new UserSettings();settings.setId("test");settings.setThreads(1);settings.setFfmpeg(ffmpeg);settings.setFfprobe(ffprobe);settings.setRecordTempPath(temp.resolve("output").toString());
        RedisUtil redis=mock(RedisUtil.class);
        when(redis.set(anyString(),any())).thenAnswer(inv->{MergeOrCutTaskInfo t=inv.getArgument(1);tasks.put(t.getId(),JSON.parseObject(JSON.toJSONString(t),MergeOrCutTaskInfo.class));return true;});
        service=new AccurateClipService(redis,settings,temp.resolve("record").toString());
    }
    @AfterEach void close(){service.shutdown();}
    private byte[] command(String... args) throws Exception {
        Process p=new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        byte[] data=read(p.getInputStream());assertTrue(p.waitFor(30,TimeUnit.SECONDS));assertEquals(0,p.exitValue());return data;
    }
    private byte[] read(java.io.InputStream in) throws Exception {java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}
    private void generate(Path file,String color) throws Exception {
        command(ffmpeg,"-v","error","-f","lavfi","-i","color=c="+color+":s=160x120:r=25:d=4","-f","lavfi","-i","sine=frequency=440:duration=4","-c:v","libx264","-c:a","aac","-pix_fmt","yuv420p","-shortest",file.toString());
    }
    private VideoTaskInfo request(String start,String end,String callId) {
        VideoTaskInfo r=new VideoTaskInfo();r.setApp("rtp");r.setStream("camera");r.setCallId(callId);
        r.setStartTime("2026-10-13 00:00:"+start);r.setEndTime("2026-10-13 00:00:"+end);
        r.setFilePathList(Arrays.asList(blue.toString(),red.toString()));return r;
    }
    private Path await(String id) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        while(System.nanoTime()<deadline) {
            MergeOrCutTaskInfo t=tasks.get(id);
            if("FAILED".equals(t.getStatus()))fail(t.getError());
            if("SUCCESS".equals(t.getStatus()))return temp.resolve("output/"+id+".mp4");
            Thread.sleep(30);
        }
        throw new AssertionError("job timeout");
    }
    private double duration(Path file) throws Exception {
        return Double.parseDouble(new String(command(ffprobe,"-v","error","-show_entries","format=duration","-of","default=nw=1:nk=1",file.toString())).trim());
    }
    private void color(Path file,String seek,boolean redExpected) throws Exception {
        byte[] rgb=command(ffmpeg,"-v","error","-ss",seek,"-i",file.toString(),"-frames:v","1","-vf","scale=1:1","-pix_fmt","rgb24","-f","rawvideo","-");
        assertTrue(rgb.length>=3);assertTrue((rgb[redExpected?0:2]&255)>180);assertTrue((rgb[redExpected?2:0]&255)<50);
    }
    @Test void cutsActualFramesAcrossFilesAndKeepsRepeatedJobsSeparate() throws Exception {
        String a=service.submit(request("03","05","same-call"));String b=service.submit(request("05","07","same-call"));
        assertNotEquals(a,b);Path cross=await(a),single=await(b);
        assertEquals(2,duration(cross),.15);assertEquals(2,duration(single),.15);
        color(cross,"0.2",true);color(cross,"1.2",false);color(single,"0.2",false);
        command(ffmpeg,"-v","error","-i",cross.toString(),"-f","null","-");
        try(java.util.stream.Stream<Path> p=Files.list(temp.resolve("output"))){assertEquals(2,p.count());}
    }
    @Test void rejectsOutOfRootFilesAndUncoveredTimes() {
        VideoTaskInfo r=request("03","05","bad");r.setFilePathList(Collections.singletonList(temp.resolve("outside.mp4").toString()));
        assertThrows(ControllerException.class,()->service.submit(r));
        assertThrows(ControllerException.class,()->service.submit(request("07","10","missing")));
    }
    @Test void persistsEncoderFailure() throws Exception {
        settings.setFfmpeg("/missing-ffmpeg");String id=service.submit(request("01","02","failure"));
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!"FAILED".equals(tasks.get(id).getStatus()) && System.nanoTime()<deadline)Thread.sleep(30);
        assertEquals("FAILED",tasks.get(id).getStatus());assertNotNull(tasks.get(id).getError());
    }
}
