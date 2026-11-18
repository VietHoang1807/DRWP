-- Platform-owned tables inside every tenant schema (${tenant_schema}).
-- ADR 019: Spring Modulith event publication registry per tenant schema.
-- Schema copied from spring-modulith-events-jdbc 2.1.x: schemas/v2/schema-postgresql.sql
CREATE TABLE IF NOT EXISTS event_publication
(
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON event_publication USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);

-- ADR 020: transactional outbox, read by Debezium (EventRouter default column names).
CREATE TABLE outbox_event
(
  id             UUID         NOT NULL PRIMARY KEY,
  aggregatetype  VARCHAR(100) NOT NULL,
  aggregateid    VARCHAR(100) NOT NULL,
  type           VARCHAR(150) NOT NULL,
  event_version  INT          NOT NULL DEFAULT 1,
  tenant_id      VARCHAR(40)  NOT NULL,
  correlation_id VARCHAR(64),
  payload        JSONB        NOT NULL,
  occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX outbox_event_occurred_at_idx ON outbox_event (occurred_at);
-- Tenant id written by the app must match this schema's tenant (defence in depth for ADR 022).
ALTER TABLE outbox_event ADD CONSTRAINT outbox_event_tenant_ck CHECK (tenant_id = '${tenant_id}');

-- Idempotent inbox for Kafka consumers (event-worker).
CREATE TABLE inbox_event
(
  consumer    VARCHAR(100) NOT NULL,
  message_id  VARCHAR(100) NOT NULL,
  event_type  VARCHAR(150),
  received_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  PRIMARY KEY (consumer, message_id)
);
