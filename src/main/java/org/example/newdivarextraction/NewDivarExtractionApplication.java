package org.example.newdivarextraction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NewDivarExtractionApplication {

    public static void main(String[] args) {
        SpringApplication.run(NewDivarExtractionApplication.class, args);
    }

}
