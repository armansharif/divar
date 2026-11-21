package org.example.newdivarextraction.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "processed_urls")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long scrapeUrlId;

    @Column(columnDefinition = "TEXT")
    private String url;

    @Column(nullable = false)
    private Boolean success;

    private Integer listingsFound;

    private Integer listingsSent;

    private LocalDateTime processedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        processedAt = LocalDateTime.now();
    }
}
