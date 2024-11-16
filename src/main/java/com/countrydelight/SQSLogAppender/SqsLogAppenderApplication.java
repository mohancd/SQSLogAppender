package com.countrydelight.SQSLogAppender;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SqsLogAppenderApplication {
    private static final Logger logger = LogManager.getLogger(SqsLogAppenderApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SqsLogAppenderApplication.class, args);

        // Example: Logging without any hardcoded ThreadContext
        logger.info("SqsLogAppenderApplication main function called");
    }
}
