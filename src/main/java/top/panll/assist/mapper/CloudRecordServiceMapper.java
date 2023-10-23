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
            " #{timeLen})" +
            " </script>")
    int add(CloudRecordItem cloudRecordItem);


    @Update(" <script>" +
            "update wvp_cloud_record set collect = #{collect} where file_path in " +
            " <foreach collection='cloudRecordItemList'  item='item'  open='(' separator=',' close=')' > #{item.filePath}</foreach>" +
            " </script>")
    void updateCollectList(@Param("collect") boolean collect, List<CloudRecordItem> cloudRecordItemList);

    @Delete(" <script>" +
            "delete from wvp_cloud_record where media_server_id=#{mediaServerId} file_path in " +
            " <foreach collection='filePathList'  item='item'  open='(' separator=',' close=')' > #{item}</foreach>" +
            " </script>")
    void deleteByFileList(List<String> filePathList, @Param("mediaServerId") String mediaServerId);


    @Select(" <script>" +
            "select file_path" +
            " from wvp_cloud_record " +
            " where collect = false and reserve = false " +
            " <if test= 'endTimeStamp != null '> and start_time &lt;= #{endTimeStamp}</if>" +
            " <if test= 'callId != null '> and call_id = #{callId}</if>" +
            " <if test= 'mediaServerId != null  ' > and media_server_id  = #{mediaServerId} </if>" +
            " </script>")
    List<String> queryRecordFilePathListForDelete(@Param("endTimeStamp")Long endTimeStamp, String mediaServerId);

    @Update(" <script>" +
            "update wvp_cloud_record set reserve = #{reserve} where file_path in " +
            " <foreach collection='cloudRecordItems'  item='item'  open='(' separator=',' close=')' > #{item.filePath}</foreach>" +
            " </script>")
    void updateReserveList(@Param("reserve") boolean reserve,List<CloudRecordItem> cloudRecordItems);

    @Update(" <script>" +
            "update wvp_cloud_record set collect = #{collect} where id = #{recordId} " +
            " </script>")
    void changeCollectById(@Param("collect") boolean collect, @Param("recordId") Integer recordId);

    @Update(" <script>" +
            "update wvp_cloud_record set reserve = #{reserve} where id = #{recordId} " +
            " </script>")
    void changeReserveById(@Param("reserve") boolean reserve, Integer recordId);


    @Insert("<script> " +
            "insert into wvp_cloud_record " +
            "(" +
            " app," +
            " stream," +
            " call_id," +
            " start_time," +
            " end_time," +
            " media_server_id," +
            " file_name," +
            " folder," +
            " file_path," +
            " file_size," +
            " time_len " +
            ") " +
            "values " +
            "<foreach collection='cloudRecordItems' index='index' item='item' separator=','> " +
            "(#{item.app}, #{item.stream}, #{item.callId}, #{item.startTime}, " +
            "#{item.endTime}, #{item.mediaServerId}, #{item.fileName},#{item.folder}," +
            "#{item.filePath}, #{item.fileSize}, #{item.timeLen}) " +
            "</foreach> " +
            "</script>")
    int batchAdd(@Param("cloudRecordItems") List<CloudRecordItem> cloudRecordItems);
}
