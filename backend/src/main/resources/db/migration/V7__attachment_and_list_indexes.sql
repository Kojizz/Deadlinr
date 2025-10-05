CREATE INDEX idx_attachments_item ON attachments(item_id);
CREATE INDEX idx_attachments_owner ON attachments(owner_id);
CREATE INDEX idx_work_course ON work_items(owner_id,course_id) WHERE archived_at IS NULL;
CREATE INDEX idx_work_labels ON work_items USING gin(labels);
CREATE INDEX idx_work_batch ON work_items(owner_id,archive_batch_id) WHERE archived_at IS NOT NULL;
ALTER TABLE attachments ADD CONSTRAINT attachment_target CHECK(item_id IS NOT NULL OR page_id IS NOT NULL);
