package com.midmightbit.sgt

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Token2022ParserTest {

    /**
     * Helper to build a mock Token-2022 mint account with extensions.
     *
     * Layout:
     *   0-3:    mint_authority_option (1 = Some)
     *   4-35:   mint_authority (32 bytes)
     *   36-81:  supply/decimals/init/freeze (46 bytes, zeroed)
     *   82-164: padding (83 bytes, zeroed)
     *   165:    AccountType = 1 (Mint)
     *   166+:   TLV extensions
     */
    private fun buildMockMintAccount(
        mintAuthority: ByteArray,
        extensions: List<Triple<Int, Int, ByteArray>> = emptyList() // (type, length, value)
    ): ByteArray {
        // Calculate total size
        val extensionsSize = extensions.sumOf { 4 + it.third.size } // 2 type + 2 length + value
        val totalSize = 166 + extensionsSize

        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // Mint authority option = 1 (Some)
        buf.putInt(0, 1)

        // Mint authority at offset 4
        System.arraycopy(mintAuthority, 0, buf.array(), 4, 32)

        // Skip supply/decimals/init/freeze/padding (already zeroed)

        // AccountType at offset 165 = 1 (Mint)
        buf.array()[165] = 1

        // TLV extensions at offset 166+
        var offset = 166
        for ((type, length, value) in extensions) {
            buf.putShort(offset, type.toShort())
            offset += 2
            buf.putShort(offset, length.toShort())
            offset += 2
            System.arraycopy(value, 0, buf.array(), offset, value.size)
            offset += value.size
        }

        return buf.array()
    }

    private fun buildMockTokenAccount(mintPubkey: ByteArray): ByteArray {
        // Token account: first 32 bytes = mint pubkey, rest can be zeroed
        val data = ByteArray(165)
        System.arraycopy(mintPubkey, 0, data, 0, 32)
        return data
    }

    @Test
    fun `parseMintAccount returns null for data shorter than 166 bytes`() {
        val shortData = java.util.Base64.getEncoder().encodeToString(ByteArray(100))
        assertNull(Token2022Parser.parseMintAccount(shortData))
    }

    @Test
    fun `parseMintAccount returns null for non-mint account type`() {
        val data = ByteArray(200)
        data[165] = 2 // AccountType = 2 (Account, not Mint)
        val base64 = java.util.Base64.getEncoder().encodeToString(data)
        assertNull(Token2022Parser.parseMintAccount(base64))
    }

    @Test
    fun `parseMintAccount extracts mint authority correctly`() {
        val mintAuth = Base58.decode(SgtConstants.SGT_MINT_AUTHORITY)
        val data = buildMockMintAccount(mintAuth)
        val base64 = java.util.Base64.getEncoder().encodeToString(data)

        val result = Token2022Parser.parseMintAccount(base64)

        assertNotNull(result)
        assertNotNull(result!!.mintAuthority)
        assertTrue(result.mintAuthority!!.contentEquals(mintAuth))
    }

    @Test
    fun `parseMintAccount returns null mint authority when option is 0`() {
        val data = ByteArray(200)
        data[165] = 1 // AccountType = Mint
        // mint_authority_option at offset 0 is already 0 (no authority)
        val base64 = java.util.Base64.getEncoder().encodeToString(data)

        val result = Token2022Parser.parseMintAccount(base64)

        assertNotNull(result)
        assertNull(result!!.mintAuthority)
    }

    @Test
    fun `parseMintAccount parses MetadataPointer extension`() {
        val mintAuth = Base58.decode(SgtConstants.SGT_MINT_AUTHORITY)
        val metadataAddr = Base58.decode(SgtConstants.SGT_METADATA_ADDRESS)

        // MetadataPointer value: 32 bytes authority + 32 bytes metadata_address
        val metadataPointerValue = mintAuth + metadataAddr

        val data = buildMockMintAccount(
            mintAuthority = mintAuth,
            extensions = listOf(Triple(18, 64, metadataPointerValue))
        )
        val base64 = java.util.Base64.getEncoder().encodeToString(data)

        val result = Token2022Parser.parseMintAccount(base64)

        assertNotNull(result)
        assertNotNull(result!!.metadataPointerAuthority)
        assertTrue(result.metadataPointerAuthority!!.contentEquals(mintAuth))
        assertNotNull(result.metadataPointerAddress)
        assertTrue(result.metadataPointerAddress!!.contentEquals(metadataAddr))
    }

    @Test
    fun `parseMintAccount parses TokenGroupMember extension`() {
        val mintAuth = Base58.decode(SgtConstants.SGT_MINT_AUTHORITY)
        val groupMint = Base58.decode(SgtConstants.SGT_METADATA_ADDRESS)

        // TokenGroupMember value: 32 bytes mint + 32 bytes group + 8 bytes memberNumber
        val randomMint = ByteArray(32) { it.toByte() }
        val memberNumber = ByteArray(8)
        val groupMemberValue = randomMint + groupMint + memberNumber

        val data = buildMockMintAccount(
            mintAuthority = mintAuth,
            extensions = listOf(Triple(23, 72, groupMemberValue))
        )
        val base64 = java.util.Base64.getEncoder().encodeToString(data)

        val result = Token2022Parser.parseMintAccount(base64)

        assertNotNull(result)
        assertNotNull(result!!.groupMemberGroup)
        assertTrue(result.groupMemberGroup!!.contentEquals(groupMint))
    }

    @Test
    fun `parseMintAccount handles multiple extensions`() {
        val mintAuth = Base58.decode(SgtConstants.SGT_MINT_AUTHORITY)
        val metadataAddr = Base58.decode(SgtConstants.SGT_METADATA_ADDRESS)

        val metadataPointerValue = mintAuth + metadataAddr

        val randomMint = ByteArray(32) { it.toByte() }
        val memberNumber = ByteArray(8)
        val groupMemberValue = randomMint + metadataAddr + memberNumber

        val data = buildMockMintAccount(
            mintAuthority = mintAuth,
            extensions = listOf(
                Triple(18, 64, metadataPointerValue),   // MetadataPointer
                Triple(23, 72, groupMemberValue)         // TokenGroupMember
            )
        )
        val base64 = java.util.Base64.getEncoder().encodeToString(data)

        val result = Token2022Parser.parseMintAccount(base64)

        assertNotNull(result)
        // MetadataPointer was parsed
        assertTrue(result!!.metadataPointerAuthority!!.contentEquals(mintAuth))
        assertTrue(result.metadataPointerAddress!!.contentEquals(metadataAddr))
        // TokenGroupMember was parsed
        assertTrue(result.groupMemberGroup!!.contentEquals(metadataAddr))
    }

    @Test
    fun `parseMintAccount skips unknown extension types gracefully`() {
        val mintAuth = Base58.decode(SgtConstants.SGT_MINT_AUTHORITY)

        // Unknown extension type 99 with some arbitrary data
        val unknownData = ByteArray(16) { 0xFF.toByte() }

        val data = buildMockMintAccount(
            mintAuthority = mintAuth,
            extensions = listOf(Triple(99, 16, unknownData))
        )
        val base64 = java.util.Base64.getEncoder().encodeToString(data)

        val result = Token2022Parser.parseMintAccount(base64)

        assertNotNull(result)
        // Known extensions should be null since none were present
        assertNull(result!!.metadataPointerAuthority)
        assertNull(result.groupMemberGroup)
        // Mint authority should still be parsed
        assertTrue(result.mintAuthority!!.contentEquals(mintAuth))
    }

    @Test
    fun `extractMintFromTokenAccount returns first 32 bytes`() {
        val expectedMint = ByteArray(32) { (it + 1).toByte() }
        val tokenAccountData = buildMockTokenAccount(expectedMint)
        val base64 = java.util.Base64.getEncoder().encodeToString(tokenAccountData)

        val result = Token2022Parser.extractMintFromTokenAccount(base64)

        assertNotNull(result)
        assertTrue(result!!.contentEquals(expectedMint))
    }

    @Test
    fun `extractMintFromTokenAccount returns null for short data`() {
        val shortData = ByteArray(16)
        val base64 = java.util.Base64.getEncoder().encodeToString(shortData)
        assertNull(Token2022Parser.extractMintFromTokenAccount(base64))
    }

    @Test
    fun `parseMintAccount returns null for invalid base64`() {
        assertNull(Token2022Parser.parseMintAccount("not-valid-base64!!!"))
    }
}
