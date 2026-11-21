package org.example.newdivarextraction.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "property_listings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String token;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(columnDefinition = "TEXT")
    private String listingType;

    @Column(columnDefinition = "TEXT")
    private String imageUrls;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String district;

    @Column(nullable = false)
    private String propertyType;

    @Column(nullable = false)
    private Boolean isBroker;

    @Column(nullable = false)
    private Boolean isSent;

    private LocalDateTime scrapedAt;

    private LocalDateTime sentAt;

    @Column(columnDefinition = "TEXT")
    private String attributes;

    @Column(columnDefinition = "TEXT")
    private String sourceUrlTitle;

    @Column(nullable = false)
    private Boolean isPersonal;

    @PrePersist
    protected void onCreate() {
        scrapedAt = LocalDateTime.now();
        if (isBroker == null) isBroker = false;
        if (isSent == null) isSent = false;
        if (isPersonal == null) isPersonal = false;
    }
}
