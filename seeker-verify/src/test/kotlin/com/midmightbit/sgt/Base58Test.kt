package com.midmightbit.sgt

import org.junit.Assert.*
import org.junit.Test

class Base58Test {

    @Test
    fun `encode and decode round-trip for known Solana address`() {
        val address = "GT2zuHVaZQYZSyQMgJPLzvkmyztfyXg2NJunqFp4p3A4"
        val bytes = Base58.decode(address)
        val reEncoded = Base58.encode(bytes)
        assertEquals(address, reEncoded)
    }

    @Test
    fun `decode SGT mint authority produces 32 bytes`() {
        val bytes = Base58.decode(SgtConstants.SGT_MINT_AUTHORITY)
        assertEquals(32, bytes.size)
    }

    @Test
    fun `decode SGT metadata address produces 32 bytes`() {
        val bytes = Base58.decode(SgtConstants.SGT_METADATA_ADDRESS)
        assertEquals(32, bytes.size)
    }

    @Test
    fun `decode Token-2022 program ID produces 32 bytes`() {
        val bytes = Base58.decode(SgtConstants.TOKEN_2022_PROGRAM_ID)
        assertEquals(32, bytes.size)
    }

    @Test
    fun `encode empty byte array returns empty string`() {
        assertEquals("", Base58.encode(ByteArray(0)))
    }

    @Test
    fun `decode empty string returns empty byte array`() {
        assertArrayEquals(ByteArray(0), Base58.decode(""))
    }

    @Test
    fun `encode single zero byte returns 1`() {
        // A single zero byte should encode to "1" in Base58
        assertEquals("1", Base58.encode(byteArrayOf(0)))
    }

    @Test
    fun `encode two zero bytes returns 11`() {
        assertEquals("11", Base58.encode(byteArrayOf(0, 0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode invalid character throws`() {
        Base58.decode("0OIl") // These characters are not in Base58 alphabet
    }

    @Test
    fun `round-trip for all SGT constants`() {
        val addresses = listOf(
            SgtConstants.SGT_MINT_AUTHORITY,
            SgtConstants.SGT_METADATA_ADDRESS,
            SgtConstants.TOKEN_2022_PROGRAM_ID
        )
        for (address in addresses) {
            val decoded = Base58.decode(address)
            val reEncoded = Base58.encode(decoded)
            assertEquals("Round-trip failed for $address", address, reEncoded)
        }
    }

    @Test
    fun `different addresses decode to different bytes`() {
        val bytes1 = Base58.decode(SgtConstants.SGT_MINT_AUTHORITY)
        val bytes2 = Base58.decode(SgtConstants.SGT_METADATA_ADDRESS)
        assertFalse(
            "Different addresses should decode to different bytes",
            bytes1.contentEquals(bytes2)
        )
    }
}
