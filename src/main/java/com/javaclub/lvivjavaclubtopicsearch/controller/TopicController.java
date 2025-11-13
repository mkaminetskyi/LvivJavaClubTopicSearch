package com.javaclub.lvivjavaclubtopicsearch.controller;

import com.javaclub.lvivjavaclubtopicsearch.model.TopicDto;
import com.javaclub.lvivjavaclubtopicsearch.model.TopicIndexResponse;
import com.javaclub.lvivjavaclubtopicsearch.model.TopicProposalDto;
import com.javaclub.lvivjavaclubtopicsearch.model.TopicSearchResponse;
import com.javaclub.lvivjavaclubtopicsearch.service.TopicSearchService;
import com.javaclub.lvivjavaclubtopicsearch.service.YoutubeTopicService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TopicController {

    private final YoutubeTopicService youtubeTopicService;
    private final TopicSearchService topicSearchService;

    @GetMapping("/topics")
    public List<TopicDto> getTopics() {
        return youtubeTopicService.fetchChannelTopics();
    }

    @PostMapping("/topics/index")
    public TopicIndexResponse indexTopics() {
        return topicSearchService.indexChannelTopics();
    }

    @PostMapping("/topics/search")
    public TopicSearchResponse searchTopics(@RequestBody TopicProposalDto proposal) {
        return topicSearchService.searchSimilarTopics(proposal);
    }
}
