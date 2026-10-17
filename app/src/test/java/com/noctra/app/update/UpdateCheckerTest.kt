package com.noctra.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Version comparison is the one piece of the updater with no device and no
 * network to test it — a subtle bug here would either nag users forever
 * (always "newer") or silently hide every release (never "newer"), so it's
 * pinned down with cases instead of eyeballed.
 */
class UpdateCheckerTest {

    @Test
    fun `patch bump is newer`() {
        assertTrue(UpdateChecker.isNewer("1.2", "1.1"))
    }

    @Test
    fun `equal versions are never newer`() {
        assertFalse(UpdateChecker.isNewer("1.1", "1.1"))
        assertFalse(UpdateChecker.isNewer("1.1.0", "1.1"))
        assertFalse(UpdateChecker.isNewer("1.1", "1.1.0"))
    }

    @Test
    fun `older release is not newer`() {
        assertFalse(UpdateChecker.isNewer("1.1", "1.2"))
        assertFalse(UpdateChecker.isNewer("0.9", "1.0"))
    }

    @Test
    fun `compares numerically not lexicographically`() {
        // String ordering would claim "1.9" > "1.10" — it must not.
        assertTrue(UpdateChecker.isNewer("1.10", "1.9"))
        assertFalse(UpdateChecker.isNewer("1.9", "1.10"))
    }

    @Test
    fun `major bump wins regardless of lower segments`() {
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.9.9"))
        assertFalse(UpdateChecker.isNewer("1.2.0", "1.2.1"))
    }

    @Test
    fun `leading v on the tag is tolerated`() {
        assertTrue(UpdateChecker.isNewer("v1.2", "1.1"))
        assertFalse(UpdateChecker.isNewer("v1.1", "1.1"))
    }

    @Test
    fun `prerelease suffix is ignored for comparison`() {
        // GitHub's /releases/latest excludes prereleases anyway, but a tag
        // like v1.2-hotfix must not confuse the compare.
        assertTrue(UpdateChecker.isNewer("1.2-beta", "1.1"))
        assertFalse(UpdateChecker.isNewer("1.1-hotfix", "1.1"))
    }

    @Test
    fun `unparseable segment counts as zero rather than crashing`() {
        assertTrue(UpdateChecker.isNewer("1.x", "0.9"))
        assertFalse(UpdateChecker.isNewer("1.1", "1.1.e"))
    }
}
