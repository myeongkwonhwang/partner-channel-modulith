package io.github.orange2652.partner.channel.core.ordr.infra.logistics;

import io.github.orange2652.partner.channel.core.ordr.application.logistics.LogisticsGateway;
import io.github.orange2652.partner.channel.core.ordr.application.logistics.Shipment;
import io.github.orange2652.partner.channel.core.ordr.application.logistics.ShipmentRequest;
import io.github.orange2652.partner.channel.core.ordr.application.logistics.ShipmentResponse;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 학습 단계 Mock — 항상 OK, shipmentId 는 UUID 로 생성. 멱등: 같은 idempotencyKey 면 같은 shipment 반환.
 *
 * <p>JVM 재기동 시 휘발. 실제 자사 물류 연동(interface table polling / DB write / Kafka)은 후속 라운드 결정.</p>
 */
@Slf4j
@Component
class MockLogisticsGateway implements LogisticsGateway {

    private final Map<UUID, Shipment> store = new ConcurrentHashMap<>();

    @Override
    public ShipmentResponse dispatch(UUID idempotencyKey, ShipmentRequest request) {
        Shipment existing = store.get(idempotencyKey);
        if (existing != null) {
            log.info("mock logistics dispatch idempotent hit idempotencyKey={} channel={} shipmentId={}",
                    idempotencyKey, request.channel(), existing.shipmentId());
            return new ShipmentResponse(existing.shipmentId());
        }
        String shipmentId = "MOCK-" + UUID.randomUUID();
        store.put(idempotencyKey, new Shipment(shipmentId, idempotencyKey));
        log.info("mock logistics dispatch new idempotencyKey={} channel={} externalOrderProductId={} shipmentId={}",
                idempotencyKey, request.channel(), request.externalOrderProductId(), shipmentId);
        return new ShipmentResponse(shipmentId);
    }

    @Override
    public Optional<Shipment> getShipment(UUID idempotencyKey) {
        Shipment shipment = store.get(idempotencyKey);
        if (shipment == null) {
            log.info("mock logistics getShipment miss idempotencyKey={}", idempotencyKey);
            return Optional.empty();
        }
        log.info("mock logistics getShipment hit idempotencyKey={} shipmentId={}",
                idempotencyKey, shipment.shipmentId());
        return Optional.of(shipment);
    }
}
