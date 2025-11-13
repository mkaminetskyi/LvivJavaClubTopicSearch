package com.javaclub.lvivjavaclubtopicsearch.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "topics")
public class TopicSearchProperties {

    /**
     * Percent value (0-100) that determines whether a topic is considered a duplicate.
     */
    private double similarityThreshold = 75.0;
}
