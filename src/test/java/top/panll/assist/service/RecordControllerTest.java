package top.panll.assist.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.panll.assist.controller.RecordController;
import top.panll.assist.controller.bean.ControllerException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RecordControllerTest {
    @Test void preservesGapReasonForWvpClient() throws Exception {
        VideoFileService service = mock(VideoFileService.class);
        when(service.mergeOrCut(any())).thenThrow(new ControllerException(100, "目标时间存在录像缺口 0.838 秒"));
        RecordController controller = new RecordController();
        ReflectionTestUtils.setField(controller, "videoFileService", service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/api/record/file/download/task/add")
                .contentType("application/json").content("{\"filePathList\":[\"record.mp4\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100))
                .andExpect(jsonPath("$.msg").value("目标时间存在录像缺口 0.838 秒"));
    }
}
