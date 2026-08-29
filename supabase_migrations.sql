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
