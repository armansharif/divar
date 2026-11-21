package org.example.newdivarextraction.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.scheduler")
public class SchedulerConfig {

    private String cron = "0 0 */5 * * ?";
}
