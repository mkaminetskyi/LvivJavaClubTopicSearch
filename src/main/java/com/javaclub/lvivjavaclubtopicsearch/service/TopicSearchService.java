package com.javaclub.lvivjavaclubtopicsearch.service;

import com.javaclub.lvivjavaclubtopicsearch.config.TopicSearchProperties;
import com.javaclub.lvivjavaclubtopicsearch.model.SimilarTopicDto;
import com.javaclub.lvivjavaclubtopicsearch.model.TopicDto;
import com.javaclub.lvivjavaclubtopicsearch.model.TopicIndexResponse;
import com.javaclub.lvivjavaclubtopicsearch.model.TopicProposalDto;
import com.javaclub.lvivjavaclubtopicsearch.model.TopicSearchResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TopicSearchService {

    private static final int TOP_K = 10;

    private final YoutubeTopicService youtubeTopicService;
    private final VectorStore vectorStore;
    private final TopicSearchProperties topicSearchProperties;

    public TopicSearchService(YoutubeTopicService youtubeTopicService,
                              VectorStore vectorStore,
                              TopicSearchProperties topicSearchProperties) {
        this.youtubeTopicService = youtubeTopicService;
        this.vectorStore = vectorStore;
        this.topicSearchProperties = topicSearchProperties;
    }

    public TopicIndexResponse indexChannelTopics() {
        List<TopicDto> topics = youtubeTopicService.fetchChannelTopics();
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
                .filter(topic -> topic.similarity() >= topicSearchProperties.getSimilarityThreshold())
                .map(topic -> "Така тема була – " + topic.videoUrl())
                .orElse("Такої теми ще не було.");

        return new TopicSearchResponse(verdict, similarTopics);
    }

    private Document toDocument(TopicDto topic) {
        String title = topic.title() == null ? "" : topic.title();
        String description = topic.description() == null ? "" : topic.description();
        String url = topic.videoUrl() == null ? "" : topic.videoUrl();

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
        Map<String, Object> metadata = document.getMetadata() != null ? document.getMetadata() : Map.of();
        double similarity = calculateSimilarityPercent(metadata);
        String title = Objects.toString(metadata.get("title"), "");
        String description = Objects.toString(metadata.get("description"), "");
        String url = Objects.toString(metadata.get("url"), "");

        return new SimilarTopicDto(title, description, url, similarity);
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
        if (proposal == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Необхідно передати тему для пошуку.");
        }

        StringBuilder builder = new StringBuilder();
        if (proposal.title() != null && !proposal.title().isBlank()) {
            builder.append(proposal.title());
        }
        if (proposal.description() != null && !proposal.description().isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(proposal.description());
        }

        if (builder.length() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Необхідно вказати хоча б назву або опис теми.");
        }

        return builder.toString();
    }
}
