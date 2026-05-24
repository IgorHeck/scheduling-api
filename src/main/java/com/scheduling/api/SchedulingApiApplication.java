package com.scheduling.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SchedulingApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SchedulingApiApplication.class, args);
    }
}
