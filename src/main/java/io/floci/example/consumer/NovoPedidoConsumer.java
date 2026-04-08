package io.floci.example.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.floci.example.dto.PedidoMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Processa pedidos NOVOS vindos da fila pedidos-novos.
 * Recebe apenas mensagens onde o SNS message attribute tipo=NOVO.
 */
@Service
public class NovoPedidoConsumer {

    private static final Logger log = LoggerFactory.getLogger(NovoPedidoConsumer.class);

    private final SnsEnvelopeExtractor extractor;

    public NovoPedidoConsumer(ObjectMapper objectMapper) {
        this.extractor = new SnsEnvelopeExtractor(objectMapper);
    }

    @SqsListener("${app.sqs.queue-novos}")
    public void processarNovoPedido(@Payload String rawBody) {
        PedidoMessage pedido = extractor.extrair(rawBody);
        log.info("[NOVO PEDIDO] id={} | produto={} | qtd={} | total=R$ {}",
                pedido.pedidoId(),
                pedido.produto(),
                pedido.quantidade(),
                pedido.valorTotal());

        // lógica de processamento de novo pedido
    }
}
