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
