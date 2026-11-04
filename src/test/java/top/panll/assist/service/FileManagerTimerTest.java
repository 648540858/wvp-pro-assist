package top.panll.assist.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import top.panll.assist.dto.MergeOrCutTaskInfo;
import top.panll.assist.dto.UserSettings;
import top.panll.assist.utils.RedisUtil;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FileManagerTimerTest {
    @TempDir Path temp;

    @Test void onlyDeletesTasksOlderThanRetentionPeriod() {
        RedisUtil redis = mock(RedisUtil.class);
        UserSettings settings = new UserSettings();
        settings.setId("test");
        settings.setRecordTempDay(7);
        settings.setRecordTempPath(temp.toString());
        FileManagerTimer timer = new FileManagerTimer();
        ReflectionTestUtils.setField(timer, "userSettings", settings);
        ReflectionTestUtils.setField(timer, "redisUtil", redis);
        when(redis.scan(any())).thenReturn(Arrays.asList("recent", "expired", "undated"));
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long now = System.currentTimeMillis();
        when(redis.get("recent")).thenReturn(task(format.format(new Date(now - TimeUnit.DAYS.toMillis(1)))));
        when(redis.get("expired")).thenReturn(task(format.format(new Date(now - TimeUnit.DAYS.toMillis(8)))));
        when(redis.get("undated")).thenReturn(task(""));

        timer.execute();

        verify(redis).del("expired");
        verify(redis, never()).del("recent");
        verify(redis, never()).del("undated");
    }

    private MergeOrCutTaskInfo task(String createdAt) {
        MergeOrCutTaskInfo task = new MergeOrCutTaskInfo();
        task.setCreateTime(createdAt);
        return task;
    }
}
