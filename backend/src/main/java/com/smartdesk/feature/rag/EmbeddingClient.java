package com.smartdesk.feature.rag;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** analytics/service 의 POST /embed 호출 (multilingual-e5-small, 384차원). */
@Component
public class EmbeddingClient {

    private final RestClient http;

    public EmbeddingClient(RagProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.getEmbedTimeoutMs());
        factory.setReadTimeout((int) props.getEmbedTimeoutMs());
        this.http = RestClient.builder().baseUrl(props.getEmbedUrl()).requestFactory(factory).build();
    }

    public record EmbedResult(String model, int dim, List<float[]> vectors) {}

    public float[] embedQuery(String text) {
        return embed(List.of(text), "query").vectors().get(0);
    }

    public EmbedResult embedPassages(List<String> texts) {
        return embed(texts, "passage");
    }

    @SuppressWarnings("unchecked")
    private EmbedResult embed(List<String> texts, String kind) {
        Map<String, Object> res = http.post().uri("/embed")
                .body(Map.of("texts", texts, "kind", kind))
                .retrieve()
                .body(Map.class);
        if (res == null) throw new IllegalStateException("임베딩 응답 없음");
        List<List<Number>> raw = (List<List<Number>>) res.get("vectors");
        List<float[]> vectors = raw.stream().map(v -> {
            float[] arr = new float[v.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = v.get(i).floatValue();
            return arr;
        }).toList();
        return new EmbedResult(String.valueOf(res.get("model")),
                ((Number) res.get("dim")).intValue(), vectors);
    }
}
