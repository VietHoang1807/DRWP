CREATE TABLE meta_form_definition
(
  id          UUID         PRIMARY KEY,
  code        VARCHAR(64)  NOT NULL UNIQUE CHECK (code ~ '^[A-Z][A-Z0-9_]{1,63}$'),
  name        VARCHAR(200) NOT NULL,
  description TEXT,
  status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
  created_by  VARCHAR(100) NOT NULL,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE meta_form_version
(
  id           UUID        PRIMARY KEY,
  form_id      UUID        NOT NULL REFERENCES meta_form_definition (id),
  version_no   INT         NOT NULL CHECK (version_no > 0),
  status       VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','PUBLISHED','DEPRECATED')),
  schema       JSONB       NOT NULL,
  checksum     VARCHAR(64),
  created_by   VARCHAR(100) NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_by VARCHAR(100),
  published_at TIMESTAMPTZ,
  CONSTRAINT meta_form_version_uk UNIQUE (form_id, version_no)
);
-- tối đa một bản nháp mỗi biểu mẫu
CREATE UNIQUE INDEX meta_form_version_one_draft ON meta_form_version (form_id) WHERE status = 'DRAFT';

-- ADR 006 / tài liệu 02 mục 1.4: phiên bản đã phát hành là bất biến, chặn ở tầng DB.
CREATE FUNCTION meta_form_version_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF OLD.status <> 'DRAFT' THEN
      RAISE EXCEPTION 'published form version % is immutable', OLD.id;
    END IF;
    RETURN OLD;
  END IF;
  IF OLD.status <> 'DRAFT' THEN
    IF NEW.schema IS DISTINCT FROM OLD.schema OR NEW.version_no <> OLD.version_no OR NEW.form_id <> OLD.form_id
       OR NEW.checksum IS DISTINCT FROM OLD.checksum THEN
      RAISE EXCEPTION 'published form version % is immutable', OLD.id;
    END IF;
    IF NOT (OLD.status = 'PUBLISHED' AND NEW.status IN ('PUBLISHED','DEPRECATED'))
       AND NOT (OLD.status = 'DEPRECATED' AND NEW.status = 'DEPRECATED') THEN
      RAISE EXCEPTION 'illegal form version status change % -> %', OLD.status, NEW.status;
    END IF;
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER meta_form_version_immutable BEFORE UPDATE OR DELETE ON meta_form_version
  FOR EACH ROW EXECUTE FUNCTION meta_form_version_guard();
