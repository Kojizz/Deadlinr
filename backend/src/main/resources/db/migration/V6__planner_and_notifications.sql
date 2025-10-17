ALTER TABLE work_items ADD COLUMN time_zone varchar(100) NOT NULL DEFAULT 'Asia/Almaty';
ALTER TABLE work_items ADD COLUMN legacy_assignment_id bigint UNIQUE REFERENCES assignments(id) ON DELETE SET NULL;
INSERT INTO work_items(owner_id,kind,title,description,created_at,legacy_assignment_id)
 SELECT owner_id,'ASSIGNMENT',title,coalesce(description,''),created_at,id FROM assignments;
CREATE FUNCTION mirror_assignment_draft() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO work_items(owner_id,kind,title,description,created_at,legacy_assignment_id)
 VALUES(NEW.owner_id,'ASSIGNMENT',NEW.title,coalesce(NEW.description,''),NEW.created_at,NEW.id)
 ON CONFLICT(legacy_assignment_id) DO UPDATE SET title=EXCLUDED.title,description=EXCLUDED.description;
 RETURN NEW;
END $$;
CREATE TRIGGER mirror_assignment AFTER INSERT OR UPDATE ON assignments FOR EACH ROW EXECUTE FUNCTION mirror_assignment_draft();
CREATE TABLE notifications(
 id bigserial PRIMARY KEY, owner_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 item_id bigint REFERENCES work_items(id) ON DELETE CASCADE,
 reminder_id bigint UNIQUE REFERENCES reminders(id) ON DELETE CASCADE,
 title text NOT NULL, body text NOT NULL DEFAULT '', read_at timestamptz, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_owner_created ON notifications(owner_id,created_at DESC);
