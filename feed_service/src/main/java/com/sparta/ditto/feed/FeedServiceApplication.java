package com.sparta.ditto.feed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.sparta.ditto.feed", "com.sparta.ditto.common"})
@ConfigurationPropertiesScan
public class FeedServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FeedServiceApplication.class, args);
    }
}
