package com.javaclub.lvivjavaclubtopicsearch.service;

import com.javaclub.lvivjavaclubtopicsearch.model.SimilarTopicDto;
import com.javaclub.lvivjavaclubtopicsearch.model.TopicChatRequest;
import com.javaclub.lvivjavaclubtopicsearch.model.TopicChatResponse;
import com.javaclub.lvivjavaclubtopicsearch.model.TopicDto;
import com.javaclub.lvivjavaclubtopicsearch.model.TopicIndexResponse;
import com.javaclub.lvivjavaclubtopicsearch.model.TopicProposalDto;
import com.javaclub.lvivjavaclubtopicsearch.model.TopicSearchResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class TopicSearchService {
    private static final String BASE_URL = "https://www.googleapis.com";
    private static final String SEARCH_PATH = "/youtube/v3/search";
    private static final String VIDEOS_PATH = "/youtube/v3/videos";
    private static final int MAX_RESULTS = 50;
    private static final int TOP_K = 10;

    private final RestClient restClient;
    private final VectorStore vectorStore;
    private final ChatClient smartChatClient;
    private final double similarityThreshold;
    private final String apiKey;
    private final String channelId;

    public TopicSearchService(RestClient.Builder restClientBuilder,
                              VectorStore vectorStore,
                              @Qualifier("smartChatClient") ChatClient smartChatClient,
                              @Value("${youtube.api-key}") String apiKey,
                              @Value("${youtube.channel-id}") String channelId,
                              @Value("${topics.similarity-threshold}") double similarityThreshold) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .build();
        this.vectorStore = vectorStore;
        this.smartChatClient = smartChatClient;
        this.similarityThreshold = similarityThreshold;
        this.apiKey = apiKey;
        this.channelId = channelId;
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
                            .filter(Objects::nonNull)
                            .map(this::toTopicDto)
                            .filter(Objects::nonNull)
                            .forEach(topics::add);
                }
            }

            pageToken = searchResponse.nextPageToken();
        } while (pageToken != null && !pageToken.isBlank());

        return topics;
    }

    public TopicIndexResponse indexChannelTopics() {
        List<TopicDto> topics = fetchChannelTopics();
        if (topics.isEmpty()) {
            return new TopicIndexResponse("Нових тем не знайдено.", 0);
        }

        List<Document> documents = topics.stream()
                .filter(Objects::nonNull)
                .map(this::toDocument)
                .toList();

        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }

        return new TopicIndexResponse(
                "До векторної бази додано " + documents.size() + " тем.",
                documents.size());
    }

    public TopicSearchResponse searchSimilarTopics(TopicProposalDto proposal) {
        String query = buildQuery(proposal);

        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(TOP_K)
                        .build());

        if (documents == null) {
            documents = List.of();
        }

        List<SimilarTopicDto> similarTopics = documents.stream()
                .map(this::toSimilarTopic)
                .toList();

        String verdict = similarTopics.stream()
                .findFirst()
                .filter(topic -> topic.similarity() >= similarityThreshold)
                .map(topic -> "Така тема була – " + topic.videoUrl())
                .orElse("Такої теми ще не було.");

        return new TopicSearchResponse(verdict, similarTopics);
    }

    private SearchResponse fetchSearchPage(String pageToken) {
        return restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path(SEARCH_PATH)
                            .queryParam("part", "snippet")
                            .queryParam("channelId", channelId)
                            .queryParam("maxResults", MAX_RESULTS)
                            .queryParam("order", "date")
                            .queryParam("type", "video")
                            .queryParam("key", apiKey);
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
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(VideoResponse.class);

        if (response == null || response.items() == null) {
            return List.of();
        }

        return response.items();
    }

    private Document toDocument(TopicDto topic) {
        String title = Objects.toString(topic.title(), "");
        String description = Objects.toString(topic.description(), "");
        String url = Objects.toString(topic.videoUrl(), "");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", title);
        metadata.put("description", description);
        metadata.put("url", url);

        String content = (title + "\n\n" + description).trim();
        if (content.isBlank()) {
            content = url;
        }

        return new Document(content, metadata);
    }

    private SimilarTopicDto toSimilarTopic(Document document) {
        Map<String, Object> metadata = Objects.requireNonNullElseGet(document.getMetadata(), Map::of);
        double similarity = calculateSimilarityPercent(metadata);
        String title = Objects.toString(metadata.get("title"), "");
        String description = Objects.toString(metadata.get("description"), "");
        String url = Objects.toString(metadata.get("url"), "");

        return new SimilarTopicDto(title, description, url, similarity);
    }

    public TopicChatResponse chatAboutTopics(TopicChatRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("Question must not be blank");
        }

        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(request.question())
                        .topK(TOP_K)
                        .build());

        if (documents == null) {
            documents = List.of();
        }

        List<SimilarTopicDto> similarTopics = documents.stream()
                .map(this::toSimilarTopic)
                .toList();

        String context = similarTopics.stream()
                .map(this::formatTopicForContext)
                .collect(Collectors.joining("\n\n"));

        String answer = smartChatClient.prompt()
                .system("""
                        Ти асистент YouTube каналу Lviv Java Club і допомагаєш з вибором тем.
                        Використовуй надані тобі відео як контекст і відповідай лише українською мовою.
                        """)
                .user(buildChatUserMessage(request.question(), context))
                .call()
                .content();

        return new TopicChatResponse(answer, similarTopics);
    }

    private String formatTopicForContext(SimilarTopicDto topic) {
        return "Назва: " + topic.title() +
                "\nОпис: " + topic.description() +
                "\nПосилання: " + topic.videoUrl() +
                "\nСхожість: " + Math.round(topic.similarity()) + "%";
    }

    private String buildChatUserMessage(String question, String context) {
        if (context == null || context.isBlank()) {
            return "Питання: " + question + "\n\nКонтекст: Немає релевантних тем у базі.";
        }

        return "Питання: " + question + "\n\nКонтекст:\n" + context;
    }

    private double calculateSimilarityPercent(Map<String, Object> metadata) {
        Object distanceObj = metadata.get("distance");
        if (distanceObj instanceof Number number) {
            double distance = Math.max(0d, Math.min(1d, number.doubleValue()));
            return (1 - distance) * 100d;
        }

        Object similarityObj = metadata.get("similarity");
        if (similarityObj instanceof Number number) {
            return Math.max(0d, Math.min(100d, number.doubleValue() * 100d));
        }

        return 0d;
    }

    private String buildQuery(TopicProposalDto proposal) {
        StringBuilder builder = new StringBuilder();

        if (proposal != null && proposal.title() != null && !proposal.title().isBlank()) {
            builder.append(proposal.title());
        }
        if (proposal != null && proposal.description() != null && !proposal.description().isBlank()) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(proposal.description());
        }

        return builder.toString();
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

    private record VideoItem(String id, VideoSnippet snippet) {
    }

    private record VideoSnippet(String title, String description) {
    }

    private TopicDto toTopicDto(VideoItem video) {
        if (video == null || video.snippet() == null) {
            return null;
        }

        String url = buildVideoUrl(video.id());
        VideoSnippet snippet = video.snippet();

        return new TopicDto(snippet.title(), snippet.description(), url);
    }

    private static String buildVideoUrl(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            return "";
        }

        return "https://www.youtube.com/watch?v=" + videoId;
    }
}
