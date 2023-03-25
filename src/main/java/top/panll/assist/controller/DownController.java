package top.panll.assist.controller;


import org.apache.catalina.connector.ClientAbortException;
import org.mp4parser.BasicContainer;
import org.mp4parser.Container;
import org.mp4parser.muxer.Movie;
import org.mp4parser.muxer.Track;
import org.mp4parser.muxer.builder.DefaultMp4Builder;
import org.mp4parser.muxer.builder.Mp4Builder;
import org.mp4parser.muxer.container.mp4.MovieCreator;
import org.mp4parser.muxer.tracks.AppendTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import top.panll.assist.dto.UserSettings;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Controller
@RequestMapping("/down")
public class DownController {

    private final static Logger logger = LoggerFactory.getLogger(DownController.class);

    @Autowired
    private UserSettings userSettings;

    /**
     * 获取app+stream列表
     *
     * @return
     */
    @GetMapping(value = "/**")
    @ResponseBody
    public void download(HttpServletRequest request, HttpServletResponse response) throws IOException {

        List<String> videoList = new ArrayList<>();
        videoList.add("/home/lin/server/test/zlm/Debug/www/record/rtp/34020000002000000003_34020000001310000001/2023-03-20/16-09-07.mp4");
        videoList.add("/home/lin/server/test/zlm/Debug/www/record/rtp/34020000002000000003_34020000001310000001/2023-03-20/17-12-10.mp4");
        videoList.add("/home/lin/server/test/zlm/Debug/www/record/rtp/34020000002000000003_34020000001310000001/2023-03-20/17-38-36.mp4");
        List<Movie> sourceMovies = new ArrayList<>();
        for (String video : videoList) {
            sourceMovies.add(MovieCreator.build(video));
        }

        List<Track> videoTracks = new LinkedList<>();
        List<Track> audioTracks = new LinkedList<>();
        for (Movie movie : sourceMovies) {
            for (Track track : movie.getTracks()) {
                if ("soun".equals(track.getHandler())) {
                    audioTracks.add(track);
                }

                if ("vide".equals(track.getHandler())) {
                    videoTracks.add(track);
                }
            }
        }
        Movie mergeMovie = new Movie();
        if (audioTracks.size() > 0) {
            mergeMovie.addTrack(new AppendTrack(audioTracks.toArray(new Track[audioTracks.size()])));
        }

        if (videoTracks.size() > 0) {
            mergeMovie.addTrack(new AppendTrack(videoTracks.toArray(new Track[videoTracks.size()])));
        }

        BasicContainer out = (BasicContainer)new DefaultMp4Builder().build(mergeMovie);

        // 文件名
        String fileName = "测试.mp4";
        // 文件类型
        String contentType = request.getServletContext().getMimeType(fileName);

        // 解决下载文件时文件名乱码问题
        byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        fileName = new String(fileNameBytes, 0, fileNameBytes.length, StandardCharsets.ISO_8859_1);

        response.setHeader("Content-Type", contentType);
        response.setHeader("Content-Length", String.valueOf(out));
        //inline表示浏览器直接使用，attachment表示下载，fileName表示下载的文件名
        response.setHeader("Content-Disposition", "inline;filename=" + fileName);
        response.setContentType(contentType);

        WritableByteChannel writableByteChannel = Channels.newChannel(response.getOutputStream());
        out.writeContainer(writableByteChannel);
        writableByteChannel.close();

    }
}
