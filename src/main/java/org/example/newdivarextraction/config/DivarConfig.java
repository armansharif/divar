package org.example.newdivarextraction.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.divar")
public class DivarConfig {

    private int scrollWaitSeconds = 3;
    private int requestDelaySeconds = 5;
    private int maxScrollAttempts = 10;
}
