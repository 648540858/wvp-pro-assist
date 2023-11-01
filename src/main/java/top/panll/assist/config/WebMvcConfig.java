package top.panll.assist.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import top.panll.assist.dto.UserSettings;

import java.io.File;


@Configuration
public class WebMvcConfig extends WebMvcConfigurerAdapter {

    @Autowired
    private UserSettings userSettings;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File file = new File(userSettings.getRecordTempPath());
        registry.addResourceHandler("/download/**").addResourceLocations("file://" + file.getAbsolutePath() + "/");
        super.addResourceHandlers(registry);
    }
}
