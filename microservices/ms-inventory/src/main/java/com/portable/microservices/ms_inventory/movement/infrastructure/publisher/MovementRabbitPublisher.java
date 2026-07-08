package com.portable.microservices.ms_inventory.movement.infrastructure.publisher;

import com.portable.microservices.ms_inventory.movement.domain.event.MovementCreatedEvent;
import com.portable.microservices.ms_inventory.shared.infrastructure.websocket.WebSocketEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class MovementRabbitPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final WebSocketEventPublisher webSocketEventPublisher;
    // Nombres hardcodeados temporalmente
    private static final String EXCHANGE = "inventory.exchange";
    private static final String ROUTING_KEY = "inventory.movement.created";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishMovementEvent(MovementCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, event);
            log.info("Evento publicado con éxito.");
        } catch (Exception e) {
            log.error("Error al publicar evento de RabbitMQ: {}", e.getMessage());
        }
        webSocketEventPublisher.publishMovementCreated(event);

        if ("SALIDA".equals(event.tipoMovimiento()) || "AJUSTE_NEGATIVO".equals(event.tipoMovimiento())) {
            webSocketEventPublisher.publishHeatmapForMovement(event.locacionId());
        }
        webSocketEventPublisher.publishDashboardRefresh();
    }
}