package com.mile.springbatchtoy;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableBatchProcessing
public class SpringBatchToyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBatchToyApplication.class, args);
    }

}
