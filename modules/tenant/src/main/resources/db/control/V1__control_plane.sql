-- Control plane (schema "platform"): tenant registry, placement, membership, ShedLock, control-plane audit.
CREATE TABLE tenant_registry
(
  tenant_id           VARCHAR(40)  PRIMARY KEY,
  display_name        VARCHAR(200) NOT NULL,
  status              VARCHAR(20)  NOT NULL CHECK (status IN ('PROVISIONING','ACTIVE','SUSPENDED','ARCHIVED')),
  compliance_profiles JSONB        NOT NULL DEFAULT '["COMMERCIAL"]',
  last_error          TEXT,
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tenant_placement
(
  tenant_id         VARCHAR(40) PRIMARY KEY REFERENCES tenant_registry (tenant_id),
  cluster_id        VARCHAR(50) NOT NULL,
  database_id       VARCHAR(50) NOT NULL DEFAULT 'default',
  schema_name       VARCHAR(60) NOT NULL CHECK (schema_name ~ '^tenant_[a-z0-9_]{1,50}$'),
  placement_version BIGINT      NOT NULL DEFAULT 1,
  status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','MIGRATING','RETIRED')),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT tenant_placement_schema_uk UNIQUE (cluster_id, database_id, schema_name)
);

CREATE TABLE tenant_membership
(
  tenant_id    VARCHAR(40)  NOT NULL REFERENCES tenant_registry (tenant_id),
  subject      VARCHAR(100) NOT NULL,
  display_name VARCHAR(200),
  status       VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED')),
  created_by   VARCHAR(100),
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, subject)
);
CREATE INDEX tenant_membership_subject_idx ON tenant_membership (subject);

-- ShedLock (ADR 018)
CREATE TABLE shedlock
(
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMP    NOT NULL,
  locked_at  TIMESTAMP    NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);

-- Audit thay đổi tenant/placement/membership (tài liệu 02 mục 4). Append-only.
CREATE TABLE ctl_audit
(
  id          BIGSERIAL    PRIMARY KEY,
  occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  actor       VARCHAR(100) NOT NULL,
  action      VARCHAR(60)  NOT NULL,
  tenant_id   VARCHAR(40),
  detail      JSONB        NOT NULL DEFAULT '{}'
);

CREATE FUNCTION ctl_audit_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'ctl_audit is append-only';
END $$;
CREATE TRIGGER ctl_audit_no_update BEFORE UPDATE OR DELETE ON ctl_audit
  FOR EACH ROW EXECUTE FUNCTION ctl_audit_immutable();
