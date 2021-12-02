package top.panll.assist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WvpProAssistApplication {

    public static void main(String[] args) {
        SpringApplication.run(WvpProAssistApplication.class, args);
    }

}
