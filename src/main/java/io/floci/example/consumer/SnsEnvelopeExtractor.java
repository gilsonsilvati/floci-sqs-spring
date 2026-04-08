package io.floci.example.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.floci.example.dto.PedidoMessage;

/**
 * Quando o SNS entrega para um SQS, o corpo da mensagem SQS é um envelope JSON:
 * {
 *   "Type": "Notification",
 *   "Message": "{\"pedidoId\":1, ...}",  <-- payload real como string
 *   ...
 * }
 * Este utilitário extrai e desserializa o campo "Message" interno.
 */
class SnsEnvelopeExtractor {

    private final ObjectMapper objectMapper;

    SnsEnvelopeExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    PedidoMessage extrair(String rawBody) {
        try {
            JsonNode envelope = objectMapper.readTree(rawBody);
            String innerMessage = envelope.get("Message").asText();
            return objectMapper.readValue(innerMessage, PedidoMessage.class);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao desembrulhar envelope SNS", e);
        }
    }
}
