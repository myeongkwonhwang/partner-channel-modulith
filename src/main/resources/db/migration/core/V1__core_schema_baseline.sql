-- core_schema baseline (serviceCore 모듈)
-- 자사 주문 (orders) / 송장 (invoices) / processed_event (consumer dedup) 책임.

CREATE TABLE core_schema.orders (
    id                          BIGSERIAL PRIMARY KEY,
    saga_id                     UUID         NOT NULL,
    channel                     VARCHAR(32)  NOT NULL,
    external_order_product_id   VARCHAR(64)  NOT NULL,
    shipment_id                 VARCHAR(64),
    status                      VARCHAR(32)  NOT NULL DEFAULT 'CREATED',
    created_at                  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_orders_channel_ext UNIQUE (channel, external_order_product_id)
);

CREATE TABLE core_schema.invoices (
    id                          BIGSERIAL PRIMARY KEY,
    saga_id                     UUID         NOT NULL,
    channel                     VARCHAR(32)  NOT NULL,
    external_order_product_id   VARCHAR(64)  NOT NULL,
    tracking_number             VARCHAR(64)  NOT NULL,
    delivery_company            VARCHAR(32)  NOT NULL,
    dispatch_status             VARCHAR(32)  NOT NULL DEFAULT 'ATTEMPTED',
    created_at                  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_invoices_channel_ext UNIQUE (channel, external_order_product_id)
);

CREATE TABLE core_schema.processed_event (
    id          BIGSERIAL PRIMARY KEY,
    event_id    VARCHAR(128) NOT NULL,
    processed_at TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uk_core_processed_event UNIQUE (event_id)
);
