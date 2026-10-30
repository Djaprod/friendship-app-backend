-- Private schema: mobile clients use authenticated Artii APIs, never SQL/Data API.
CREATE SCHEMA IF NOT EXISTS artii;
REVOKE ALL ON SCHEMA artii FROM PUBLIC;
CREATE TABLE IF NOT EXISTS artii.schema_versions(version integer PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS artii.users(id text PRIMARY KEY, payload jsonb NOT NULL, position bigint NOT NULL DEFAULT 0);
ALTER TABLE artii.users ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON artii.users FROM PUBLIC;
CREATE TABLE IF NOT EXISTS artii.sessions(id text PRIMARY KEY, payload jsonb NOT NULL, position bigint NOT NULL DEFAULT 0);
ALTER TABLE artii.sessions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON artii.sessions FROM PUBLIC;
CREATE TABLE IF NOT EXISTS artii.friend_requests(id text PRIMARY KEY, payload jsonb NOT NULL, position bigint NOT NULL DEFAULT 0);
ALTER TABLE artii.friend_requests ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON artii.friend_requests FROM PUBLIC;
CREATE TABLE IF NOT EXISTS artii.friends(id text PRIMARY KEY, payload jsonb NOT NULL, position bigint NOT NULL DEFAULT 0);
ALTER TABLE artii.friends ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON artii.friends FROM PUBLIC;
CREATE TABLE IF NOT EXISTS artii.rooms(id text PRIMARY KEY, payload jsonb NOT NULL, position bigint NOT NULL DEFAULT 0);
ALTER TABLE artii.rooms ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON artii.rooms FROM PUBLIC;
CREATE TABLE IF NOT EXISTS artii.messages(id text PRIMARY KEY, payload jsonb NOT NULL, position bigint NOT NULL DEFAULT 0);
ALTER TABLE artii.messages ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON artii.messages FROM PUBLIC;
CREATE TABLE IF NOT EXISTS artii.locations(id text PRIMARY KEY, payload jsonb NOT NULL, position bigint NOT NULL DEFAULT 0);
ALTER TABLE artii.locations ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON artii.locations FROM PUBLIC;
CREATE TABLE IF NOT EXISTS artii.blocks(id text PRIMARY KEY, payload jsonb NOT NULL, position bigint NOT NULL DEFAULT 0);
ALTER TABLE artii.blocks ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON artii.blocks FROM PUBLIC;
CREATE TABLE IF NOT EXISTS artii.reports(id text PRIMARY KEY, payload jsonb NOT NULL, position bigint NOT NULL DEFAULT 0);
ALTER TABLE artii.reports ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON artii.reports FROM PUBLIC;
CREATE TABLE IF NOT EXISTS artii.calls(id text PRIMARY KEY, payload jsonb NOT NULL, position bigint NOT NULL DEFAULT 0);
ALTER TABLE artii.calls ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON artii.calls FROM PUBLIC;
CREATE TABLE IF NOT EXISTS artii.signals(id text PRIMARY KEY, payload jsonb NOT NULL, position bigint NOT NULL DEFAULT 0);
ALTER TABLE artii.signals ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON artii.signals FROM PUBLIC;
CREATE TABLE IF NOT EXISTS artii.encounters(id text PRIMARY KEY, payload jsonb NOT NULL, position bigint NOT NULL DEFAULT 0);
ALTER TABLE artii.encounters ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON artii.encounters FROM PUBLIC;
CREATE TABLE IF NOT EXISTS artii.purchases(id text PRIMARY KEY, payload jsonb NOT NULL, position bigint NOT NULL DEFAULT 0);
ALTER TABLE artii.purchases ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON artii.purchases FROM PUBLIC;
CREATE TABLE IF NOT EXISTS artii.game_invites(id text PRIMARY KEY, payload jsonb NOT NULL, position bigint NOT NULL DEFAULT 0);
ALTER TABLE artii.game_invites ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON artii.game_invites FROM PUBLIC;
CREATE TABLE IF NOT EXISTS artii.game_sessions(id text PRIMARY KEY, payload jsonb NOT NULL, position bigint NOT NULL DEFAULT 0);
ALTER TABLE artii.game_sessions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON artii.game_sessions FROM PUBLIC;
CREATE TABLE IF NOT EXISTS artii.uploads(id text PRIMARY KEY, payload jsonb NOT NULL, position bigint NOT NULL DEFAULT 0);
ALTER TABLE artii.uploads ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON artii.uploads FROM PUBLIC;

ALTER TABLE artii.users ADD COLUMN IF NOT EXISTS username text GENERATED ALWAYS AS (lower(payload->>'username')) STORED;
ALTER TABLE artii.users ADD COLUMN IF NOT EXISTS email text GENERATED ALWAYS AS (nullif(lower(payload->>'email'),'')) STORED;
CREATE UNIQUE INDEX IF NOT EXISTS users_username_unique ON artii.users(username);
CREATE UNIQUE INDEX IF NOT EXISTS users_email_unique ON artii.users(email);
ALTER TABLE artii.friends ADD COLUMN IF NOT EXISTS user_a text GENERATED ALWAYS AS (payload->>0) STORED;
ALTER TABLE artii.friends ADD COLUMN IF NOT EXISTS user_b text GENERATED ALWAYS AS (payload->>1) STORED;
CREATE UNIQUE INDEX IF NOT EXISTS friends_pair_unique ON artii.friends(user_a,user_b);
ALTER TABLE artii.messages ADD COLUMN IF NOT EXISTS room_id text GENERATED ALWAYS AS (payload->>'room') STORED;
ALTER TABLE artii.messages ADD COLUMN IF NOT EXISTS sender_id text GENERATED ALWAYS AS (payload->>'from') STORED;
CREATE INDEX IF NOT EXISTS messages_room_idx ON artii.messages(room_id);
ALTER TABLE artii.locations ADD COLUMN IF NOT EXISTS user_id text GENERATED ALWAYS AS (payload->>'user') STORED;
CREATE UNIQUE INDEX IF NOT EXISTS locations_user_unique ON artii.locations(user_id);
ALTER TABLE artii.game_sessions ADD COLUMN IF NOT EXISTS room_code text GENERATED ALWAYS AS (payload->>'code') STORED;
CREATE UNIQUE INDEX IF NOT EXISTS game_room_code_unique ON artii.game_sessions(room_code);
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM artii.schema_versions WHERE version=1) THEN
  ALTER TABLE artii.users ADD CONSTRAINT username_valid CHECK(username IS NOT NULL AND username ~ '^[a-z][a-z0-9_]{2,23}$');
  ALTER TABLE artii.friends ADD CONSTRAINT friends_distinct CHECK(user_a IS NOT NULL AND user_b IS NOT NULL AND user_a<user_b);
  ALTER TABLE artii.friends ADD CONSTRAINT friends_a_fk FOREIGN KEY(user_a) REFERENCES artii.users(id) DEFERRABLE INITIALLY DEFERRED;
  ALTER TABLE artii.friends ADD CONSTRAINT friends_b_fk FOREIGN KEY(user_b) REFERENCES artii.users(id) DEFERRABLE INITIALLY DEFERRED;
  ALTER TABLE artii.messages ADD CONSTRAINT messages_room_fk FOREIGN KEY(room_id) REFERENCES artii.rooms(id) DEFERRABLE INITIALLY DEFERRED;
  ALTER TABLE artii.messages ADD CONSTRAINT messages_sender_fk FOREIGN KEY(sender_id) REFERENCES artii.users(id) DEFERRABLE INITIALLY DEFERRED;
  ALTER TABLE artii.locations ADD CONSTRAINT locations_user_fk FOREIGN KEY(user_id) REFERENCES artii.users(id) DEFERRABLE INITIALLY DEFERRED;
  ALTER TABLE artii.locations ADD CONSTRAINT locations_coordinates CHECK((payload->>'lat')::double precision BETWEEN -85 AND 85 AND (payload->>'lon')::double precision BETWEEN -180 AND 180);
  INSERT INTO artii.schema_versions(version) VALUES(1);
 END IF;
END $$;
-- No anon/authenticated grants and no policies: service-only data, including hidden roles.

-- Private durable attachment bytes; never included in client state snapshots.
CREATE TABLE IF NOT EXISTS artii.media_chunks (
 media_id text NOT NULL,
 part integer NOT NULL CHECK(part >= 0),
 bytes bytea NOT NULL,
 PRIMARY KEY(media_id,part)
);
ALTER TABLE artii.media_chunks ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON artii.media_chunks FROM PUBLIC;
