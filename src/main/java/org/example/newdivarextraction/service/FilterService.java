package org.example.newdivarextraction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.newdivarextraction.model.FilterKeyword;
import org.example.newdivarextraction.repository.FilterKeywordRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilterService {

    private final FilterKeywordRepository filterKeywordRepository;

    public boolean isBroker(String description) {
        if (description == null || description.isBlank()) {
            return false;
        }

        String lowerDesc = description.toLowerCase();
        List<FilterKeyword> keywords = filterKeywordRepository.findByEnabledTrue();

        for (FilterKeyword kw : keywords) {
            String keywordLower = kw.getKeyword().toLowerCase();
            if (lowerDesc.contains(keywordLower)) {
                log.info("Broker detected by keyword: '{}' in description", kw.getKeyword());
                return true;
            }
        }

        log.debug("No broker keywords found in description (length: {})", description.length());
        return false;
    }

    public List<FilterKeyword> getAllKeywords() {
        return filterKeywordRepository.findAll();
    }

    public List<FilterKeyword> getActiveKeywords() {
        return filterKeywordRepository.findByEnabledTrue();
    }

    public FilterKeyword addKeyword(String keyword) {
        FilterKeyword existing = filterKeywordRepository.findByKeyword(keyword);
        if (existing != null) {
            return existing;
        }
        FilterKeyword newKeyword = FilterKeyword.builder()
                .keyword(keyword)
                .enabled(true)
                .build();
        return filterKeywordRepository.save(newKeyword);
    }

    public void toggleKeyword(Long id, boolean enabled) {
        filterKeywordRepository.findById(id).ifPresent(kw -> {
            kw.setEnabled(enabled);
            filterKeywordRepository.save(kw);
        });
    }

    public void removeKeyword(Long id) {
        filterKeywordRepository.deleteById(id);
    }
}
