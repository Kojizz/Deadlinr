CREATE TABLE user_settings(owner_id bigint PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,preferences jsonb NOT NULL DEFAULT '{}');
CREATE TABLE saved_views(id uuid PRIMARY KEY,owner_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,name varchar(80) NOT NULL,filters jsonb NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX idx_saved_views_owner ON saved_views(owner_id,created_at);
