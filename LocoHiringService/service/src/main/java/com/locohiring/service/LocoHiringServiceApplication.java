package com.locohiring.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class LocoHiringServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocoHiringServiceApplication.class, args);
    }

    /** All "now" decisions go through this clock; its zone (the JVM's, set by TZ) is the zone of availability hours. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
