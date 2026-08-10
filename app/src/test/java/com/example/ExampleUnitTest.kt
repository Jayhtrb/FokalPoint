package com.example

import com.example.data.network.UrlFormatter
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testUrlFormatting() {
    val testCases = listOf(
        "instagram.com/johndoe" to "https://instagram.com/johndoe",
        "@johndoe" to "https://www.instagram.com/johndoe",
        "johndoe" to "https://www.instagram.com/johndoe",
        "youtube.com/@johndoe" to "https://youtube.com/@johndoe",
        "johndoe.com" to "https://johndoe.com",
        "www.johndoe.com" to "https://www.johndoe.com",
        "https://johndoe.com" to "https://johndoe.com"
    )
    
    testCases.forEach { (input, expected) ->
        val formatted = UrlFormatter.formatWebsiteUrl(input)
        assertEquals("Failed for input: $input", expected, formatted)
    }
  }
}

