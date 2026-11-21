package org.example.newdivarextraction.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "filter_keywords")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String keyword;

    @Column(nullable = false)
    private Boolean enabled;

    @PrePersist
    protected void onCreate() {
        if (enabled == null) enabled = true;
    }
}
