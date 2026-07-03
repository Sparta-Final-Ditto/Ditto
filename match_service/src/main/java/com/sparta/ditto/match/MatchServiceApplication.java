package com.sparta.ditto.match;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class MatchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MatchServiceApplication.class, args);
    }
}
