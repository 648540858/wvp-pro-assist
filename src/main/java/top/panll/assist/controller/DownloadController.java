package top.panll.assist.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import top.panll.assist.dto.UserSettings;
import top.panll.assist.utils.PageInfo;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/download")
public class DownloadController {

    private final static Logger logger = LoggerFactory.getLogger(DownloadController.class);

    @Autowired
    private UserSettings userSettings;

    /**
     * 获取app+stream列表
     *
     * @return
     */
    @GetMapping(value = "/**")
    @ResponseBody
    public void download(HttpServletRequest request, HttpServletResponse response) {

        String resourcePath = request.getServletPath();
        System.out.println(resourcePath);
        resourcePath = resourcePath.substring("/download".length() + 1, resourcePath.length());
        String record = userSettings.getRecord();
//        if (record.endsWith("/")) {
//            record = record.substring(0, record.length() - 1);
//            System.out.println(record);
//        }
        System.out.println(record + resourcePath);
        File file = new File(record + resourcePath);
        if (!file.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        /**
         * 参考实现来自： CSDN 进修的CODER SpringBoot Java实现Http方式分片下载断点续传+实现H5大视频渐进式播放
         * https://blog.csdn.net/lovequanquqn/article/details/104562945
         */
        String range = request.getHeader("Range");
        logger.info("current request rang:" + range);
        //开始下载位置
        long startByte = 0;
        //结束下载位置
        long endByte = file.length() - 1;
        logger.info("文件开始位置：{}，文件结束位置：{}，文件总长度：{}", startByte, endByte, file.length());

        //有range的话
        if (range != null && range.contains("bytes=") && range.contains("-")) {
            range = range.substring(range.lastIndexOf("=") + 1).trim();
            String[] ranges = range.split("-");
            try {
                //判断range的类型
                if (ranges.length == 1) {
                    // 类型一：bytes=-2343,
                    if (range.startsWith("-")) {
                        endByte = Long.parseLong(ranges[0]);
                    }
                    //类型二：bytes=2343-
                    else if (range.endsWith("-")) {
                        startByte = Long.parseLong(ranges[0]);
                    }
                }
                //类型三：bytes=22-2343
                else if (ranges.length == 2) {
                    startByte = Long.parseLong(ranges[0]);
                    endByte = Long.parseLong(ranges[1]);
                }

            } catch (NumberFormatException e) {
                startByte = 0;
                endByte = file.length() - 1;
                logger.error("Range Occur Error,Message:{}", e.getLocalizedMessage());
            }


        }

        // 要下载的长度
        long contentLength = endByte - startByte + 1;
        // 文件名
        String fileName = file.getName();
        // 文件类型
        String contentType = request.getServletContext().getMimeType(fileName);

        // 解决下载文件时文件名乱码问题
        byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        fileName = new String(fileNameBytes, 0, fileNameBytes.length, StandardCharsets.ISO_8859_1);

        response.setHeader("Content-Type", contentType);
        response.setHeader("Content-Length", String.valueOf(contentLength));
        //inline表示浏览器直接使用，attachment表示下载，fileName表示下载的文件名
        response.setHeader("Content-Disposition", "inline;filename=" + fileName);
        response.setContentType(contentType);
        if (range != null) {
            //各种响应头设置
            //支持断点续传，获取部分字节内容：
            response.setHeader("Accept-Ranges", "bytes");
            //http状态码要为206：表示获取部分内容
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            // Content-Range，格式为：[要下载的开始位置]-[结束位置]/[文件总大小]
            response.setHeader("Content-Range", "bytes " + startByte + "-" + endByte + "/" + file.length());
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
        }


        BufferedOutputStream outputStream = null;
        RandomAccessFile randomAccessFile = null;
        //已传送数据大小
        long transmitted = 0;
        try {
            randomAccessFile = new RandomAccessFile(file, "r");

            outputStream = new BufferedOutputStream(response.getOutputStream());
            byte[] buff = new byte[4096];
            int len = 0;
            randomAccessFile.seek(startByte);
            //warning：判断是否到了最后不足4096（buff的length）个byte这个逻辑（(transmitted + len) <= contentLength）要放前面
            //不然会会先读取randomAccessFile，造成后面读取位置出错;
            while ((transmitted + len) <= contentLength && (len = randomAccessFile.read(buff)) != -1) {
                outputStream.write(buff, 0, len);
                transmitted += len;
            }
            //处理不足buff.length部分
            if (transmitted < contentLength) {
                len = randomAccessFile.read(buff, 0, (int) (contentLength - transmitted));
                outputStream.write(buff, 0, len);
                transmitted += len;
            }

            outputStream.flush();
            response.flushBuffer();
            randomAccessFile.close();
            logger.info("下载完毕：" + startByte + "-" + endByte + "：" + transmitted);
        } catch (ClientAbortException e) {
            logger.warn("用户停止下载：" + startByte + "-" + endByte + "：" + transmitted);
            //捕获此异常表示拥护停止下载
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("用户下载IO异常，Message：{}", e.getLocalizedMessage());
        } finally {
            try {
                if (randomAccessFile != null) {
                    randomAccessFile.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }///end try
    }
}
