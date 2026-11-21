package org.example.newdivarextraction.repository;

import org.example.newdivarextraction.model.ProcessedUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedUrlRepository extends JpaRepository<ProcessedUrl, Long> {

    Optional<ProcessedUrl> findFirstByScrapeUrlIdOrderByProcessedAtDesc(Long scrapeUrlId);
}
