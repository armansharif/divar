package org.example.newdivarextraction.repository;

import org.example.newdivarextraction.model.ScrapeUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScrapeUrlRepository extends JpaRepository<ScrapeUrl, Long> {

    List<ScrapeUrl> findByEnabledTrue();

    boolean existsByUrl(String url);
}
