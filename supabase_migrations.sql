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
-- MIGRATION 5: analytics eligibility columns on user_profiles
-- ============================================================
-- What this does:
--   1. Adds onboarding_completed_at. ANALYTICS_SPEC.md section 3
--      ("Eligibility") requires knowing when onboarding finished so a
--      night can be excluded if the routine window closed before the
--      user ever had the app configured (e.g. onboarded at 11 PM with
--      an 8 PM target). Such a night renders as a neutral hollow cell
--      and is left out of every rate and average, so nobody's first
--      mark is for hours that passed before they arrived.
--   2. Adds created_at if absent. Section 3 also anchors the
--      "Week N" journey counter and the 7-day/30-day window start to
--      the first recorded data, falling back to created_at only when
--      no sleep_records or routine_sessions rows exist yet.
--
-- Both are nullable, so no default is overridden on insert and no
-- backfill is required. Rows predating this migration have a NULL
-- onboarding_completed_at; the client then falls back to created_at,
-- and failing that treats every night as eligible so that real
-- historical data is never hidden. Owned-row RLS already applies.
-- ============================================================

ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMPTZ;
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT now();
