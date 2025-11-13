package com.javaclub.lvivjavaclubtopicsearch.model;

import java.util.List;

public record TopicSearchResponse(String verdict, List<SimilarTopicDto> similarTopics) {
}
