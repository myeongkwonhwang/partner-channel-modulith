-- logistics_schema baseline (자사 물류 — channelBatch internal polling + serviceCore LogisticsGateway 호출 대상)
-- DB-to-DB interface. 자사 권한 → 멱등 보장 (idempotency_key UNIQUE).

CREATE TABLE logistics_schema.shipment (
    id                          BIGSERIAL PRIMARY KEY,
    idempotency_key             UUID         NOT NULL UNIQUE,
    shipment_id                 VARCHAR(64)  NOT NULL UNIQUE,
    channel                     VARCHAR(32)  NOT NULL,
    external_order_product_id   VARCHAR(64)  NOT NULL,
    tracking_number             VARCHAR(64),
    delivery_company            VARCHAR(32),
    status                      VARCHAR(32)  NOT NULL DEFAULT 'RESERVED',
    created_at                  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_shipment_channel_ext ON logistics_schema.shipment(channel, external_order_product_id);
