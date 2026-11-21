package org.example.newdivarextraction.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.newdivarextraction.model.FilterKeyword;
import org.example.newdivarextraction.repository.FilterKeywordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordInitializerService {

    private final FilterKeywordRepository filterKeywordRepository;
    private final ObjectMapper objectMapper;

    @Value("classpath:filter-keywords.json")
    private Resource keywordsFile;

    @PostConstruct
    public void initKeywords() {
        if (filterKeywordRepository.count() > 0) {
            log.info("Keywords already loaded in database, skipping initialization");
            return;
        }

        try {
            InputStream is = keywordsFile.getInputStream();
            Map<String, List<Map<String, Object>>> data = objectMapper.readValue(
                is, new TypeReference<>() {}
            );

            List<Map<String, Object>> keywordsList = data.get("keywords");
            int loaded = 0;

            for (Map<String, Object> item : keywordsList) {
                String word = (String) item.get("word");
                Boolean enabled = (Boolean) item.getOrDefault("enabled", true);

                FilterKeyword keyword = FilterKeyword.builder()
                        .keyword(word)
                        .enabled(enabled)
                        .build();
                filterKeywordRepository.save(keyword);
                loaded++;
            }

            log.info("Successfully loaded {} filter keywords from JSON file", loaded);
        } catch (Exception e) {
            log.error("Failed to load filter keywords from JSON file", e);
        }
    }
}
