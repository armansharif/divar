package org.example.newdivarextraction.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.telegram")
public class TelegramConfig {

    private String botToken;
    private String channelId;
}
