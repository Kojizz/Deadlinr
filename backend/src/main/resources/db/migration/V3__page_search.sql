-- Keep indexing independent of which API writes a page. No existing migration changes.
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE pages ADD COLUMN search_vector tsvector NOT NULL DEFAULT ''::tsvector;
ALTER TABLE pages ADD COLUMN search_title text NOT NULL DEFAULT '';

-- Extract BlockNote text recursively, including nested blocks and table cells.
CREATE FUNCTION deadlinr_page_text(document jsonb) RETURNS text
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    part jsonb;
    entry record;
    words text[] := ARRAY[]::text[];
BEGIN
    IF jsonb_typeof(document) = 'array' THEN
        FOR part IN SELECT value FROM jsonb_array_elements(document) LOOP
            words := array_append(words, deadlinr_page_text(part));
        END LOOP;
    ELSIF jsonb_typeof(document) = 'object' THEN
        IF jsonb_typeof(document->'text') = 'string' THEN
            words := array_append(words, document->>'text');
        END IF;
        FOR entry IN SELECT key, value FROM jsonb_each(document) WHERE key <> 'text' LOOP
            words := array_append(words, deadlinr_page_text(entry.value));
        END LOOP;
    END IF;
    RETURN trim(array_to_string(array_remove(words, ''), ' '));
END;
$$;

CREATE FUNCTION deadlinr_update_page_search() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' OR NEW.content IS DISTINCT FROM OLD.content THEN
        NEW.search_text := deadlinr_page_text(NEW.content);
    END IF;
    NEW.search_title := lower(unaccent(coalesce(NEW.title, '')));
    NEW.search_vector := setweight(to_tsvector('simple', unaccent(coalesce(NEW.title, ''))), 'A')
                      || setweight(to_tsvector('simple', unaccent(coalesce(NEW.search_text, ''))), 'B');
    RETURN NEW;
END;
$$;

CREATE TRIGGER pages_search_update
BEFORE INSERT OR UPDATE OF title, content, search_text ON pages
FOR EACH ROW EXECUTE FUNCTION deadlinr_update_page_search();

-- Re-extract old documents too: some pre-F0 rows never had search_text populated.
UPDATE pages SET search_text = deadlinr_page_text(content);

CREATE INDEX idx_pages_search_vector ON pages USING gin(search_vector) WHERE archived_at IS NULL;
CREATE INDEX idx_pages_search_title ON pages USING gin(search_title gin_trgm_ops) WHERE archived_at IS NULL;

-- Prefix only the lexemes produced by PostgreSQL, never raw user syntax.
-- This preserves web-search phrase, OR and exclusion operators.
CREATE FUNCTION deadlinr_prefix_query(input text) RETURNS tsquery
LANGUAGE sql STABLE AS $$
    SELECT regexp_replace(
        websearch_to_tsquery('simple', unaccent(input))::text,
        '(''(?:[^'']|'''')+'')', '\1:*', 'g'
    )::tsquery;
$$;
