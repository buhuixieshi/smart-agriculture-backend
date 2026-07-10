package com.agriculture.config;

import com.agriculture.mqtt.MqttBizMessageHandler;
import com.agriculture.mqtt.MqttProperties;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Configuration
@IntegrationComponentScan(basePackages = "com.agriculture.mqtt")
@EnableConfigurationProperties(MqttProperties.class)
@ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttIntegrationConfig {

    private final MqttProperties mqttProperties;
    private final MqttBizMessageHandler mqttBizMessageHandler;

    public MqttIntegrationConfig(MqttProperties mqttProperties,
                                 MqttBizMessageHandler mqttBizMessageHandler) {
        this.mqttProperties = mqttProperties;
        this.mqttBizMessageHandler = mqttBizMessageHandler;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();

        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{mqttProperties.getBrokerUrl()});
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);

        if (mqttProperties.getUsername() != null && !mqttProperties.getUsername().isBlank()) {
            options.setUserName(mqttProperties.getUsername());
        }

        if (mqttProperties.getPassword() != null && !mqttProperties.getPassword().isBlank()) {
            options.setPassword(mqttProperties.getPassword().toCharArray());
        }

        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer mqttInbound() {
        String telemetryTopic = mqttProperties.getTopics().getTelemetry();
        String telemetryWildcardTopic = resolveTelemetryWildcardTopic(telemetryTopic);
        String[] topics = new String[]{
                telemetryWildcardTopic,
                mqttProperties.getTopics().getHeartbeat(),
                mqttProperties.getTopics().getControlReply()
        };

        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        mqttProperties.getClientId() + "-in",
                        mqttClientFactory(),
                        topics
                );

        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(mqttProperties.getQos());
        adapter.setOutputChannel(mqttInputChannel());

        return adapter;
    }

    private String resolveTelemetryWildcardTopic(String telemetryTopic) {
        if (telemetryTopic == null || telemetryTopic.isBlank()) {
            return "device/data/#";
        }

        int lastSlash = telemetryTopic.lastIndexOf('/');
        if (lastSlash <= 0) {
            return telemetryTopic + "/#";
        }

        return telemetryTopic.substring(0, lastSlash) + "/#";
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler mqttInboundHandler() {
        return message -> {
            String topic = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class);
            String payload = String.valueOf(message.getPayload());

            mqttBizMessageHandler.handleMessage(topic, payload);
        };
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound() {
        MqttPahoMessageHandler handler =
                new MqttPahoMessageHandler(
                        mqttProperties.getClientId() + "-out",
                        mqttClientFactory()
                );

        handler.setAsync(true);
        handler.setDefaultTopic(mqttProperties.getTopics().getControl());
        handler.setDefaultQos(mqttProperties.getQos());

        return handler;
    }
}
