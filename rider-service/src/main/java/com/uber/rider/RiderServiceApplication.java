package com.uber.rider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.uber.rider", "com.uber.common"})
@EnableKafka
@EnableAsync
@EnableScheduling
public class RiderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RiderServiceApplication.class, args);
    }
}
