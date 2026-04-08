package io.floci.example.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.floci.example.dto.PedidoMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

@Service
public class PedidoProducer {

    private static final Logger log = LoggerFactory.getLogger(PedidoProducer.class);

    private final SnsAsyncClient snsClient;
    private final ObjectMapper objectMapper;
    private final String topicName;

    public PedidoProducer(SnsAsyncClient snsClient,
                          ObjectMapper objectMapper,
                          @Value("${app.sns.topic-name}") String topicName) {
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
        this.topicName = topicName;
    }

    public void enviar(PedidoMessage pedido) {
        try {
            String topicArn = resolverTopicArn();
            String payload  = objectMapper.writeValueAsString(pedido);

            snsClient.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(payload)
                    .messageAttributes(Map.of(
                            "tipo", MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(pedido.tipo().name())
                                    .build()))
                    .build()).join();

            log.info("Pedido {} publicado no SNS com tipo={}", pedido.pedidoId(), pedido.tipo());
        } catch (Exception e) {
            throw new RuntimeException("Erro ao publicar pedido no SNS", e);
        }
    }

    private String resolverTopicArn() {
        return snsClient.listTopics().join()
                .topics()
                .stream()
                .map(t -> t.topicArn())
                .filter(arn -> arn.endsWith(":" + topicName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Tópico SNS não encontrado: " + topicName));
    }
}
