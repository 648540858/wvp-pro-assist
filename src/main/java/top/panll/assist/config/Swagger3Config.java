package top.panll.assist.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

@Configuration
public class Swagger3Config {

    @Value("${swagger-ui.enabled}")
    private boolean enable;

    @Bean
    public Docket createRestApi() {
        return new Docket(DocumentationType.OAS_30)
                .apiInfo(apiInfo())
                .groupName("1. 全部")
                .select()
                .apis(RequestHandlerSelectors.basePackage("top.panll.assist"))
                .paths(PathSelectors.any())
                .build()
                .pathMapping("/")
                .enable(enable);
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("WVP-PRO-ASSIST 接口文档")
                .description("更多请咨询服务开发者(https://github.com/648540858/wvp-pro-assist)。")
                .contact(new Contact("648540858", "648540858", "648540858@qq.com"))
                .version("1.0")
                .build();
    }
}
