package org.example.newdivarextraction.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttributeEntry {

    private String key;
    private String title;
    private String value;
    private int order;
}
