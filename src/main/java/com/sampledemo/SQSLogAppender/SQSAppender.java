package com.sampledemo.SQSLogAppender;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Plugin(name = "SQSAppender", category = "Core", elementType = "appender", printObject = true)
public class SQSAppender extends AbstractAppender {

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;

    protected SQSAppender(String name, Filter filter, Layout<? extends Serializable> layout, SqsClient sqsClient, String queueUrl) {
        super(name, filter, layout, false);
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void append(LogEvent event) {
        String traceId = ThreadContext.get("trace_id");
        String spanId = ThreadContext.get("span_id");

        if (traceId == null || spanId == null) {
            LOGGER.warn("Skipping log message as trace_id or span_id is not set in ThreadContext.");
            return;
        }

        try {
            // Create the log message in JSON format
            String logMessage = formatLogMessage(event);

            // Send the log message to SQS
            SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(logMessage)
                    .build();

            sqsClient.sendMessage(sendMsgRequest);
        } catch (Exception e) {
            LOGGER.error("Failed to send log message to SQS", e);
        }
    }

    private String formatLogMessage(LogEvent event) throws JsonProcessingException {
        Map<String, Object> logDetails = new HashMap<>();
        logDetails.put("log_type", "service_request_log");
        logDetails.put("trace_id", Optional.ofNullable(ThreadContext.get("trace_id")).orElse("N/A"));
        logDetails.put("span_id", Optional.ofNullable(ThreadContext.get("span_id")).orElse("N/A"));
        logDetails.put("url", Optional.ofNullable(ThreadContext.get("url")).orElse("N/A"));
        logDetails.put("request_type", Optional.ofNullable(ThreadContext.get("request_type")).orElse("N/A"));
        logDetails.put("request_data", Optional.ofNullable(ThreadContext.get("request_data")).orElse(null));
        logDetails.put("response_data", Optional.ofNullable(ThreadContext.get("response_data")).orElse(null));
        logDetails.put("service_name", Optional.ofNullable(ThreadContext.get("service_name")).orElse("N/A"));
        logDetails.put("created_date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return objectMapper.writeValueAsString(logDetails);
    }

    @Override
    public void stop() {
        super.stop();
        if (sqsClient != null) {
            sqsClient.close();
        }
    }

    @PluginFactory
    public static SQSAppender createAppender(@PluginAttribute("name") String name,
                                             @PluginAttribute("queueUrl") String queueUrl,
                                             @PluginElement("Filter") Filter filter,
                                             @PluginElement("Layout") Layout<? extends Serializable> layout,
                                             @PluginAttribute("region") String region,
                                             @PluginElement("credentialsProvider") AwsCredentialsProvider credentialsProvider) {

        if (layout == null) {
            layout = PatternLayout.createDefaultLayout();
        }

        // Build the SQS client using the provided credentials and region
        SqsClient sqsClient = SqsClient.builder()
                .credentialsProvider(credentialsProvider)
                .region(region != null ? Region.of(region) : Region.AP_SOUTH_1)
                .build();

        return new SQSAppender(name, filter, layout, sqsClient, queueUrl);
    }
}
