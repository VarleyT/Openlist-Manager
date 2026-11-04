package com.bytegrep.oplist_manager.feature.users

import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.model.UserPermission
import com.bytegrep.oplist_manager.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionTest {

    @Test
    fun `enabling a bit sets only that bit`() {
        assertEquals(0b1000, togglePermission(0, UserPermission.WRITE.bit, true))
    }

    @Test
    fun `enabling an already set bit keeps mask unchanged`() {
        assertEquals(0b1010, togglePermission(0b1010, 3, true))
    }

    @Test
    fun `disabling a bit clears only that bit`() {
        assertEquals(0b0010, togglePermission(0b1010, 3, false))
    }

    @Test
    fun `disabling an unset bit keeps mask unchanged`() {
        assertEquals(0b0010, togglePermission(0b0010, 0, false))
    }

    @Test
    fun `highest permission bit can be toggled`() {
        val mask = togglePermission(0, UserPermission.SHARE.bit, true)
        assertEquals(1 shl 14, mask)
        assertEquals(0, togglePermission(mask, UserPermission.SHARE.bit, false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative bit is rejected`() {
        togglePermission(0, -1, true)
    }

    @Test
    fun `hasPermission reflects mask for general user`() {
        // 12799 为实测 admin 掩码，这里用于普通用户检验位运算
        val user = UserDto(role = UserRole.GENERAL, permission = 12799)
        UserPermission.entries.forEach { permission ->
            val expected = (12799 shr permission.bit) and 1 == 1
            assertEquals(permission.name, expected, user.hasPermission(permission.bit))
        }
    }

    @Test
    fun `hasPermission after toggle round trip`() {
        val base = UserDto(role = UserRole.GENERAL, permission = 0)
        val granted = base.copy(permission = togglePermission(base.permission, UserPermission.REMOVE.bit, true))
        assertTrue(granted.hasPermission(UserPermission.REMOVE.bit))
        assertFalse(granted.hasPermission(UserPermission.RENAME.bit))
        val revoked = granted.copy(permission = togglePermission(granted.permission, UserPermission.REMOVE.bit, false))
        assertFalse(revoked.hasPermission(UserPermission.REMOVE.bit))
    }

    @Test
    fun `admin has every permission regardless of mask`() {
        val admin = UserDto(role = UserRole.ADMIN, permission = 0)
        UserPermission.entries.forEach { assertTrue(it.name, admin.hasPermission(it.bit)) }
    }
}
