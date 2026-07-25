package com.expensewise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ExpenseWiseApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseWiseApplication.class, args);
    }
}
