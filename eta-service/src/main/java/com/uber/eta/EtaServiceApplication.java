package com.uber.eta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.uber.eta", "com.uber.common"})
@EnableKafka
@EnableAsync
@EnableScheduling
public class EtaServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EtaServiceApplication.class, args);
    }
}
