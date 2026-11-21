package org.example.newdivarextraction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.newdivarextraction.dto.PropertyDto;
import org.example.newdivarextraction.model.ProcessedUrl;
import org.example.newdivarextraction.model.ScrapeUrl;
import org.example.newdivarextraction.repository.ProcessedUrlRepository;
import org.example.newdivarextraction.repository.ScrapeUrlRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final DivarScraperService scraperService;
    private final TelegramService telegramService;
    private final ScrapeUrlRepository scrapeUrlRepository;
    private final ProcessedUrlRepository processedUrlRepository;

    @Scheduled(cron = "${app.scheduler.cron:0 0 8 * * ?}")
    public void runScrapingJob() {
        log.info("========== Starting scheduled scraping job ==========");
        int totalListings = 0;
        int totalSent = 0;

        List<ScrapeUrl> activeUrls = scrapeUrlRepository.findByEnabledTrue();
        log.info("Found {} active scrape URLs", activeUrls.size());

        for (ScrapeUrl scrapeUrl : activeUrls) {
            log.info("Processing URL: {}", scrapeUrl.getUrl());

            try {
                List<PropertyDto> listings = scraperService.scrapeUrl(scrapeUrl.getUrl(), scrapeUrl.getPersianTitle());

                int sentCount = 0;
                for (PropertyDto dto : listings) {
                    telegramService.sendListing(dto);
                    sentCount++;
                    totalSent++;

                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                ProcessedUrl processedUrl = ProcessedUrl.builder()
                        .scrapeUrlId(scrapeUrl.getId())
                        .url(scrapeUrl.getUrl())
                        .success(true)
                        .listingsFound(listings.size())
                        .listingsSent(sentCount)
                        .build();
                processedUrlRepository.save(processedUrl);

                totalListings += listings.size();
                log.info("URL processed - Found: {}, Sent: {}", listings.size(), sentCount);

            } catch (Exception e) {
                log.error("Error processing URL: {}", scrapeUrl.getUrl(), e);

                ProcessedUrl processedUrl = ProcessedUrl.builder()
                        .scrapeUrlId(scrapeUrl.getId())
                        .url(scrapeUrl.getUrl())
                        .success(false)
                        .listingsFound(0)
                        .listingsSent(0)
                        .errorMessage(e.getMessage())
                        .build();
                processedUrlRepository.save(processedUrl);
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("========== Scraping job completed ==========");
        log.info("Total listings: {}, Sent: {}", totalListings, totalSent);
    }

    public void runManual() {
        log.info("Running manual scraping job...");
        runScrapingJob();
    }

    public Map<String, Object> runSelectedUrls(List<Long> urlIds) {
        log.info("Running scraping for selected URLs: {}", urlIds);
        int totalListings = 0;
        int totalSent = 0;
        List<Map<String, Object>> results = new ArrayList<>();

        for (Long urlId : urlIds) {
            Optional<ScrapeUrl> optional = scrapeUrlRepository.findById(urlId);
            if (optional.isEmpty()) continue;

            ScrapeUrl scrapeUrl = optional.get();
            Map<String, Object> result = new HashMap<>();
            result.put("urlId", scrapeUrl.getId());
            result.put("url", scrapeUrl.getUrl());

            try {
                List<PropertyDto> listings = scraperService.scrapeUrl(scrapeUrl.getUrl(), scrapeUrl.getPersianTitle());
                int sentCount = 0;
                for (PropertyDto dto : listings) {
                    telegramService.sendListing(dto);
                    sentCount++;
                    totalSent++;
                    Thread.sleep(5000);
                }

                ProcessedUrl processedUrl = ProcessedUrl.builder()
                        .scrapeUrlId(scrapeUrl.getId())
                        .url(scrapeUrl.getUrl())
                        .success(true)
                        .listingsFound(listings.size())
                        .listingsSent(sentCount)
                        .build();
                processedUrlRepository.save(processedUrl);

                result.put("status", "success");
                result.put("listingsFound", listings.size());
                result.put("listingsSent", sentCount);
                totalListings += listings.size();

            } catch (Exception e) {
                log.error("Error processing URL: {}", scrapeUrl.getUrl(), e);
                result.put("status", "error");
                result.put("error", e.getMessage());

                ProcessedUrl processedUrl = ProcessedUrl.builder()
                        .scrapeUrlId(scrapeUrl.getId())
                        .url(scrapeUrl.getUrl())
                        .success(false)
                        .listingsFound(0)
                        .listingsSent(0)
                        .errorMessage(e.getMessage())
                        .build();
                processedUrlRepository.save(processedUrl);
            }
            results.add(result);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalListings", totalListings);
        summary.put("totalSent", totalSent);
        summary.put("results", results);
        return summary;
    }
}
