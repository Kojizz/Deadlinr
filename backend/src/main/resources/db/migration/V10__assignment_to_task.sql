-- Assignment drafts become regular tasks before the assignments table goes away.
-- V6 mirrored existing rows into work_items (kind='ASSIGNMENT', legacy_assignment_id).
-- Any drafts missed by the mirror are backfilled here. Reminders, attachments and
-- notifications already reference the work_items rows, so they keep targeting the
-- same (now migrated) tasks after the kind update.
INSERT INTO work_items(owner_id,kind,title,description,created_at,legacy_assignment_id)
SELECT a.owner_id,'TASK',a.title,coalesce(a.description,''),a.created_at,a.id
FROM assignments a
WHERE NOT EXISTS (SELECT 1 FROM work_items w WHERE w.legacy_assignment_id = a.id);

UPDATE work_items SET kind='TASK', version=version+1 WHERE kind='ASSIGNMENT';

DROP TRIGGER IF EXISTS mirror_assignment ON assignments;
DROP FUNCTION IF EXISTS mirror_assignment_draft();
ALTER TABLE work_items DROP COLUMN IF EXISTS legacy_assignment_id;
DROP TABLE assignments;

ALTER TABLE work_items DROP CONSTRAINT IF EXISTS work_items_kind_check;
ALTER TABLE work_items ADD CONSTRAINT work_items_kind_check CHECK (kind IN ('TASK','EVENT','BIRTHDAY'));
