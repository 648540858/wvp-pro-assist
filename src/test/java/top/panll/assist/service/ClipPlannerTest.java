package top.panll.assist.service;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClipPlannerTest {
    private ClipPlanner.Source source(String file,double duration) {
        Path p=Paths.get("/record/2026-10-13/"+file);
        return new ClipPlanner.Source(p,ClipPlanner.start(p),duration,160,120,false);
    }
    @Test void readsBothZlmNames() {
        assertEquals(ClipPlanner.start(Paths.get("/2026-10-13/2026-10-13-00-03-05-0.mp4")),ClipPlanner.start(Paths.get("/2026-10-13/00-03-05-0.mp4")));
        assertThrows(IllegalArgumentException.class,()->ClipPlanner.start(Paths.get("/2026-10-13/nonsense.mp4")));
    }
    @Test void sortsAndTrimsOverlappingSources() {
        List<ClipPlanner.Slice> p=ClipPlanner.plan(Arrays.asList(source("00-00-04-1.mp4",4),source("00-00-00-0.mp4",4.2)),"2026-10-13 00:00:03","2026-10-13 00:00:05");
        assertEquals(2,p.size());assertEquals(3,p.get(0).offset,1e-6);
        assertEquals(.2,p.get(1).offset,1e-6);assertEquals(2,p.stream().mapToDouble(s->s.duration).sum(),1e-6);
    }
    @Test void rejectsGapsMissingBoundariesAndInvertedRanges() {
        List<ClipPlanner.Source> p=Arrays.asList(source("00-00-00-0.mp4",4),source("00-00-05-1.mp4",4));
        assertThrows(IllegalArgumentException.class,()->ClipPlanner.plan(p,"2026-10-13 00:00:03","2026-10-13 00:00:06"));
        assertThrows(IllegalArgumentException.class,()->ClipPlanner.plan(p,"2026-10-13 00:00:08","2026-10-13 00:00:10"));
        assertThrows(IllegalArgumentException.class,()->ClipPlanner.plan(p,"2026-10-13 00:00:02","2026-10-13 00:00:01"));
        assertThrows(IllegalArgumentException.class,()->ClipPlanner.plan(p,"2026-10-13 00:00:02",null));
    }
}
