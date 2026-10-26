package app.zhizhang.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `newer semantic versions are detected`() {
        assertTrue(UpdateChecker.isNewer("v1.0.2", "1.0.1"))
        assertTrue(UpdateChecker.isNewer("1.1.0", "1.0.9"))
        assertTrue(UpdateChecker.isNewer("v2.0", "1.9.9"))
        assertFalse(UpdateChecker.isNewer("v1.0.1", "1.0.1"))
        assertFalse(UpdateChecker.isNewer("v1.0.0", "1.0.1"))
    }
}
