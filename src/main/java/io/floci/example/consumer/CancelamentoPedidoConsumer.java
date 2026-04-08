package io.floci.example.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.floci.example.dto.PedidoMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Processa cancelamentos vindos da fila pedidos-cancelados.
 * Recebe apenas mensagens onde o SNS message attribute tipo=CANCELAMENTO.
 */
@Service
public class CancelamentoPedidoConsumer {

    private static final Logger log = LoggerFactory.getLogger(CancelamentoPedidoConsumer.class);

    private final SnsEnvelopeExtractor extractor;

    public CancelamentoPedidoConsumer(ObjectMapper objectMapper) {
        this.extractor = new SnsEnvelopeExtractor(objectMapper);
    }

    @SqsListener("${app.sqs.queue-cancelados}")
    public void processarCancelamento(@Payload String rawBody) {
        PedidoMessage pedido = extractor.extrair(rawBody);
        log.warn("[CANCELAMENTO] id={} | produto={} | qtd={} | total=R$ {}",
                pedido.pedidoId(),
                pedido.produto(),
                pedido.quantidade(),
                pedido.valorTotal());

        // lógica de cancelamento: estornar pagamento, atualizar estoque, etc.
    }
}
