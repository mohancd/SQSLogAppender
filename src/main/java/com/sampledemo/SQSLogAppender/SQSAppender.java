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

/**
 * Custom Log4j2 appender to send log messages to an AWS SQS queue.
 */
@Plugin(name = "SQSAppender", category = "Core", elementType = "appender", printObject = true)
public class SQSAppender extends AbstractAppender {

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;

    /**
     * Constructor for SQSAppender.
     *
     * @param name       Appender name
     * @param filter     Filter for log events
     * @param layout     Layout for log messages
     * @param sqsClient  AWS SQS client instance
     * @param queueUrl   SQS queue URL
     */
    protected SQSAppender(String name, Filter filter, Layout<? extends Serializable> layout, SqsClient sqsClient, String queueUrl) {
        super(name, filter, layout, false);
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void append(LogEvent event) {
        if (!isThreadContextValid()) {
            LOGGER.warn("Skipping log message: Missing required ThreadContext keys (trace_id, span_id).");
            return;
        }

        try {
            String logMessage = formatLogMessage(event);
            sendMessageToSQS(logMessage);
        } catch (Exception e) {
            LOGGER.error("Failed to send log message to SQS", e);
        }
    }

    private boolean isThreadContextValid() {
        return ThreadContext.containsKey("trace_id") && ThreadContext.containsKey("span_id");
    }

    private void sendMessageToSQS(String logMessage) {
        SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(logMessage)
                .build();

        sqsClient.sendMessage(sendMsgRequest);
    }

    private String formatLogMessage(LogEvent event) throws JsonProcessingException {
        Map<String, Object> logDetails = new HashMap<>();
        logDetails.put("log_type", "service_request_log");
        logDetails.put("trace_id", getThreadContextValue("trace_id"));
        logDetails.put("span_id", getThreadContextValue("span_id"));
        logDetails.put("url", getThreadContextValue("url"));
        logDetails.put("request_type", getThreadContextValue("request_type"));
        logDetails.put("request_data", getThreadContextValue("request_data"));
        logDetails.put("response_data", getThreadContextValue("response_data"));
        logDetails.put("service_name", getThreadContextValue("service_name"));
        logDetails.put("created_date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return objectMapper.writeValueAsString(logDetails);
    }

    private String getThreadContextValue(String key) {
        return Optional.ofNullable(ThreadContext.get(key)).orElse("N/A");
    }

    @Override
    public void stop() {
        super.stop();
        if (sqsClient != null) {
            sqsClient.close();
        }
    }

    /**
     * Plugin factory to create an SQSAppender instance.
     *
     * @param name               Appender name
     * @param queueUrl           SQS queue URL
     * @param filter             Log event filter
     * @param layout             Log message layout
     * @param region             AWS region
     * @param credentialsProvider AWS credentials provider
     * @return SQSAppender instance
     */
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

        SqsClient sqsClient = SqsClient.builder()
                .credentialsProvider(credentialsProvider)
                .region(region != null ? Region.of(region) : Region.AP_SOUTH_1)
                .build();

        return new SQSAppender(name, filter, layout, sqsClient, queueUrl);
    }
}
