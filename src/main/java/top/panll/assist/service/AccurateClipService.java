package top.panll.assist.service;

import com.alibaba.fastjson.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.panll.assist.dto.*;
import top.panll.assist.utils.RedisUtil;
import top.panll.assist.controller.bean.ControllerException;
import javax.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Bounded, isolated jobs with frame-accurate re-encoding and observable failures. */
@Service
public class AccurateClipService {
    private final RedisUtil redis;
    private final UserSettings settings;
    private final Path root;
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4),new ThreadPoolExecutor.AbortPolicy());
    public AccurateClipService(RedisUtil redis, UserSettings settings,
            @Value("${userSettings.record-root:/opt/media/bin/www/record}") String root) {
        this.redis=redis; this.settings=settings; this.root=Paths.get(root);
    }
    @PreDestroy public void shutdown() { executor.shutdownNow(); }
    private static String seconds(double value) { return String.format(Locale.ROOT,"%.6f",value); }
    private void save(MergeOrCutTaskInfo task) {
        String key=String.format("%S_%S_%S",AssistConstants.MERGEORCUT,settings.getId(),task.getId());
        if (!redis.set(key,task)) throw new IllegalStateException("无法保存任务状态到 Redis");
    }
    public String submit(VideoTaskInfo request) {
        try {
            Path allowed=root.toRealPath();
            List<ClipPlanner.Source> sources=new ArrayList<>();
            Set<Path> seen=new HashSet<>();
            if (request.getFilePathList()==null || request.getFilePathList().isEmpty())
                throw new IllegalArgumentException("录像列表为空");
            for (String item:request.getFilePathList()) {
                Path file=Paths.get(item).toRealPath();
                if (!file.startsWith(allowed) || !Files.isRegularFile(file) || file.getFileName().toString().startsWith("."))
                    throw new IllegalArgumentException("只能处理录像目录中已完成的文件");
                if (seen.add(file)) sources.add(probe(file));
            }
            List<ClipPlanner.Slice> slices=ClipPlanner.plan(sources,request.getStartTime(),request.getEndTime());
            MergeOrCutTaskInfo task=new MergeOrCutTaskInfo();
            task.setId(UUID.randomUUID().toString().replace("-",""));
            task.setApp(request.getApp()); task.setStream(request.getStream()); task.setCallId(request.getCallId());
            task.setStartTime(request.getStartTime()); task.setEndTime(request.getEndTime());
            task.setCreateTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            task.setPercentage("0"); task.setStatus("QUEUED"); save(task);
            try { executor.execute(()->run(task,slices,request.getRemoteHost())); }
            catch (RejectedExecutionException e) { task.setStatus("FAILED");task.setError("裁剪队列已满，请稍后重试");save(task);throw e; }
            return task.getId();
        } catch (Exception e) {
            throw new ControllerException(100,e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());
        }
    }
    private ClipPlanner.Source probe(Path file) throws Exception {
        JSONObject json=JSON.parseObject(command(Arrays.asList(settings.getFfprobe(),"-v","error","-show_format","-show_streams","-of","json",file.toString()),30));
        double duration=json.getJSONObject("format").getDoubleValue("duration");
        if (!Double.isFinite(duration) || duration<=0) throw new IllegalArgumentException("录像时长无效: "+file.getFileName());
        int width=0,height=0;boolean audio=false;
        for (Object obj:json.getJSONArray("streams")) {
            JSONObject stream=(JSONObject)obj;
            if ("video".equals(stream.getString("codec_type")) && width==0) {width=stream.getIntValue("width");height=stream.getIntValue("height");}
            if ("audio".equals(stream.getString("codec_type"))) audio=true;
        }
        if (width<=0 || height<=0) throw new IllegalArgumentException("录像没有视频轨道");
        return new ClipPlanner.Source(file,ClipPlanner.start(file),duration,width,height,audio);
    }
    private void run(MergeOrCutTaskInfo task,List<ClipPlanner.Slice> slices,String remoteHost) {
        Path job=null,output=null;
        try {
            Path directory=Paths.get(settings.getRecordTempPath());Files.createDirectories(directory);
            job=Files.createTempDirectory(directory,".clip-"+task.getId()+"-");
            task.setStatus("RUNNING");save(task);
            List<String> entries=new ArrayList<>();
            for (int i=0;i<slices.size();i++) {
                ClipPlanner.Slice s=slices.get(i);Path part=job.resolve("part-"+i+".mp4");
                List<String> args=new ArrayList<>(Arrays.asList(settings.getFfmpeg(),"-nostdin","-hide_banner","-loglevel","error","-y",
                        "-threads",String.valueOf(settings.getThreads()),"-ss",seconds(s.offset),"-i",s.source.path.toString(),"-t",seconds(s.duration),
                        "-map","0:v:0","-map","0:a:0?","-c:v","libx264","-preset","veryfast","-crf","20","-pix_fmt","yuv420p",
                        "-threads",String.valueOf(settings.getThreads()),"-c:a","aac","-movflags","+faststart",part.toString()));
                command(args,600);
                entries.add("file '"+part.toAbsolutePath().toString().replace("'","'\\''")+"'");
                task.setPercentage(String.valueOf(0.9*(i+1)/slices.size()));save(task);
            }
            Path result=job.resolve("result.mp4");
            if (slices.size()==1) Files.move(job.resolve("part-0.mp4"),result);
            else {
                Path list=job.resolve("concat.txt");Files.write(list,entries,StandardCharsets.UTF_8);
                command(Arrays.asList(settings.getFfmpeg(),"-nostdin","-hide_banner","-loglevel","error","-y","-f","concat","-safe","0","-i",list.toString(),
                        "-c","copy","-movflags","+faststart",result.toString()),600);
            }
            JSONObject info=JSON.parseObject(command(Arrays.asList(settings.getFfprobe(),"-v","error","-show_entries","format=duration","-of","json",result.toString()),30));
            double actual=info.getJSONObject("format").getDoubleValue("duration");
            double expected=slices.stream().mapToDouble(x->x.duration).sum();
            if (!Double.isFinite(actual) || Math.abs(actual-expected)>0.15) throw new IllegalStateException("输出时长校验失败: expected="+expected+", actual="+actual);
            output=directory.resolve(task.getId()+".mp4");Files.move(result,output,StandardCopyOption.ATOMIC_MOVE);
            task.setRecordFile("/download/"+output.getFileName());
            if (remoteHost!=null) {
                String base=remoteHost.replaceAll("/+$","");
                task.setDownloadFile(base+"/download.html?url=download/"+output.getFileName());
                task.setPlayFile(base+task.getRecordFile());
            }
            task.setPercentage("1");task.setStatus("SUCCESS");save(task);
        } catch (Exception e) {
            task.setStatus("FAILED");task.setError(e.getMessage());task.setPercentage("0");
            if(output!=null)try{Files.deleteIfExists(output);}catch(IOException ignored){}
            save(task);
        } finally {
            if(job!=null)try(java.util.stream.Stream<Path> paths=Files.walk(job)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path->{try{Files.deleteIfExists(path);}catch(IOException ignored){}});
            }catch(IOException ignored){}
        }
    }
    private static String command(List<String> args,int timeout) throws Exception {
        Path log=Files.createTempFile("assist-command-",".log");Process process=null;
        try {
            process=new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if(!process.waitFor(timeout,TimeUnit.SECONDS))throw new IOException("媒体处理超时");
            String text=new String(Files.readAllBytes(log),StandardCharsets.UTF_8);
            if(process.exitValue()!=0)throw new IOException("媒体处理失败: "+text.substring(0,Math.min(text.length(),1500)));
            return text;
        } finally {
            if(process!=null && process.isAlive()){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}
            Files.deleteIfExists(log);
        }
    }
}
