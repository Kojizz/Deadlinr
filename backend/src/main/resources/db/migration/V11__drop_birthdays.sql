-- Birthdays are removed as a kind. Reminders/attachments/notifications rows
-- targeting these items cascade with the row delete.
DELETE FROM work_items WHERE kind='BIRTHDAY';

ALTER TABLE work_items DROP CONSTRAINT IF EXISTS work_items_kind_check;
ALTER TABLE work_items ADD CONSTRAINT work_items_kind_check CHECK (kind IN ('TASK','EVENT'));
