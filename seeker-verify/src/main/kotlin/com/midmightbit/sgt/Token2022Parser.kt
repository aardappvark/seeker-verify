package com.midmightbit.sgt

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses Token-2022 mint account binary data and extracts extension fields.
 *
 * ## Token-2022 Mint Account Binary Layout
 *
 * ```
 * Bytes 0-3:      mint_authority_option (u32 LE, 1 = Some)
 * Bytes 4-35:     mint_authority (32 bytes, PublicKey)
 * Bytes 36-43:    supply (u64 LE)
 * Byte  44:       decimals (u8)
 * Byte  45:       is_initialized (bool)
 * Bytes 46-49:    freeze_authority_option (u32 LE)
 * Bytes 50-81:    freeze_authority (32 bytes, PublicKey)
 * --- 82 bytes base mint ---
 * Bytes 82-164:   padding (83 bytes, to align with token account size of 165)
 * Byte  165:      AccountType discriminator (1 = Mint, 2 = Account)
 * Bytes 166+:     TLV extensions
 * ```
 *
 * ## TLV Extension Entry Format
 *
 * ```
 * Type:    2 bytes (u16 LE) — ExtensionType enum value
 * Length:  2 bytes (u16 LE) — byte count of value data
 * Value:   [Length] bytes   — extension-specific data
 * ```
 */
internal object Token2022Parser {

    // -- Token-2022 Extension Type IDs (u16) --
    private const val EXT_METADATA_POINTER: Int = 18
    private const val EXT_TOKEN_GROUP_MEMBER: Int = 23

    // -- Mint account layout offsets --
    private const val MINT_AUTHORITY_OPTION_OFFSET = 0
    private const val MINT_AUTHORITY_OFFSET = 4
    private const val PUBKEY_SIZE = 32
    private const val BASE_MINT_SIZE = 82
    private const val ACCOUNT_TYPE_OFFSET = 165
    private const val TLV_START_OFFSET = 166
    private const val TLV_HEADER_SIZE = 4 // 2 bytes type + 2 bytes length

    /**
     * Holds the extracted fields from a Token-2022 mint account
     * that are relevant for SGT verification.
     */
    data class MintExtensionData(
        val mintAuthority: ByteArray?,
        val metadataPointerAuthority: ByteArray?,
        val metadataPointerAddress: ByteArray?,
        val groupMemberMint: ByteArray?,
        val groupMemberGroup: ByteArray?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MintExtensionData) return false
            return mintAuthority.contentEqualsNullable(other.mintAuthority) &&
                    metadataPointerAuthority.contentEqualsNullable(other.metadataPointerAuthority) &&
                    metadataPointerAddress.contentEqualsNullable(other.metadataPointerAddress) &&
                    groupMemberMint.contentEqualsNullable(other.groupMemberMint) &&
                    groupMemberGroup.contentEqualsNullable(other.groupMemberGroup)
        }

        override fun hashCode(): Int {
            var result = mintAuthority?.contentHashCode() ?: 0
            result = 31 * result + (metadataPointerAuthority?.contentHashCode() ?: 0)
            result = 31 * result + (metadataPointerAddress?.contentHashCode() ?: 0)
            result = 31 * result + (groupMemberMint?.contentHashCode() ?: 0)
            result = 31 * result + (groupMemberGroup?.contentHashCode() ?: 0)
            return result
        }

        private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean {
            if (this == null && other == null) return true
            if (this == null || other == null) return false
            return this.contentEquals(other)
        }
    }

    /**
     * Parses a base64-encoded Token-2022 mint account and extracts
     * the fields needed for SGT verification.
     *
     * @param base64Data Base64-encoded account data from Solana RPC
     * @return Parsed extension data, or null if the data is too short or not a mint account
     */
    fun parseMintAccount(base64Data: String): MintExtensionData? {
        val data = try {
            java.util.Base64.getDecoder().decode(base64Data)
        } catch (e: Exception) {
            return null
        }

        // Must be at least large enough to contain base mint + padding + AccountType (166 bytes)
        if (data.size < TLV_START_OFFSET) return null

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Extract mint authority (bytes 0-35)
        val mintAuthorityOption = buf.getInt(MINT_AUTHORITY_OPTION_OFFSET)
        val mintAuthority = if (mintAuthorityOption == 1) {
            data.copyOfRange(MINT_AUTHORITY_OFFSET, MINT_AUTHORITY_OFFSET + PUBKEY_SIZE)
        } else {
            null
        }

        // Verify this is a Mint account (AccountType byte at offset 165 must be 1)
        val accountType = data[ACCOUNT_TYPE_OFFSET].toInt() and 0xFF
        if (accountType != 1) return null

        // Parse TLV extensions starting at byte 166
        var metadataPointerAuthority: ByteArray? = null
        var metadataPointerAddress: ByteArray? = null
        var groupMemberMint: ByteArray? = null
        var groupMemberGroup: ByteArray? = null

        var offset = TLV_START_OFFSET
        while (offset + TLV_HEADER_SIZE <= data.size) {
            val extType = buf.getShort(offset).toInt() and 0xFFFF
            offset += 2

            // Type 0 = Uninitialized, signals end of extensions
            if (extType == 0) break

            val extLength = buf.getShort(offset).toInt() and 0xFFFF
            offset += 2

            // Safety check: extension value must fit within data
            if (offset + extLength > data.size) break

            when (extType) {
                EXT_METADATA_POINTER -> {
                    // MetadataPointer: 32 bytes authority + 32 bytes metadata_address
                    if (extLength >= 64) {
                        metadataPointerAuthority = data.copyOfRange(offset, offset + PUBKEY_SIZE)
                        metadataPointerAddress = data.copyOfRange(
                            offset + PUBKEY_SIZE,
                            offset + PUBKEY_SIZE * 2
                        )
                    }
                }

                EXT_TOKEN_GROUP_MEMBER -> {
                    // TokenGroupMember: 32 bytes mint + 32 bytes group + 8 bytes memberNumber
                    if (extLength >= 64) {
                        groupMemberMint = data.copyOfRange(offset, offset + PUBKEY_SIZE)
                        groupMemberGroup = data.copyOfRange(
                            offset + PUBKEY_SIZE,
                            offset + PUBKEY_SIZE * 2
                        )
                    }
                }
            }

            offset += extLength
        }

        return MintExtensionData(
            mintAuthority = mintAuthority,
            metadataPointerAuthority = metadataPointerAuthority,
            metadataPointerAddress = metadataPointerAddress,
            groupMemberMint = groupMemberMint,
            groupMemberGroup = groupMemberGroup
        )
    }

    /**
     * Extracts the mint public key from a Token-2022 token account's binary data.
     *
     * Token account layout: first 32 bytes = mint PublicKey.
     *
     * @param base64Data Base64-encoded token account data from Solana RPC
     * @return 32-byte mint public key, or null if data is too short
     */
    fun extractMintFromTokenAccount(base64Data: String): ByteArray? {
        val data = try {
            java.util.Base64.getDecoder().decode(base64Data)
        } catch (e: Exception) {
            return null
        }

        if (data.size < PUBKEY_SIZE) return null
        return data.copyOfRange(0, PUBKEY_SIZE)
    }
}
