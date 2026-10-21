package com.android.zilbio

import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.domain.model.Friend
import com.android.zilbio.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRealtimeServiceParserTest {

    @Test
    fun testParseNullOrEmpty() {
        val emptyResult = CloudRealtimeService.parseFirebaseList<User>(null, User::class.java)
        assertTrue(emptyResult.isEmpty())

        val nullStrResult = CloudRealtimeService.parseFirebaseList<User>("null", User::class.java)
        assertTrue(nullStrResult.isEmpty())
    }

    @Test
    fun testParseJsonObjectWithKeyInjection() {
        val json = """
            {
                "user_abc": {
                    "username": "alice",
                    "displayName": "Alice Smith"
                },
                "user_def": {
                    "userId": "already_set",
                    "username": "bob",
                    "displayName": "Bob Jones"
                }
            }
        """.trimIndent()

        val result = CloudRealtimeService.parseFirebaseList<User>(json, User::class.java)
        assertEquals(2, result.size)
        val alice = result.first { it.username == "alice" }
        assertEquals("user_abc", alice.userId)

        val bob = result.first { it.username == "bob" }
        assertEquals("already_set", bob.userId)
    }

    @Test
    fun testParseJsonArray() {
        val json = """
            [
                null,
                { "userId": "u1", "username": "user1" },
                { "userId": "u2", "username": "user2" }
            ]
        """.trimIndent()

        val result = CloudRealtimeService.parseFirebaseList<Friend>(json, Friend::class.java)
        assertEquals(2, result.size)
        assertEquals("u1", result[0].userId)
        assertEquals("u2", result[1].userId)
    }
}
