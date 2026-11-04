package top.panll.assist.service;

import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

/** Converts wall-clock ranges into ordered file offsets; never hides missing footage. */
public final class ClipPlanner {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern MODERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})-\\d+\\.mp4$");
    private static final Pattern LEGACY = Pattern.compile("^(\\d{2})-(\\d{2})-(\\d{2})(?:-\\d+)?\\.mp4$");
    // One or two media frames of rounding are tolerated, but real gaps fail explicitly.
    static final double TOLERANCE = 0.1;

    public static final class Source {
        final Path path;
        final double start, duration;
        final int width, height;
        final boolean audio;
        Source(Path path, double start, double duration, int width, int height, boolean audio) {
            this.path=path; this.start=start; this.duration=duration;
            this.width=width; this.height=height; this.audio=audio;
        }
    }
    public static final class Slice {
        final Source source;
        final double offset, duration;
        Slice(Source source, double offset, double duration) {
            this.source=source; this.offset=offset; this.duration=duration;
        }
    }
    public static double time(String value) {
        return LocalDateTime.parse(value, DATE).atZone(ZONE).toInstant().toEpochMilli()/1000.0;
    }
    public static double start(Path file) {
        String name=file.getFileName().toString();
        Matcher m=MODERN.matcher(name);
        if (m.matches()) return time(m.group(1)+" "+m.group(2)+":"+m.group(3)+":"+m.group(4));
        m=LEGACY.matcher(name);
        if (m.matches()) return time(file.getParent().getFileName()+" "+m.group(1)+":"+m.group(2)+":"+m.group(3));
        throw new IllegalArgumentException("不支持的录像文件名: "+name);
    }
    public static List<Slice> plan(List<Source> input, String start, String end) {
        if ((start==null)!=(end==null)) throw new IllegalArgumentException("开始和结束时间必须同时提供");
        List<Source> sources=new ArrayList<>(input);
        sources.sort(Comparator.comparingDouble(x->x.start));
        if (sources.isEmpty()) throw new IllegalArgumentException("没有可用录像");
        boolean bounded=start!=null;
        double from=bounded?time(start):sources.get(0).start;
        double to=bounded?time(end):sources.stream().mapToDouble(x->x.start+x.duration).max().getAsDouble();
        if (to<=from) throw new IllegalArgumentException("结束时间必须晚于开始时间");
        List<Slice> result=new ArrayList<>();
        double cursor=from;
        for (Source source:sources) {
            double a=Math.max(cursor,source.start), b=Math.min(to,source.start+source.duration);
            if (b<=a) continue;
            if (bounded && a-cursor>TOLERANCE) throw new IllegalArgumentException(String.format(Locale.ROOT,"目标时间存在录像缺口 %.3f 秒",a-cursor));
            result.add(new Slice(source,Math.max(0,a-source.start),b-a));
            cursor=b;
            if (cursor>=to) break;
        }
        if (result.isEmpty() || (bounded && to-cursor>TOLERANCE)) throw new IllegalArgumentException("录像未完整覆盖目标时间段");
        Source first=result.get(0).source;
        for (Slice slice:result) {
            Source s=slice.source;
            if (s.width!=first.width || s.height!=first.height || s.audio!=first.audio)
                throw new IllegalArgumentException("所选录像的分辨率或音轨不一致");
        }
        return result;
    }
    private ClipPlanner() {}
}
