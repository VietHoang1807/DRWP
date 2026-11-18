CREATE TABLE idn_role
(
  id          UUID         PRIMARY KEY,
  code        VARCHAR(50)  NOT NULL UNIQUE,
  name        VARCHAR(200) NOT NULL,
  description TEXT,
  system      BOOLEAN      NOT NULL DEFAULT false,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE idn_role_permission
(
  role_id       UUID         NOT NULL REFERENCES idn_role (id) ON DELETE CASCADE,
  resource_code VARCHAR(100) NOT NULL,
  -- chỉ bit 0..62 (tài liệu 02 mục 2): BIGINT không âm
  allow_mask    BIGINT       NOT NULL DEFAULT 0 CHECK (allow_mask >= 0),
  deny_mask     BIGINT       NOT NULL DEFAULT 0 CHECK (deny_mask >= 0),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  PRIMARY KEY (role_id, resource_code)
);

CREATE TABLE idn_user_role
(
  subject    VARCHAR(100) NOT NULL,
  role_id    UUID         NOT NULL REFERENCES idn_role (id) ON DELETE CASCADE,
  granted_by VARCHAR(100),
  granted_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  PRIMARY KEY (subject, role_id)
);
CREATE INDEX idn_user_role_role_idx ON idn_user_role (role_id);

-- Tăng mỗi khi thay đổi vai trò/quyền: khóa cache quyền chứa policyVersion nên thu hồi có hiệu lực ngay.
CREATE TABLE idn_policy_version
(
  id      SMALLINT PRIMARY KEY CHECK (id = 1),
  version BIGINT   NOT NULL
);
INSERT INTO idn_policy_version (id, version) VALUES (1, 1);
