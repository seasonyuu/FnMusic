package com.seasonyuu.fnmusic

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.model.ConnectionProfile
import com.seasonyuu.fnmusic.core.model.Endpoint
import com.seasonyuu.fnmusic.core.model.LoginForm
import com.seasonyuu.fnmusic.core.model.SessionState
import com.seasonyuu.fnmusic.core.network.CredentialVault
import com.seasonyuu.fnmusic.core.network.SavedCredentials
import com.seasonyuu.fnmusic.feature.session.ConnectionScreen
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun savedFormCanBeEditedAndSubmitted() {
        val form = mutableStateOf(LoginForm(fnId = "test-fnid", username = "test-user", password = "test-password"))
        var submitted: ConnectionProfile? = null
        var submittedPassword = ""
        compose.setContent {
            FnMusicTheme {
                ConnectionScreen(SessionState.Unresolved, { profile, password ->
                    submitted = profile
                    submittedPassword = password.concatToString()
                    password.fill('\u0000')
                }, form.value, { form.value = it })
            }
        }
        compose.onNodeWithContentDescription("音乐账号").assertTextContains("test-user")
        compose.onNodeWithContentDescription("显示密码").performClick()
        compose.onNodeWithContentDescription("密码", useUnmergedTree = true).assertTextContains("test-password")
        compose.onNodeWithContentDescription("音乐账号").performTextReplacement("edited-user")
        compose.onNodeWithText("登录音乐库").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("edited-user", submitted?.username)
            assertEquals(Endpoint.FnConnect("test-fnid"), submitted?.endpoint)
            assertEquals("test-password", submittedPassword)
        }
    }

    @Test fun connectionModesRetainIndependentAddressesAndRenderFlowingBackground() {
        val form = mutableStateOf(LoginForm(fnId = "test-fnid", directUrl = "https://nas.example/music/"))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FnMusicTheme { ConnectionScreen(SessionState.Unresolved, { _, _ -> }, form.value, { form.value = it }) }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("登录音乐库").assertIsNotEnabled()
        val first = compose.onRoot().captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(3_000)
        val second = compose.onRoot().captureToImage().asAndroidBitmap()
        assertFalse("The shared light background must move", first.sameAs(second))
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.cacheDir, "login-preview.png").outputStream().use { second.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("NAS 直连").performClick()
        compose.mainClock.advanceTimeBy(320)
        compose.onNodeWithContentDescription("飞牛音乐地址").assertTextContains("https://nas.example/music/")
        compose.onNodeWithText("FN Connect").performClick()
        compose.mainClock.advanceTimeBy(320)
        compose.onNodeWithContentDescription("FN ID").assertTextContains("test-fnid")
        // Reverse direction before the transition finishes, then edit the settled destination.
        compose.onNodeWithText("NAS 直连").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithText("FN Connect").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithText("NAS 直连").performClick()
        compose.mainClock.advanceTimeBy(320)
        compose.onNodeWithContentDescription("飞牛音乐地址").performTextReplacement("https://edited.example/music/")
        compose.runOnIdle {
            assertEquals("test-fnid", form.value.fnId)
            assertEquals("https://edited.example/music/", form.value.directUrl)
        }
    }

    @Test fun rememberedPasswordIsEncryptedAndSurvivesSessionDeletion() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val name = "login-vault-test-${System.nanoTime()}"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(ignored: String?, mode: Int) = base.getSharedPreferences(name, mode)
        }
        try {
            val vault = CredentialVault(context, Json)
            val form = LoginForm(true, "test-fnid", "https://nas.example/", "test-user", "test-secret", true)
            vault.saveLoginForm(form)
            vault.save(SavedCredentials(ConnectionProfile(Endpoint.FnConnect("test-fnid"), "test-user"), "test-hash", "test-token"))
            vault.clearCredentials()
            val reopened = CredentialVault(context, Json)
            assertNull(reopened.load())
            assertEquals(form, reopened.loadLoginForm())
            val stored = base.getSharedPreferences(name, Context.MODE_PRIVATE).all.values.joinToString()
            assertFalse(stored.contains("test-secret"))
            assertFalse(stored.contains("test-user"))
            assertFalse(stored.contains("nas.example"))
        } finally {
            base.deleteSharedPreferences(name)
        }
    }
}
