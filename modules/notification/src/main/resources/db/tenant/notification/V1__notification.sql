CREATE TABLE noti_notification
(
  id         UUID         PRIMARY KEY,
  recipient  VARCHAR(100) NOT NULL,
  type       VARCHAR(50)  NOT NULL,
  title      VARCHAR(300) NOT NULL,
  body       TEXT,
  ref_type   VARCHAR(50),
  ref_id     VARCHAR(100),
  read_at    TIMESTAMPTZ,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  -- idempotent khi registry phát lại sự kiện
  CONSTRAINT noti_notification_dedup UNIQUE (recipient, type, ref_id)
);
CREATE INDEX noti_notification_inbox_idx ON noti_notification (recipient, created_at DESC);
CREATE INDEX noti_notification_unread_idx ON noti_notification (recipient) WHERE read_at IS NULL;
