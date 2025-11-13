package com.javaclub.lvivjavaclubtopicsearch.service;

import com.javaclub.lvivjavaclubtopicsearch.config.YouTubeProperties;
import com.javaclub.lvivjavaclubtopicsearch.model.TopicDto;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class YoutubeTopicService {
    private static final String BASE_URL = "https://www.googleapis.com";
    private static final String SEARCH_PATH = "/youtube/v3/search";
    private static final String VIDEOS_PATH = "/youtube/v3/videos";
    private static final int MAX_RESULTS = 50;

    private final RestClient restClient;
    private final YouTubeProperties properties;

    public YoutubeTopicService(RestClient.Builder restClientBuilder,
                               YouTubeProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .build();
        this.properties = properties;
    }

    public List<TopicDto> fetchChannelTopics() {
        List<TopicDto> topics = new ArrayList<>();
        String pageToken = null;

        do {
            SearchResponse searchResponse = fetchSearchPage(pageToken);

            if (searchResponse == null || searchResponse.items() == null || searchResponse.items().isEmpty()) {
                break;
            }

            List<String> videoIds = searchResponse.items().stream()
                    .map(SearchItem::id)
                    .filter(Objects::nonNull)
                    .map(SearchItemId::videoId)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();

            if (!videoIds.isEmpty()) {
                List<VideoItem> videoItems = fetchVideos(videoIds);
                if (videoItems != null && !videoItems.isEmpty()) {
                    videoItems.stream()
                            .map(VideoItem::snippet)
                            .filter(Objects::nonNull)
                            .map(snippet -> new TopicDto(snippet.title(), snippet.description()))
                            .forEach(topics::add);
                }
            }

            pageToken = searchResponse.nextPageToken();
        } while (pageToken != null && !pageToken.isBlank());

        return topics;
    }

    private SearchResponse fetchSearchPage(String pageToken) {
        return restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path(SEARCH_PATH)
                            .queryParam("part", "snippet")
                            .queryParam("channelId", properties.channelId())
                            .queryParam("maxResults", MAX_RESULTS)
                            .queryParam("order", "date")
                            .queryParam("type", "video")
                            .queryParam("key", properties.apiKey());
                    if (pageToken != null && !pageToken.isBlank()) {
                        builder.queryParam("pageToken", pageToken);
                    }
                    return builder.build();
                })
                .retrieve()
                .body(SearchResponse.class);
    }

    private List<VideoItem> fetchVideos(List<String> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) {
            return List.of();
        }

        String ids = String.join(",", videoIds);

        VideoResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path(VIDEOS_PATH)
                        .queryParam("part", "snippet")
                        .queryParam("id", ids)
                        .queryParam("maxResults", MAX_RESULTS)
                        .queryParam("key", properties.apiKey())
                        .build())
                .retrieve()
                .body(VideoResponse.class);

        if (response == null || response.items() == null) {
            return List.of();
        }

        return response.items();
    }

    private record SearchResponse(List<SearchItem> items, String nextPageToken) {
    }

    private record SearchItem(SearchItemId id, SearchSnippet snippet) {
    }

    private record SearchItemId(String kind, String videoId) {
    }

    private record SearchSnippet(String title, String description) {
    }

    private record VideoResponse(List<VideoItem> items) {
    }

    private record VideoItem(VideoSnippet snippet) {
    }

    private record VideoSnippet(String title, String description) {
    }
}
