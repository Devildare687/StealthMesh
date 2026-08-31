package io.github.devildare687.stealthmesh.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataManagerNicknameTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("bitchat_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `custom nickname survives data manager recreation`() {
        DataManager(context).saveNickname("Nova")

        val recreated = DataManager(context)

        assertEquals("Nova", recreated.loadNickname())
    }

    @Test
    fun `generated anonymous fallback is persisted and reused`() {
        val first = DataManager(context).loadNickname()
        val recreated = DataManager(context).loadNickname()

        assertTrue(first.matches(Regex("anon\\d{4}")))
        assertEquals(first, recreated)
    }
}
