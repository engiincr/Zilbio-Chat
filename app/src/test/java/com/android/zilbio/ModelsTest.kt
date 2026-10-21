package com.android.zilbio

import com.android.zilbio.domain.model.Friend
import com.android.zilbio.domain.model.Language
import com.android.zilbio.domain.model.User
import com.android.zilbio.domain.model.UserPermissions
import com.android.zilbio.domain.model.isOwnerOrAdmin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    @Test
    fun testUserIsOwnerOrAdmin() {
        val regularUser = User(userId = "u1", username = "regular", role = "user")
        assertFalse(regularUser.isOwnerOrAdmin())

        val adminUser = User(userId = "u2", username = "admin", role = "admin")
        assertTrue(adminUser.isOwnerOrAdmin())

        val ownerUser = User(userId = "u3", username = "owner", role = "owner")
        assertTrue(ownerUser.isOwnerOrAdmin())

        val hardcodedOwner = User(userId = "u4", username = "tugkancr", role = "user")
        assertTrue(hardcodedOwner.isOwnerOrAdmin())

        val userWithAdminPerm = User(
            userId = "u5",
            username = "user5",
            role = "user",
            permissions = UserPermissions(isAdmin = true)
        )
        assertTrue(userWithAdminPerm.isOwnerOrAdmin())
    }

    @Test
    fun testFriendIsOwnerOrAdmin() {
        val normalFriend = Friend(userId = "f1", username = "friend1", role = "user")
        assertFalse(normalFriend.isOwnerOrAdmin())

        val adminFriend = Friend(userId = "f2", username = "friend2", role = "admin")
        assertTrue(adminFriend.isOwnerOrAdmin())

        val ownerFriend = Friend(userId = "f3", username = "friend3", role = "owner")
        assertTrue(ownerFriend.isOwnerOrAdmin())
    }

    @Test
    fun testLanguageLookup() {
        val tr = Language.fromCode("tr")
        assertEquals("tr", tr.code)

        val en = Language.fromCode("en")
        assertEquals("en", en.code)

        val unknown = Language.fromCode("xyz")
        assertEquals("tr", unknown.code)
    }
}
