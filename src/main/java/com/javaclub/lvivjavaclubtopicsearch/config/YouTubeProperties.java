package com.javaclub.lvivjavaclubtopicsearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "youtube")
public record YouTubeProperties(String apiKey, String channelId) {
}
