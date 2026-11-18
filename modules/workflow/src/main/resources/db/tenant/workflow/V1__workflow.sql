CREATE TABLE wf_definition
(
  id          UUID         PRIMARY KEY,
  code        VARCHAR(64)  NOT NULL UNIQUE CHECK (code ~ '^[A-Z][A-Z0-9_]{1,63}$'),
  name        VARCHAR(200) NOT NULL,
  description TEXT,
  form_code   VARCHAR(64)  NOT NULL,
  status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
  created_by  VARCHAR(100) NOT NULL,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- mỗi loại hồ sơ (form_code) có tối đa một quy trình ACTIVE
CREATE UNIQUE INDEX wf_definition_form_active ON wf_definition (form_code) WHERE status = 'ACTIVE';

CREATE TABLE wf_version
(
  id            UUID         PRIMARY KEY,
  definition_id UUID         NOT NULL REFERENCES wf_definition (id),
  version_no    INT          NOT NULL CHECK (version_no > 0),
  status        VARCHAR(20)  NOT NULL CHECK (status IN ('DRAFT','PUBLISHED','DEPRECATED')),
  spec          JSONB        NOT NULL,
  checksum      VARCHAR(64),
  created_by    VARCHAR(100) NOT NULL,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  published_by  VARCHAR(100),
  published_at  TIMESTAMPTZ,
  CONSTRAINT wf_version_uk UNIQUE (definition_id, version_no)
);
CREATE UNIQUE INDEX wf_version_one_draft ON wf_version (definition_id) WHERE status = 'DRAFT';

CREATE FUNCTION wf_version_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF OLD.status <> 'DRAFT' THEN
      RAISE EXCEPTION 'published workflow version % is immutable', OLD.id;
    END IF;
    RETURN OLD;
  END IF;
  IF OLD.status <> 'DRAFT' THEN
    IF NEW.spec IS DISTINCT FROM OLD.spec OR NEW.version_no <> OLD.version_no OR NEW.definition_id <> OLD.definition_id
       OR NEW.checksum IS DISTINCT FROM OLD.checksum THEN
      RAISE EXCEPTION 'published workflow version % is immutable', OLD.id;
    END IF;
    IF NOT (OLD.status = 'PUBLISHED' AND NEW.status IN ('PUBLISHED','DEPRECATED'))
       AND NOT (OLD.status = 'DEPRECATED' AND NEW.status = 'DEPRECATED') THEN
      RAISE EXCEPTION 'illegal workflow version status change % -> %', OLD.status, NEW.status;
    END IF;
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER wf_version_immutable BEFORE UPDATE OR DELETE ON wf_version
  FOR EACH ROW EXECUTE FUNCTION wf_version_guard();

CREATE TABLE wf_instance
(
  id              UUID         PRIMARY KEY,
  definition_id   UUID         NOT NULL REFERENCES wf_definition (id),
  definition_code VARCHAR(64)  NOT NULL,
  version_id      UUID         NOT NULL REFERENCES wf_version (id),
  version_no      INT          NOT NULL,
  record_id       UUID         NOT NULL,      -- tham chiếu logic tới hồ sơ (module Record), không FK xuyên module
  record_code     VARCHAR(100) NOT NULL,
  record_owner    VARCHAR(100) NOT NULL,
  trigger_id      UUID         NOT NULL UNIQUE, -- RecordSubmitted.submissionId: idempotent khi registry phát lại
  status          VARCHAR(20)  NOT NULL CHECK (status IN ('RUNNING','COMPLETED','CANCELLED')),
  current_state   VARCHAR(50)  NOT NULL,
  outcome         VARCHAR(20),
  started_by      VARCHAR(100) NOT NULL,
  started_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  ended_at        TIMESTAMPTZ,
  version         BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX wf_instance_one_running ON wf_instance (record_id) WHERE status = 'RUNNING';
CREATE INDEX wf_instance_record_idx ON wf_instance (record_id, started_at DESC);

CREATE TABLE wf_task
(
  id               UUID         PRIMARY KEY,
  instance_id      UUID         NOT NULL REFERENCES wf_instance (id),
  state_code       VARCHAR(50)  NOT NULL,
  name             VARCHAR(200) NOT NULL,
  assignee_type    VARCHAR(10)  NOT NULL CHECK (assignee_type IN ('USER','ROLE')),
  assignee_value   VARCHAR(100) NOT NULL,
  status           VARCHAR(20)  NOT NULL CHECK (status IN ('OPEN','COMPLETED','CANCELLED')),
  due_at           TIMESTAMPTZ,
  completed_by     VARCHAR(100),
  completed_action VARCHAR(50),
  comment          TEXT,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  completed_at     TIMESTAMPTZ,
  version          BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX wf_task_inbox_idx ON wf_task (assignee_type, assignee_value) WHERE status = 'OPEN';
CREATE INDEX wf_task_instance_idx ON wf_task (instance_id);
CREATE INDEX wf_task_due_idx ON wf_task (due_at) WHERE status = 'OPEN' AND due_at IS NOT NULL;
-- một instance chỉ có một task OPEN tại một thời điểm (quy trình tuần tự)
CREATE UNIQUE INDEX wf_task_one_open ON wf_task (instance_id) WHERE status = 'OPEN';

CREATE TABLE wf_transition_history
(
  id              UUID         PRIMARY KEY,
  instance_id     UUID         NOT NULL REFERENCES wf_instance (id),
  seq             INT          NOT NULL,
  task_id         UUID,
  from_state      VARCHAR(50),
  to_state        VARCHAR(50)  NOT NULL,
  transition_code VARCHAR(50),
  automatic       BOOLEAN      NOT NULL DEFAULT false,
  actor           VARCHAR(100) NOT NULL,
  comment         TEXT,
  occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT wf_transition_history_seq_uk UNIQUE (instance_id, seq)
);
-- không thể xử lý cùng một task hai lần (chống duplicate transition)
CREATE UNIQUE INDEX wf_transition_history_task_uk ON wf_transition_history (task_id) WHERE task_id IS NOT NULL AND automatic = false;
