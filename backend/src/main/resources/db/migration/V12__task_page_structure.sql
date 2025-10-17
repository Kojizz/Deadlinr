-- Task↔page structure: track which archive batch cancelled a reminder,
-- so restore only rehabilitates reminders that this archive actually cancelled.
ALTER TABLE reminders ADD COLUMN IF NOT EXISTS archive_batch_id uuid;

-- Fast lookup of open-task counts per page (one grouped query).
CREATE INDEX IF NOT EXISTS idx_work_page ON work_items (page_id) WHERE archived_at IS NULL;
