package top.panll.assist.mapper;

import org.apache.ibatis.annotations.*;
import top.panll.assist.dto.CloudRecordItem;

import java.util.List;

@Mapper
public interface CloudRecordServiceMapper {

    @Insert(" <script>" +
            "INSERT INTO wvp_cloud_record (" +
            " app," +
            " stream," +
            "<if test=\"callId != null\"> call_id,</if>" +
            " start_time," +
            " end_time," +
            " media_server_id," +
            " file_name," +
            " folder," +
            " file_path," +
            " file_size," +
            " collect," +
            " time_len ) " +
            "VALUES (" +
            " #{app}," +
            " #{stream}," +
            " <if test=\"callId != null\"> #{callId},</if>" +
            " #{startTime}," +
            " #{endTime}," +
            " #{mediaServerId}," +
            " #{fileName}," +
            " #{folder}," +
            " #{filePath}," +
            " #{fileSize}," +
            " #{collect}," +
            " #{timeLen})" +
            " </script>")
    int add(CloudRecordItem cloudRecordItem);

    @Select(" <script>" +
            "select *" +
            " from wvp_cloud_record " +
            " where app = #{app} and stream = #{stream} and call_id = #{callId} and file_path=#{filePath}" +
            " </script>")
    CloudRecordItem query(@Param("app") String app,
                          @Param("stream") String stream,
                          @Param("callId") String callId,
                          @Param("filePath") String filePath);
}
