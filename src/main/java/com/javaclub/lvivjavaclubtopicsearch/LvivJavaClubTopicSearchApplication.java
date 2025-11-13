package com.javaclub.lvivjavaclubtopicsearch;

import com.javaclub.lvivjavaclubtopicsearch.config.TopicSearchProperties;
import com.javaclub.lvivjavaclubtopicsearch.config.YouTubeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({YouTubeProperties.class, TopicSearchProperties.class})
public class LvivJavaClubTopicSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(LvivJavaClubTopicSearchApplication.class, args);
    }
}
