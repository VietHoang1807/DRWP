CREATE TABLE file_metadata
(
  id            UUID         PRIMARY KEY,
  record_id     UUID,                         -- tham chiếu logic tới hồ sơ (module Record)
  original_name VARCHAR(255) NOT NULL,
  content_type  VARCHAR(150) NOT NULL,
  size_bytes    BIGINT       NOT NULL CHECK (size_bytes > 0),
  sha256        VARCHAR(64)  NOT NULL,
  storage_key   VARCHAR(300),
  status        VARCHAR(20)  NOT NULL CHECK (status IN ('QUARANTINED','AVAILABLE','REJECTED','DELETED')),
  scan_result   VARCHAR(100),
  uploaded_by   VARCHAR(100) NOT NULL,
  uploaded_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ
);
CREATE INDEX file_metadata_record_idx ON file_metadata (record_id) WHERE status = 'AVAILABLE';
CREATE INDEX file_metadata_uploader_idx ON file_metadata (uploaded_by, uploaded_at DESC);
