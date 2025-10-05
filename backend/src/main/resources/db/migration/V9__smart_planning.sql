CREATE TABLE labels(owner_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,name varchar(40) NOT NULL,color varchar(7) NOT NULL DEFAULT '#2563eb',PRIMARY KEY(owner_id,name));
CREATE TABLE task_templates(id uuid PRIMARY KEY,owner_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,name varchar(80) NOT NULL,steps jsonb NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX idx_templates_owner ON task_templates(owner_id,created_at);
CREATE TABLE work_dependencies(item_id bigint NOT NULL REFERENCES work_items(id) ON DELETE CASCADE,depends_on_id bigint NOT NULL REFERENCES work_items(id) ON DELETE CASCADE,PRIMARY KEY(item_id,depends_on_id),CHECK(item_id<>depends_on_id));
CREATE INDEX idx_dependencies_target ON work_dependencies(depends_on_id);
ALTER TABLE work_items ADD COLUMN completion_due_at timestamptz;
UPDATE work_items SET completion_due_at=due_at WHERE status='DONE';
CREATE FUNCTION remember_completion_due() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.status='DONE' AND (TG_OP='INSERT' OR OLD.status<>'DONE') THEN NEW.completion_due_at=NEW.due_at;END IF;RETURN NEW;END;$$;
CREATE TRIGGER work_completion BEFORE INSERT OR UPDATE ON work_items FOR EACH ROW EXECUTE FUNCTION remember_completion_due();
CREATE TABLE digest_deliveries(owner_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,day date NOT NULL,kind varchar(10) NOT NULL,notification_id bigint REFERENCES notifications(id) ON DELETE SET NULL,email_pending boolean NOT NULL DEFAULT false,email_sent_at timestamptz,attempts int NOT NULL DEFAULT 0,last_attempt_at timestamptz,PRIMARY KEY(owner_id,day,kind));
CREATE TABLE work_history(id bigint PRIMARY KEY,owner_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,undone boolean NOT NULL DEFAULT false,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX idx_history_owner ON work_history(owner_id,id);
CREATE TABLE work_history_rows(history_id bigint NOT NULL REFERENCES work_history(id) ON DELETE CASCADE,item_id bigint NOT NULL,before_state jsonb,after_state jsonb NOT NULL,undo_state jsonb,PRIMARY KEY(history_id,item_id));
CREATE FUNCTION record_work_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF current_setting('deadlinr.skip_history',true)='true' THEN RETURN NEW;END IF;
 DELETE FROM work_history WHERE owner_id=NEW.owner_id AND undone;
 INSERT INTO work_history(id,owner_id) VALUES(txid_current(),NEW.owner_id) ON CONFLICT DO NOTHING;
 INSERT INTO work_history_rows(history_id,item_id,before_state,after_state) VALUES(txid_current(),NEW.id,CASE WHEN TG_OP='UPDATE' THEN to_jsonb(OLD) ELSE NULL END,to_jsonb(NEW))
 ON CONFLICT(history_id,item_id) DO UPDATE SET after_state=excluded.after_state;
 RETURN NEW;
END;$$;
CREATE TRIGGER work_audit AFTER INSERT OR UPDATE ON work_items FOR EACH ROW EXECUTE FUNCTION record_work_history();
