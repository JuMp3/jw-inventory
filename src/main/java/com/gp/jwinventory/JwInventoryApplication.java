package com.gp.jwinventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JwInventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(JwInventoryApplication.class, args);
    }
}
