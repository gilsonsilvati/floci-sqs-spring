package io.floci.example.dto;

public record PedidoMessage(
        Long pedidoId,
        String produto,
        Integer quantidade,
        Double valorTotal,
        TipoPedido tipo
) {}
