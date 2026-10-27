-- ============================================================
-- Noctra DB Migration Scripts
-- Run these in Supabase SQL Editor BEFORE installing the app
-- ============================================================


-- ============================================================
-- MIGRATION 1: routine_sessions status column
-- ============================================================
-- What this does:
--   1. Adds a new 'status' text column (default 'PENDING')
--   2. Backfills existing rows based on the old 'is_completed' boolean
--   3. Drops the old 'is_completed' column
--
-- Why:
--   Code now uses a 3-state status: PENDING, COMPLETED, MISSED
--   Old schema only had a boolean (true/false)
-- ============================================================

-- Step 1: Add the new status column
ALTER TABLE routine_sessions
ADD COLUMN status TEXT DEFAULT 'PENDING';

-- Step 2: Backfill existing data
-- If is_completed was TRUE → status = 'COMPLETED'
-- If is_completed was FALSE → status = 'MISSED'
UPDATE routine_sessions
SET status = CASE
    WHEN is_completed = TRUE THEN 'COMPLETED'
    WHEN is_completed = FALSE THEN 'MISSED'
    ELSE 'PENDING'  -- fallback for NULLs
END;

-- Step 3: Drop the old boolean column
ALTER TABLE routine_sessions
DROP COLUMN is_completed;


-- ============================================================
-- MIGRATION 2: reward_ledger column rename
-- ============================================================
-- What this does:
--   Renames 'devolution_pending' to 'has_first_miss'
--
-- Why:
--   The old name was misleading. The boolean tracks whether
--   the user has missed their first session (for streak
--   devolution logic), not whether devolution is "pending"
-- ============================================================

ALTER TABLE reward_ledger
RENAME COLUMN devolution_pending TO has_first_miss;


-- ============================================================
-- MIGRATION 3: friendships + encouragement_reactions tables
-- ============================================================
-- What this does:
--   1. Creates the friendships table for friend requests/connections
--   2. Creates the encouragement_reactions table for daily hearts
--   3. Enables Realtime on friendships for live leaderboard updates
--   4. Sets up RLS policies so users only see their own data
-- ============================================================

-- Step 1: Create friendships table
CREATE TABLE IF NOT EXISTS friendships (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  requester_id TEXT REFERENCES user_profiles(user_id) ON DELETE CASCADE,
  receiver_id TEXT REFERENCES user_profiles(user_id) ON DELETE CASCADE,
  status TEXT DEFAULT 'PENDING',
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

-- Step 2: Create encouragement_reactions table
CREATE TABLE IF NOT EXISTS encouragement_reactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  sender_id UUID REFERENCES user_profiles(user_id) ON DELETE CASCADE,
  receiver_id UUID REFERENCES user_profiles(user_id) ON DELETE CASCADE,
  reaction_date DATE DEFAULT CURRENT_DATE,
  UNIQUE(sender_id, receiver_id, reaction_date)
);

-- Step 3: Enable Realtime on friendships table
ALTER PUBLICATION supabase_realtime ADD TABLE friendships;

-- Step 4: RLS policies for friendships
ALTER TABLE friendships ENABLE ROW LEVEL SECURITY;

CREATE POLICY "friendships_select" ON friendships
  FOR SELECT USING (auth.uid() = requester_id OR auth.uid() = receiver_id);

CREATE POLICY "friendships_insert" ON friendships
  FOR INSERT WITH CHECK (auth.uid() = requester_id);

CREATE POLICY "friendships_update" ON friendships
  FOR UPDATE USING (auth.uid() = requester_id OR auth.uid() = receiver_id);

CREATE POLICY "friendships_delete" ON friendships
  FOR DELETE USING (auth.uid() = requester_id OR auth.uid() = receiver_id);

-- Step 5: RLS policies for encouragement_reactions
ALTER TABLE encouragement_reactions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "reactions_select" ON encouragement_reactions
  FOR SELECT USING (auth.uid() = sender_id OR auth.uid() = receiver_id);

CREATE POLICY "reactions_insert" ON encouragement_reactions
  FOR INSERT WITH CHECK (auth.uid() = sender_id);

-- ============================================================
-- MIGRATION 4: onboarding draft columns on user_profiles
-- ============================================================
-- What this does:
--   1. Adds draft_bedtime + draft_activity_ids for mid-onboarding resume.
--      Drafts never touch the active routine config — only the Summary
--      writes that. Covered by existing own-row RLS policies, no new
--      policies needed.
-- ============================================================

ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS draft_bedtime TEXT;
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS draft_activity_ids JSONB;

-- ============================================================
-- MIGRATION 5: onboarding completion timestamp on user_profiles
-- ============================================================
-- What this does:
--   1. Adds onboarding_completed_at for audit anchoring + Week N.
--      Set once by markOnboardingComplete(). Covered by existing
--      own-row RLS policies, no new policies needed.
-- ============================================================

ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMPTZ;

-- ============================================================
-- MIGRATION 6: created_at fallback on user_profiles
-- ============================================================
-- What this does:
--   1. Adds created_at if absent — fallback anchor for Week N and
--      window starts when onboarding_completed_at is NULL (legacy
--      rows). Nullable-safe: no default overridden on insert.
-- ============================================================

ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT now();

-- ============================================================
-- MIGRATION 7: per-night bedtime snapshot on sleep_records
-- ============================================================
-- What this does:
--   1. Adds target_bedtime TEXT ("HH:mm:ss") — the profile bedtime live
--      when the night synced, stamped first-write-wins by the sync.
--      Lets adherence judge each night against its own target instead of
--      the current one. Nullable: old rows read NULL = fall back to the
--      current target, no backfill required. Covered by existing own-row
--      RLS policies, no new policies needed.
-- ============================================================

ALTER TABLE sleep_records ADD COLUMN IF NOT EXISTS target_bedtime TEXT;

-- ============================================================
-- MIGRATION 8: XP economy columns on sleep_records
-- ============================================================
-- What this does:
--   1. Adds xp_accrued — XP frozen at first score computation.
--      The popup displays exactly this number, so display and
--      award can never disagree.
--   2. Adds xp_settled — one-way latch flipped when the morning
--      popup pays out. Settlement reads only xp_settled = false
--      rows, so a night can never be paid twice (guards the
--      popup re-show bug on process kill + reconnect).
--   Defaults keep old rows valid: pre-feature rows read
--   (0, false) = "nothing to settle". No backfill needed.
--   Covered by existing own-row RLS policies.
-- ============================================================

ALTER TABLE sleep_records ADD COLUMN IF NOT EXISTS xp_accrued INT DEFAULT 0;
ALTER TABLE sleep_records ADD COLUMN IF NOT EXISTS xp_settled BOOLEAN DEFAULT false;

-- ============================================================
-- MIGRATION 9: shop_items + user_inventory (companion outfits)
-- ============================================================
-- What this does:
--   1. Creates shop_items — the outfit catalog.
--   2. Creates user_inventory — what each user owns.
--   3. Adds user_profiles.outfit_equipped — currently worn item.
--   4. Creates the on_profile_created trigger: grants the default
--      outfit at signup, right after the profile row lands.
--
-- HISTORY (recorded because this shipped in three buggy steps,
-- each discovered only in production — do not repeat):
--   v1: BEFORE INSERT + SECURITY INVOKER → RLS 42501. At signup
--       the client has no session yet (email confirmation means
--       tokens only arrive at verification), so auth.uid() is
--       null and the own-row insert policy rejects the grant.
--   v2: added SECURITY DEFINER → surfaced the next layer:
--       BEFORE INSERT fires before the profile row exists, so
--       the inventory insert hit user_inventory_user_id_fkey
--       (referencing a user not yet in user_profiles).
--   v3 (below): AFTER INSERT + SECURITY DEFINER — fires after
--       the profile row lands, runs with owner rights, updates
--       outfit_equipped via UPDATE (AFTER triggers can't edit
--       NEW). Verified working end-to-end.
--
-- SECURITY NOTE on the DEFINER: unavoidable here. Signup-time
-- runs have no user identity, so SOMEONE must act on the new
-- user's behalf. Mitigations in place: no arguments, pinned
-- search_path, body touches exactly one shop read + one
-- inventory insert + one profile update, callable only by the
-- trigger (not exposed over the API).
-- ============================================================

-- Step 1: Shop catalog
CREATE TABLE IF NOT EXISTS shop_items (
  item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  label TEXT NOT NULL,
  description TEXT DEFAULT '',
  category TEXT DEFAULT 'outfit',
  token_cost INT DEFAULT 0,
  preview_asset TEXT,
  item_asset TEXT UNIQUE NOT NULL,
  sort_order INT DEFAULT 0
);

-- Step 2: What users own
CREATE TABLE IF NOT EXISTS user_inventory (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id TEXT REFERENCES user_profiles(user_id) ON DELETE CASCADE,
  item_id UUID REFERENCES shop_items(item_id) ON DELETE CASCADE,
  purchased_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE(user_id, item_id)
);

-- Step 3: Currently worn outfit (nullable — null = app default)
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS outfit_equipped TEXT;

-- Step 4: RLS — users see and buy only their own rows
ALTER TABLE shop_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_inventory ENABLE ROW LEVEL SECURITY;

-- Catalog is world-readable (prices must render pre-purchase);
-- writes are dashboard-only (no client policy = denied).
DO $$ BEGIN
  CREATE POLICY "shop_items_select" ON shop_items FOR SELECT USING (true);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE POLICY "inventory_select" ON user_inventory
    FOR SELECT USING (auth.uid()::text = user_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE POLICY "inventory_insert" ON user_inventory
    FOR INSERT WITH CHECK (auth.uid()::text = user_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE POLICY "inventory_delete" ON user_inventory
    FOR DELETE USING (auth.uid()::text = user_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- Step 5: Signup grant (the trigger — see history block above)
CREATE OR REPLACE FUNCTION public.equip_default_outfit()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path = public
AS $function$
DECLARE
  default_id UUID;
BEGIN
  SELECT item_id INTO default_id FROM shop_items WHERE item_asset = 'default' LIMIT 1;
  IF default_id IS NOT NULL THEN
    INSERT INTO user_inventory (user_id, item_id, purchased_at)
    VALUES (NEW.user_id, default_id, NOW());
    UPDATE user_profiles SET outfit_equipped = default_id WHERE user_id = NEW.user_id;
  END IF;
  RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS on_profile_created ON public.user_profiles;
CREATE TRIGGER on_profile_created
  AFTER INSERT ON public.user_profiles
  FOR EACH ROW
  EXECUTE FUNCTION equip_default_outfit();

-- Step 6: Catalog seed (all 11 outfits, IDs preserved from production so
-- existing user_inventory rows and outfit_equipped references stay valid).
-- ON CONFLICT on item_asset (UNIQUE): re-running against prod is a no-op.
-- The 'default' row is required — the signup trigger grants it by item_asset.
INSERT INTO shop_items (item_id, label, description, category, token_cost, preview_asset, item_asset, sort_order) VALUES
  ('761478ef-06f2-4a30-8be3-68459c3e0534', 'Default',  'Default skin',    'OUTFIT', 0,   '', 'default',  1),
  ('7bde04bf-da5d-4cb6-99a0-40a58ca8131b', 'Pajamas',  'Cozy pajamas',    'OUTFIT', 100, '', 'pajamas',  2),
  ('ebefa437-4e00-499f-bda2-e11e8df9ae62', 'Office',   'Office attire',   'OUTFIT', 100, '', 'office',   3),
  ('6fe7a1c8-7c9f-448e-adce-118d6e363dd8', 'Fairy',    'Fairy outfit',    'OUTFIT', 150, '', 'fairy',    4),
  ('5f886087-9774-493e-bd89-718f77edad2c', 'Boom',     'Boom outfit',     'OUTFIT', 150, '', 'boom',     5),
  ('d0cb1903-d9c2-4e98-bca0-483acca817f5', 'Cozy',     'Cozy vibes',      'OUTFIT', 50,  '', 'cozy',     6),
  ('d50d2ba7-0187-43d0-ad3f-3c537a43eb14', 'Casual',   'Casual wear',     'OUTFIT', 200, '', 'casual',   7),
  ('c4be44f7-97a2-4c5a-ac5a-77edf8c8d304', 'Coquette', 'Coquette style',  'OUTFIT', 250, '', 'coquette', 8),
  ('880144ae-ee4a-4af8-8d2a-6bc2088ebc63', 'Alt',      'Alt aesthetic',   'OUTFIT', 250, '', 'alt',      9),
  ('f08025e6-d246-4e3d-a198-6d87e0b02c56', 'Swaggy',   'Swaggy look',     'OUTFIT', 300, '', 'swaggy',   10),
  ('919039e2-4e63-4029-9b2f-140669798d43', 'Brazil',   'Brazilian vibes', 'OUTFIT', 300, '', 'brazil',   11)
ON CONFLICT (item_asset) DO NOTHING;

-- ============================================================
-- MIGRATION 10: activity_library (wind-down activity catalog)
-- ============================================================
-- What this does:
--   1. Creates activity_library — the read-only catalog behind
--      onboarding's activity picker and routine execution.
--   2. Seeds all 10 activities with production IDs preserved.
--
-- WHY THIS TABLE IS LOAD-BEARING (not just data):
--   App logic keys off exact `label` strings — ActivityIllustrations,
--   TimesUpTransitionFragment, and RoutineStartFragment all match
--   labels verbatim. Changing a label string silently breaks
--   illustrations and type-specific UI, so: never edit these labels
--   without an app-side audit. The `instruction` strings are also
--   user-facing (shown during execution).
--
-- Writes: dashboard/backend only — the app treats this as
-- read-only (RoutineRepository). No client insert/update/delete
-- policies = denied by default. Select is open because onboarding
-- must read it pre-routine; there is nothing sensitive in it.
-- ============================================================

CREATE TABLE IF NOT EXISTS activity_library (
  activity_id UUID PRIMARY KEY,
  label TEXT NOT NULL,
  description TEXT DEFAULT '',
  instruction TEXT DEFAULT '',
  activity_type TEXT NOT NULL,          -- JOURNAL | PHYS | AUDIO (app-switched)
  default_duration_minutes INT DEFAULT 5,
  icon_asset TEXT,
  sort_order INT DEFAULT 0
);

ALTER TABLE activity_library ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  CREATE POLICY "activity_library_select" ON activity_library FOR SELECT USING (true);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

INSERT INTO activity_library (activity_id, label, description, instruction, activity_type, default_duration_minutes, icon_asset, sort_order) VALUES
  ('9eb4d9f7-0443-4912-afae-0fd12f1f0361', 'Slow-Paced Breathing',              'Guided breathing with inhale, hold, exhale intervals to lower arousal.',                              'Follow the breathing circle. Inhale for 4, hold for 7, exhale for 8.',                              'PHYS',   5,  'ic_breathing',   1),
  ('4c2e4862-4fcb-4698-ac8e-e913f5bb651e', 'White/Pink Noise',                  'Low-stimulus ambient audio to mask environmental sounds.',                                             'Play the audio and close your eyes. Audio cannot be paused.',                                       'AUDIO',  10, 'ic_noise',       2),
  ('04c00030-062a-4c36-ac8f-1f8a5d22e8c2', 'Gratitude Journaling',              'Reflect on three things you are grateful for tonight.',                                               'Write freely. Your text is not saved — this is for reflection only.',                                'JOURNAL', 5,  'ic_journal',     3),
  ('befe6fb9-9322-4c9d-8314-bcdfd411e1f9', 'Progressive Muscle Relaxation',     'Tense and release muscle groups to discharge physical tension.',                                      'Follow each cycle: tense for 5 seconds, then release for 10 seconds.',                              'PHYS',   8,  'ic_pmr',         4),
  ('ff1f5195-e972-4d47-a5df-3e4d771a0933', 'Bedtime Stretching',                'Five gentle stretches to release tension before bed.',                                                'Move through each stretch slowly. Hold each one for 30 seconds.',                                   'PHYS',   7,  'ic_stretch',     5),
  ('6e3dff06-28e5-434b-a888-ac4c816f656f', 'Mindfulness',                       'Brief guided meditation with nature-themed visuals and audio.',                                       'Sit comfortably. Focus on the audio and let thoughts pass.',                                        'AUDIO',  10, 'ic_mindfulness', 6),
  ('4b349a14-4535-4677-9f23-e61b35090bb5', 'Reading',                           'Read for a while to wind down before sleep.',                                                         'Pick up a physical book and read until the timer ends.',                                            'PHYS',   15, 'ic_reading',     7),
  ('7f0d16ce-b715-46b0-b4e0-0eee001287f6', 'Warm Shower',                       'A warm shower to relax muscles and signal sleep onset.',                                              'Take a warm shower. Return to the app when finished.',                                              'PHYS',   10, 'ic_shower',      8),
  ('24a8228b-f343-4593-9eb7-df8caaf30367', 'Bedtime To-Do List Writing',        'Write tomorrow''s tasks somewhere outside the app to clear mental clutter.',                          'Use a notebook or notes app to write tomorrow''s tasks. Not in this app.',                          'JOURNAL', 5,  'ic_todo',        9),
  ('cfe3b4e2-cc1e-42c5-8f96-10f4e2067d3b', 'Low-Stimulus Audio Listening',      'Gently guided meditation audio to quiet a busy mind before sleep.',                                   'Lie back and breathe slowly. Listen to the soothing rhythm of the audio.',                          'AUDIO',  10, 'ic_meditation',  10)
ON CONFLICT (activity_id) DO NOTHING;
-- ── Extra columns that already exist in the live DB ─────────────────────
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS health_connect_granted BOOLEAN;
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS hr_baseline_bpm NUMERIC;
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS is_email_verified BOOLEAN;
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS draft_bedtime TEXT;
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS draft_activity_ids JSONB;
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS outfit_equipped UUID;

ALTER TABLE sleep_records ADD COLUMN IF NOT EXISTS target_bedtime TEXT;
ALTER TABLE sleep_records ADD COLUMN IF NOT EXISTS xp_accrued INT DEFAULT 0;
ALTER TABLE sleep_records ADD COLUMN IF NOT EXISTS xp_settled BOOLEAN DEFAULT false;

-- New tables that are already present in the DB ─────────────────────────
CREATE TABLE IF NOT EXISTS routine_configurations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT,
    activity_sequence JSONB,
    total_duration_minutes INT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS monthly_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT,
    year_month TEXT,
    avg_adherence_rate NUMERIC,
    routines_completed INT,
    avg_sleep_quality_score NUMERIC,
    week1_adherence_rate NUMERIC,
    week2_adherence_rate NUMERIC,
    week3_adherence_rate NUMERIC,
    week4_adherence_rate NUMERIC,
    month_insight_text TEXT,
    report_storage_path TEXT,
    generated_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS app_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    run_type TEXT,
    event_detail TEXT,
    event_date DATE,
    event_time TIME,
    details JSONB,
    created_at TIMESTAMPTZ,
    parent_id UUID
);

-- Row‑level security for the new tables (own‑row pattern) ───────────────
ALTER TABLE routine_configurations ENABLE ROW LEVEL SECURITY;
CREATE POLICY "rc_select" ON routine_configurations FOR SELECT USING (auth.uid()::text = user_id);

ALTER TABLE monthly_reports ENABLE ROW LEVEL SECURITY;
CREATE POLICY "mr_select" ON monthly_reports FOR SELECT USING (auth.uid()::text = user_id);

ALTER TABLE app_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY "al_select" ON app_log FOR SELECT USING (auth.uid()::text = user_id);

-- Migration 11: remove unused tables/columns (monthly_reports, health_connect_granted, hr_baseline_bpm)
DROP TABLE IF EXISTS monthly_reports;
ALTER TABLE user_profiles DROP COLUMN IF EXISTS health_connect_granted;
ALTER TABLE user_profiles DROP COLUMN IF EXISTS hr_baseline_bpm;
