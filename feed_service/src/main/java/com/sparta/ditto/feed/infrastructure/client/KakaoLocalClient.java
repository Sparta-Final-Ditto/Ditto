package com.sparta.ditto.feed.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
/** 카카오 Local API REST 클라이언트 (역지오코딩) */
public class KakaoLocalClient {

    private final RestClient restClient;
    private final String apiKey;

    public KakaoLocalClient(@Value("${app.kakao.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl("https://dapi.kakao.com")
                .build();
    }

    @SuppressWarnings("unchecked")
    public String reverseGeocode(double latitude, double longitude) {
        Map<String, Object> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/local/geo/coord2address.json")
                        .queryParam("x", longitude)
                        .queryParam("y", latitude)
                        .build())
                .header("Authorization", "KakaoAK " + apiKey)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            return null;
        }

        List<Map<String, Object>> documents = (List<Map<String, Object>>) response.get("documents");
        if (documents == null || documents.isEmpty()) {
            return null;
        }

        Map<String, Object> address = (Map<String, Object>) documents.get(0).get("address");
        if (address == null) {
            return null;
        }

        String region1 = (String) address.get("region_1depth_name");
        String region2 = (String) address.get("region_2depth_name");
        return region1 + " " + region2;
    }
}