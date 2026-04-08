package io.floci.example.controller;

import io.floci.example.dto.PedidoMessage;
import io.floci.example.producer.PedidoProducer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private final PedidoProducer producer;

    public PedidoController(PedidoProducer producer) {
        this.producer = producer;
    }

    @PostMapping
    public ResponseEntity<String> enviarPedido(@RequestBody PedidoMessage pedido) {
        producer.enviar(pedido);
        return ResponseEntity.accepted()
                .body("Pedido %d [%s] publicado no SNS".formatted(pedido.pedidoId(), pedido.tipo()));
    }
}
