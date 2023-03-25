package top.panll.assist.utils;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

public class DateUtils {

    public static final String PATTERNForDateTime = "yyyy-MM-dd HH:mm:ss";

    public static final String PATTERNForDate = "yyyy-MM-dd";

    public static final String zoneStr = "Asia/Shanghai";



    // 获得某天最大时间 2020-02-19 23:59:59
    public static Date getEndOfDay(Date date) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault());;
        LocalDateTime endOfDay = localDateTime.with(LocalTime.MAX);
        return Date.from(endOfDay.atZone(ZoneId.systemDefault()).toInstant());
    }

    // 获得某天最小时间 2020-02-17 00:00:00
    public static Date getStartOfDay(Date date) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault());
        LocalDateTime startOfDay = localDateTime.with(LocalTime.MIN);
        return Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant());
    }

    public static String getDateStr(Date date) {
        SimpleDateFormat formatter =  new SimpleDateFormat(PATTERNForDate);
        return formatter.format(date);
    }

    public static String getDateTimeStr(Date date) {
        SimpleDateFormat formatter =  new SimpleDateFormat(PATTERNForDateTime);
        return formatter.format(date);
    }

}
