CREATE TABLE rec_record
(
  id                   UUID         PRIMARY KEY,
  record_code          VARCHAR(100) NOT NULL UNIQUE,
  form_code            VARCHAR(64)  NOT NULL,
  -- tham chiếu logic tới phiên bản biểu mẫu (module Metadata): KHÔNG có FK xuyên module (ADR 015)
  form_version_id      UUID         NOT NULL,
  workflow_instance_id UUID,
  status               VARCHAR(20)  NOT NULL CHECK (status IN ('DRAFT','SUBMITTED','IN_PROGRESS','APPROVED','REJECTED','RETURNED','CANCELLED')),
  dynamic_data         JSONB        NOT NULL DEFAULT '{}'::jsonb,
  created_by           VARCHAR(100) NOT NULL,
  created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  submitted_at         TIMESTAMPTZ,
  completed_at         TIMESTAMPTZ,
  submission_count     INT          NOT NULL DEFAULT 0,
  version              BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX rec_record_form_status_idx ON rec_record (form_code, status, created_at DESC);
CREATE INDEX rec_record_owner_idx ON rec_record (created_by, created_at DESC);
CREATE INDEX rec_record_data_gin ON rec_record USING gin (dynamic_data jsonb_path_ops);

CREATE TABLE rec_record_revision
(
  id           UUID         PRIMARY KEY,
  record_id    UUID         NOT NULL REFERENCES rec_record (id) ON DELETE CASCADE,
  revision_no  INT          NOT NULL,
  change_type  VARCHAR(30)  NOT NULL,
  status       VARCHAR(20)  NOT NULL,
  dynamic_data JSONB        NOT NULL,
  changed_by   VARCHAR(100) NOT NULL,
  changed_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT rec_record_revision_uk UNIQUE (record_id, revision_no)
);

CREATE TABLE rec_code_sequence
(
  form_code  VARCHAR(64) NOT NULL,
  year       INT         NOT NULL,
  next_value BIGINT      NOT NULL,
  PRIMARY KEY (form_code, year)
);
