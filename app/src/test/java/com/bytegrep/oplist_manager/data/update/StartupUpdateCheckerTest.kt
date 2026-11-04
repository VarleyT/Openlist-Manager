package com.bytegrep.oplist_manager.data.update

import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.datastore.AppSettings
import com.bytegrep.oplist_manager.core.datastore.AppSettingsStore
import com.bytegrep.oplist_manager.core.datastore.UpdateCheckRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class StartupUpdateCheckerTest {

    private val repository = mockk<UpdateRepository>()
    private val store = mockk<AppSettingsStore>(relaxUnitFun = true)
    private val checker = StartupUpdateChecker(repository, store)
    private val release = AppRelease("1.2.0", "v1.2.0", "", AppLinks.RELEASES_URL)

    @Before
    fun setUp() {
        givenSettings(autoCheck = true, record = UpdateCheckRecord())
        coEvery { repository.check(any()) } returns UpdateCheckResult.Available(release)
    }

    private fun givenSettings(autoCheck: Boolean, record: UpdateCheckRecord) {
        every { store.settings } returns flowOf(AppSettings(autoCheckUpdate = autoCheck))
        coEvery { store.updateCheckRecord() } returns record
    }

    @Test
    fun `returns release and records check time when update available`() = runTest {
        assertEquals(release, checker.check("1.0.0", now = NOW))

        coVerify { store.setLastUpdateCheckAt(NOW) }
    }

    @Test
    fun `skips when auto check disabled`() = runTest {
        givenSettings(autoCheck = false, record = UpdateCheckRecord())

        assertNull(checker.check("1.0.0", now = NOW))

        coVerify(exactly = 0) { repository.check(any()) }
    }

    @Test
    fun `skips when checked within interval`() = runTest {
        givenSettings(autoCheck = true, record = UpdateCheckRecord(lastCheckAt = NOW - 1000))

        assertNull(checker.check("1.0.0", now = NOW))

        coVerify(exactly = 0) { repository.check(any()) }
    }

    @Test
    fun `checks again after interval elapsed`() = runTest {
        val last = NOW - StartupUpdateChecker.CHECK_INTERVAL_MS
        givenSettings(autoCheck = true, record = UpdateCheckRecord(lastCheckAt = last))

        assertEquals(release, checker.check("1.0.0", now = NOW))
    }

    @Test
    fun `checks again when clock moved backwards`() = runTest {
        givenSettings(autoCheck = true, record = UpdateCheckRecord(lastCheckAt = NOW + 1000))

        assertEquals(release, checker.check("1.0.0", now = NOW))
    }

    @Test
    fun `ignored version is not prompted`() = runTest {
        givenSettings(autoCheck = true, record = UpdateCheckRecord(ignoredVersion = "1.2.0"))

        assertNull(checker.check("1.0.0", now = NOW))
    }

    @Test
    fun `failure is silent but still throttled`() = runTest {
        coEvery { repository.check(any()) } throws AppException.Network("网络异常")

        assertNull(checker.check("1.0.0", now = NOW))

        coVerify { store.setLastUpdateCheckAt(NOW) }
    }

    @Test
    fun `up to date returns null`() = runTest {
        coEvery { repository.check(any()) } returns UpdateCheckResult.UpToDate

        assertNull(checker.check("1.2.0", now = NOW))
    }

    @Test
    fun `ignore persists version`() = runTest {
        checker.ignore("1.2.0")

        coVerify { store.setIgnoredVersion("1.2.0") }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
