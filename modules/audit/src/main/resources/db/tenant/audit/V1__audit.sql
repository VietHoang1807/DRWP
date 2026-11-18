CREATE TABLE aud_event
(
  id             BIGSERIAL    PRIMARY KEY,
  dedup_key      VARCHAR(64)  NOT NULL UNIQUE,  -- idempotent khi registry phát lại
  occurred_at    TIMESTAMPTZ  NOT NULL,
  recorded_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  actor          VARCHAR(100),
  action         VARCHAR(80)  NOT NULL,
  resource_type  VARCHAR(50),
  resource_id    VARCHAR(100),
  outcome        VARCHAR(20)  NOT NULL CHECK (outcome IN ('SUCCESS','DENIED','FAILED')),
  correlation_id VARCHAR(64),
  detail         JSONB        NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX aud_event_resource_idx ON aud_event (resource_type, resource_id, occurred_at DESC);
CREATE INDEX aud_event_actor_idx ON aud_event (actor, occurred_at DESC);
CREATE INDEX aud_event_time_idx ON aud_event (occurred_at DESC);

CREATE FUNCTION aud_event_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'aud_event is append-only';
END $$;
CREATE TRIGGER aud_event_no_change BEFORE UPDATE OR DELETE ON aud_event
  FOR EACH ROW EXECUTE FUNCTION aud_event_immutable();
