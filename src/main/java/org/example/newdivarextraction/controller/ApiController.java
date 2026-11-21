package org.example.newdivarextraction.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.newdivarextraction.model.FilterKeyword;
import org.example.newdivarextraction.model.ScrapeUrl;
import org.example.newdivarextraction.repository.PropertyListingRepository;
import org.example.newdivarextraction.repository.ScrapeUrlRepository;
import org.example.newdivarextraction.service.FilterService;
import org.example.newdivarextraction.service.SchedulerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final SchedulerService schedulerService;
    private final FilterService filterService;
    private final PropertyListingRepository propertyListingRepository;
    private final ScrapeUrlRepository scrapeUrlRepository;

    @PostMapping("/scrape/run")
    public ResponseEntity<Map<String, Object>> runScraping() {
        log.info("Manual scraping triggered via API");
        new Thread(() -> schedulerService.runManual()).start();
        return ResponseEntity.ok(Map.of(
            "status", "started",
            "message", "Scraping job started in background"
        ));
    }

    @GetMapping("/scrape/run")
    public ResponseEntity<Map<String, Object>> runScrapingGet() {
        return runScraping();
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long total = propertyListingRepository.count();
        long brokers = propertyListingRepository.countByIsBrokerTrue();
        long personal = propertyListingRepository.countByIsBrokerFalse();

        return ResponseEntity.ok(Map.of(
            "totalListings", total,
            "brokerListings", brokers,
            "personalListings", personal
        ));
    }

    @GetMapping("/urls")
    public ResponseEntity<List<ScrapeUrl>> getUrls() {
        return ResponseEntity.ok(scrapeUrlRepository.findAll());
    }

    @PostMapping("/urls")
    public ResponseEntity<?> addUrl(@RequestBody Map<String, Object> body) {
        String url = (String) body.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url is required"));
        }

        if (scrapeUrlRepository.existsByUrl(url)) {
            return ResponseEntity.badRequest().body(Map.of("error", "URL already exists"));
        }

        String persianTitle = (String) body.get("persianTitle");

        ScrapeUrl scrapeUrl = ScrapeUrl.builder()
                .url(url)
                .persianTitle(persianTitle != null ? persianTitle : "")
                .enabled(true)
                .build();
        scrapeUrlRepository.save(scrapeUrl);
        return ResponseEntity.ok(scrapeUrl);
    }

    @PutMapping("/urls/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleUrl(@PathVariable Long id) {
        return scrapeUrlRepository.findById(id).map(url -> {
            url.setEnabled(!url.getEnabled());
            scrapeUrlRepository.save(url);
            Map<String, Object> result = new HashMap<>();
            result.put("status", "updated");
            result.put("enabled", url.getEnabled());
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/urls/{id}")
    public ResponseEntity<Map<String, Object>> removeUrl(@PathVariable Long id) {
        if (scrapeUrlRepository.existsById(id)) {
            scrapeUrlRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/keywords")
    public ResponseEntity<List<FilterKeyword>> getKeywords() {
        return ResponseEntity.ok(filterService.getAllKeywords());
    }

    @PostMapping("/keywords")
    public ResponseEntity<FilterKeyword> addKeyword(@RequestBody Map<String, Object> body) {
        String keyword = (String) body.get("keyword");
        FilterKeyword newKw = filterService.addKeyword(keyword);
        return ResponseEntity.ok(newKw);
    }

    @PutMapping("/keywords/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleKeyword(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        boolean enabled = (Boolean) body.get("enabled");
        filterService.toggleKeyword(id, enabled);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "updated");
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/keywords/{id}")
    public ResponseEntity<Map<String, Object>> removeKeyword(@PathVariable Long id) {
        filterService.removeKeyword(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @PostMapping("/scrape/selected")
    public ResponseEntity<Map<String, Object>> runSelectedScraping(@RequestBody Map<String, List<Long>> body) {
        List<Long> urlIds = body.get("urlIds");
        if (urlIds == null || urlIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "urlIds is required"));
        }
        log.info("Manual scraping triggered for selected URLs: {}", urlIds);
        Map<String, Object> result = schedulerService.runSelectedUrls(urlIds);
        return ResponseEntity.ok(result);
    }
}
