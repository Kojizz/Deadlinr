ALTER TABLE pages ADD COLUMN archive_batch_id uuid;
CREATE INDEX idx_pages_owner_archived ON pages(owner_id,archived_at);
CREATE INDEX idx_pages_owner_parent_active ON pages(owner_id,parent_id,position) WHERE archived_at IS NULL;
CREATE TABLE work_items (
 id bigserial PRIMARY KEY, owner_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 kind varchar(20) NOT NULL DEFAULT 'TASK' CHECK(kind IN ('TASK','EVENT','ASSIGNMENT','BIRTHDAY')),
 title varchar(500) NOT NULL, description text NOT NULL DEFAULT '',
 page_id bigint REFERENCES pages(id) ON DELETE CASCADE,
 course_id bigint REFERENCES courses(id) ON DELETE SET NULL,
 parent_id bigint REFERENCES work_items(id) ON DELETE CASCADE,
 status varchar(20) NOT NULL DEFAULT 'TODO' CHECK(status IN ('TODO','IN_PROGRESS','DONE','SOMEDAY')),
 priority smallint NOT NULL DEFAULT 3 CHECK(priority BETWEEN 1 AND 4),
 due_at timestamptz, start_at timestamptz, all_day boolean NOT NULL DEFAULT false,
 hard_deadline boolean NOT NULL DEFAULT false, location text NOT NULL DEFAULT '', link text NOT NULL DEFAULT '',
 repeat_rule varchar(20) NOT NULL DEFAULT 'NONE', labels jsonb NOT NULL DEFAULT '[]',
 reminder_minutes jsonb NOT NULL DEFAULT '[60]', position double precision NOT NULL DEFAULT 0,
 completed_at timestamptz, archived_at timestamptz, archive_batch_id uuid,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_work_owner_due ON work_items(owner_id,due_at) WHERE archived_at IS NULL;
CREATE INDEX idx_work_owner_status_priority ON work_items(owner_id,status,priority) WHERE archived_at IS NULL;
CREATE INDEX idx_work_parent ON work_items(parent_id);
CREATE INDEX idx_work_archive ON work_items(owner_id,archived_at);
CREATE TABLE reminders(
 id bigserial PRIMARY KEY, owner_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 item_id bigint REFERENCES work_items(id) ON DELETE CASCADE, page_id bigint REFERENCES pages(id) ON DELETE CASCADE,
 scheduled_at timestamptz NOT NULL, sent_at timestamptz, cancelled boolean NOT NULL DEFAULT false,
 category varchar(30) NOT NULL DEFAULT 'REMINDER', UNIQUE(item_id,scheduled_at,category)
);
CREATE INDEX idx_reminders_pending ON reminders(scheduled_at) WHERE sent_at IS NULL AND NOT cancelled;
CREATE TABLE attachments(
 id uuid PRIMARY KEY, owner_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 item_id bigint REFERENCES work_items(id) ON DELETE CASCADE, page_id bigint REFERENCES pages(id) ON DELETE CASCADE,
 name varchar(255) NOT NULL, size bigint NOT NULL CHECK(size>=0), content_type varchar(100) NOT NULL,
 uploaded_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE file_deletions(id uuid PRIMARY KEY, queued_at timestamptz NOT NULL DEFAULT now());
