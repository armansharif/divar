package org.example.newdivarextraction.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.newdivarextraction.config.TelegramConfig;
import org.example.newdivarextraction.dto.PropertyDto;
import org.example.newdivarextraction.model.PropertyListing;
import org.example.newdivarextraction.repository.PropertyListingRepository;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramService {

    private final TelegramConfig telegramConfig;
    private final PropertyListingRepository propertyListingRepository;
    private final RestTemplate restTemplate;

    private static final String TELEGRAM_API = "https://api.telegram.org";
    private static final int MAX_IMAGES_PER_MESSAGE = 5;

    public void sendListing(PropertyDto dto) {
        if (telegramConfig.getBotToken() == null ||
            telegramConfig.getBotToken().equals("YOUR_BOT_TOKEN_HERE")) {
            log.debug("Telegram bot token not configured, skipping send");
            return;
        }

        try {
            List<String> images = dto.getImageUrls();
            if (images != null && !images.isEmpty()) {
                List<String> limitedImages = images.size() > MAX_IMAGES_PER_MESSAGE
                        ? images.subList(0, MAX_IMAGES_PER_MESSAGE)
                        : images;
                if (limitedImages.size() == 1) {
                    sendPhoto(limitedImages.get(0), dto.getTitle(), dto.getToken());
                } else {
                    sendMediaGroup(limitedImages, dto.getTitle(), dto.getToken());
                }
            }

            String message = dto.toTelegramMessage();
            sendMessage(message, dto.getToken());

            Optional<PropertyListing> listing = propertyListingRepository.findByToken(dto.getToken());
            listing.ifPresent(l -> {
                l.setIsSent(true);
                l.setSentAt(LocalDateTime.now());
                propertyListingRepository.save(l);
            });

            log.info("Sent listing to Telegram: {}", dto.getToken());

        } catch (Exception e) {
            log.error("Failed to send listing to Telegram: {}", dto.getToken(), e);
        }
    }

    public void sendBatch(List<PropertyDto> dtos) {
        for (PropertyDto dto : dtos) {
            try {
                sendListing(dto);
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void sendMessage(String text, String token) {
        String url = TELEGRAM_API + "/bot" + telegramConfig.getBotToken() + "/sendMessage";

        JsonObject body = new JsonObject();
        body.addProperty("chat_id", telegramConfig.getChannelId());
        body.addProperty("text", text);
        body.addProperty("parse_mode", "HTML");
        body.addProperty("disable_web_page_preview", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>(body.toString(), headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Telegram sendMessage failed for token {}: {}", token, response.getBody());
        }
    }

    private void sendMediaGroup(List<String> imageUrls, String caption, String token) {
        String url = TELEGRAM_API + "/bot" + telegramConfig.getBotToken() + "/sendMediaGroup";

        JsonArray mediaArray = new JsonArray();

        for (int i = 0; i < imageUrls.size(); i++) {
            JsonObject mediaItem = new JsonObject();
            mediaItem.addProperty("type", "photo");
            mediaItem.addProperty("media", imageUrls.get(i));

            if (i == 0) {
                mediaItem.addProperty("caption", caption != null ? caption : "");
            }

            mediaArray.add(mediaItem);
        }

        JsonObject body = new JsonObject();
        body.addProperty("chat_id", telegramConfig.getChannelId());
        body.add("media", mediaArray);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>(body.toString(), headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.debug("Telegram sendMediaGroup failed for token {}: {}", token, response.getBody());
        } else {
            log.debug("Sent {} images for token {}", imageUrls.size(), token);
        }
    }

    private void sendPhoto(String imageUrl, String caption, String token) {
        String url = TELEGRAM_API + "/bot" + telegramConfig.getBotToken() + "/sendPhoto";

        JsonObject body = new JsonObject();
        body.addProperty("chat_id", telegramConfig.getChannelId());
        body.addProperty("photo", imageUrl);
        body.addProperty("caption", caption != null ? caption : "");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>(body.toString(), headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.debug("Telegram sendPhoto failed for token {}: {}", token, response.getBody());
        }
    }
}
