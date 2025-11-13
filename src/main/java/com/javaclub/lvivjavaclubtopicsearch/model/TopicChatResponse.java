package com.javaclub.lvivjavaclubtopicsearch.model;

import java.util.List;

public record TopicChatResponse(String answer, List<SimilarTopicDto> similarTopics) {
}
