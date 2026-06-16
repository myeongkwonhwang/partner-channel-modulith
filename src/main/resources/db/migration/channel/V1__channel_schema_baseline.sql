-- channel_schema baseline (MSA partner-channel-msa V1__channel_schema_baseline.sql 와 동일)
-- 외부 채널 raw 적재 / staging_order / outbox / polling cursor 등 channelBatch + channelAdapter 책임.

CREATE TABLE channel_schema.staging_order (
    id                          BIGSERIAL PRIMARY KEY,
    channel                     VARCHAR(32)  NOT NULL,
    external_order_product_id   VARCHAR(64)  NOT NULL,
    raw                         JSONB        NOT NULL,
    status                      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    received_at                 TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_staging_order_channel_ext UNIQUE (channel, external_order_product_id)
);
CREATE INDEX idx_staging_order_channel_status ON channel_schema.staging_order(channel, status);

CREATE TABLE channel_schema.polling_cursor (
    id              BIGSERIAL PRIMARY KEY,
    channel         VARCHAR(32) NOT NULL,
    cursor_type     VARCHAR(32) NOT NULL,
    last_polled_at  TIMESTAMP   NOT NULL,
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uk_polling_cursor UNIQUE (channel, cursor_type)
);

CREATE TABLE channel_schema.processed_event (
    id          BIGSERIAL PRIMARY KEY,
    event_id    VARCHAR(128) NOT NULL,
    processed_at TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uk_channel_processed_event UNIQUE (event_id)
);
