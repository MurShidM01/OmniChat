package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.presets.ProviderPreset
import com.example.network.NetworkUtils
import com.example.security.KeystoreSecretManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("OmniChat", appName)
  }

  @Test
  fun `keystore secret manager encrypts and decrypts correctly`() {
    val sampleKey = "sk-test-1234567890abcdef"
    val encrypted = KeystoreSecretManager.encrypt(sampleKey)
    assertFalse("Encrypted string should not contain plaintext", encrypted.contains("1234567890"))
    val decrypted = KeystoreSecretManager.decrypt(encrypted)
    assertEquals(sampleKey, decrypted)
  }

  @Test
  fun `keystore secret manager key masking works as expected`() {
    val key = "sk-ant-api03-abcdefg123456789"
    val masked = KeystoreSecretManager.maskKey(key)
    assertTrue(masked.startsWith("sk-a"))
    assertTrue(masked.endsWith("6789"))
    assertTrue(masked.contains("••••••••"))
  }

  @Test
  fun `presets contain expected default providers`() {
    val presets = ProviderPreset.getPresets()
    val titles = presets.map { it.title }
    assertTrue(titles.contains("OpenAI"))
    assertTrue(titles.contains("Anthropic"))
    assertTrue(titles.contains("OpenRouter"))
    assertTrue(titles.contains("Groq"))
    assertTrue(titles.contains("DeepSeek"))
    assertTrue(titles.contains("Ollama (Local / Self-hosted)"))
  }

  @Test
  fun `network utils builds full url without double slashes`() {
    val url1 = NetworkUtils.buildFullUrl("https://api.openai.com/v1", "/chat/completions")
    assertEquals("https://api.openai.com/v1/chat/completions", url1)

    val url2 = NetworkUtils.buildFullUrl("https://api.openai.com/v1/", "chat/completions")
    assertEquals("https://api.openai.com/v1/chat/completions", url2)
  }
}

