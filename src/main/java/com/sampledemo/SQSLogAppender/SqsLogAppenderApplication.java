package com.sampledemo.SQSLogAppender;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the SQS Log Appender demo.
 */
@SpringBootApplication
public class SqsLogAppenderApplication {
    private static final Logger logger = LogManager.getLogger(SqsLogAppenderApplication.class);

    /**
     * Main method to run the Spring Boot application.
     *
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(SqsLogAppenderApplication.class, args);

        // Example: Logging without hardcoded ThreadContext
        logger.info("SqsLogAppenderApplication main function called");
    }
}
