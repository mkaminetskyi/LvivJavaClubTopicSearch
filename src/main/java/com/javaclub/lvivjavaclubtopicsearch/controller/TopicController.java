package com.javaclub.lvivjavaclubtopicsearch.controller;

import com.javaclub.lvivjavaclubtopicsearch.model.TopicDto;
import com.javaclub.lvivjavaclubtopicsearch.service.YoutubeTopicService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TopicController {

    private final YoutubeTopicService youtubeTopicService;

    @GetMapping("/topics")
    public List<TopicDto> getTopics() {
        return youtubeTopicService.fetchChannelTopics();
    }
}
