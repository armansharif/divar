package org.example.newdivarextraction.dto;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyDto {

    private String token;
    private String title;
    private String listingType;
    private List<String> imageUrls;
    private String url;
    private boolean isBroker;
    private List<AttributeEntry> attributes;
    private String sourceUrlTitle;

    public static List<AttributeEntry> parseAttributes(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return new Gson().fromJson(json, new TypeToken<List<AttributeEntry>>(){}.getType());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static String toJson(List<AttributeEntry> attributes) {
        return new Gson().toJson(attributes);
    }

    public String getAttributeValue(String key) {
        if (attributes == null) return null;
        return attributes.stream()
                .filter(a -> a.getKey().equals(key))
                .map(AttributeEntry::getValue)
                .findFirst()
                .orElse(null);
    }

    public String toTelegramMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("          ***  **  **  **  **  **  **  **  **  **  **  **  **  **  **\n\n");

        if (listingType != null) {
            sb.append("  #").append(listingType).append("\n\n");
        }

        sb.append("  ").append(title).append("\n\n");

        if (attributes != null) {
            attributes.stream()
                    .sorted(Comparator.comparingInt(AttributeEntry::getOrder))
                    .forEach(attr -> {
                        if (attr.getValue() != null && !attr.getValue().isBlank()
                            && !attr.getKey().equals("fullDescription")
                            && !attr.getKey().equals("title")
                            && !attr.getKey().equals("url")) {
                            sb.append(attr.getTitle()).append(" : ").append(attr.getValue()).append("\n");
                        }
                    });
        }

        String desc = getAttributeValue("fullDescription");
        if (desc != null && !desc.isBlank()) {
            String shortDesc = desc.length() > 300
                ? desc.substring(0, 300) + "..."
                : desc;
            sb.append("\n  : ").append(shortDesc).append("\n\n");
        }

        if (url != null && !url.isBlank()) {
            if (sourceUrlTitle != null && !sourceUrlTitle.isBlank()) {
                sb.append("\n   : ").append(sourceUrlTitle).append("\n");
            }
            sb.append("   : ").append(url).append("\n");
        }
        sb.append("          ***  **  ** **  **  **  **  **  **  **  **  **  **  **  **");

        return sb.toString();
    }
}
