package com.midmightbit.sgt

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SgtCheckerTest {

    private lateinit var mockServer: MockWebServer

    @Before
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
    }

    @After
    fun teardown() {
        mockServer.shutdown()
    }

    private val testWalletAddress = "11111111111111111111111111111112"

    @Test
    fun `checkWallet returns false for wallet with no Token-2022 accounts`() = runTest {
        // RPC response: empty token accounts
        mockServer.enqueue(
            MockResponse()
                .setBody("""
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "result": {
                            "context": {"slot": 12345},
                            "value": []
                        }
                    }
                """.trimIndent())
                .setResponseCode(200)
        )

        val result = SgtChecker.checkWallet(
            walletAddress = testWalletAddress,
            rpcUrl = mockServer.url("/").toString()
        )

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow())
    }

    @Test
    fun `checkWallet returns failure on RPC error`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setBody("""
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "error": {
                            "code": -32600,
                            "message": "Invalid request"
                        }
                    }
                """.trimIndent())
                .setResponseCode(200)
        )

        val result = SgtChecker.checkWallet(
            walletAddress = testWalletAddress,
            rpcUrl = mockServer.url("/").toString()
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SgtException)
    }

    @Test
    fun `checkWallet returns failure on HTTP error`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val result = SgtChecker.checkWallet(
            walletAddress = testWalletAddress,
            rpcUrl = mockServer.url("/").toString()
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `checkWallet returns false when mint does not match SGT criteria`() = runTest {
        // Build a fake token account with a random mint
        val fakeMint = ByteArray(32) { 0x42 }
        val tokenAccountData = ByteArray(165)
        System.arraycopy(fakeMint, 0, tokenAccountData, 0, 32)
        val tokenAccountBase64 = java.util.Base64.getEncoder().encodeToString(
            tokenAccountData
        )
        val fakeMintBase58 = Base58.encode(fakeMint)

        // Response 1: getTokenAccountsByOwner returns one token account
        mockServer.enqueue(
            MockResponse()
                .setBody("""
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "result": {
                            "context": {"slot": 12345},
                            "value": [{
                                "pubkey": "tokenAccountPubkey123",
                                "account": {
                                    "data": ["$tokenAccountBase64", "base64"],
                                    "executable": false,
                                    "lamports": 2039280,
                                    "owner": "${SgtConstants.TOKEN_2022_PROGRAM_ID}",
                                    "rentEpoch": 0,
                                    "space": 165
                                }
                            }]
                        }
                    }
                """.trimIndent())
                .setResponseCode(200)
        )

        // Response 2: getMultipleAccounts returns a non-SGT mint
        // Build a mint account without the right authority
        val wrongAuthority = ByteArray(32) { 0x01 }
        val mintAccountData = ByteArray(200)
        // mint_authority_option = 1 (Some)
        val buf = ByteBuffer.wrap(mintAccountData).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0, 1)
        System.arraycopy(wrongAuthority, 0, mintAccountData, 4, 32)
        mintAccountData[165] = 1 // AccountType = Mint
        val mintAccountBase64 = java.util.Base64.getEncoder().encodeToString(
            mintAccountData
        )

        mockServer.enqueue(
            MockResponse()
                .setBody("""
                    {
                        "jsonrpc": "2.0",
                        "id": 2,
                        "result": {
                            "context": {"slot": 12345},
                            "value": [{
                                "data": ["$mintAccountBase64", "base64"],
                                "executable": false,
                                "lamports": 1461600,
                                "owner": "${SgtConstants.TOKEN_2022_PROGRAM_ID}",
                                "rentEpoch": 0,
                                "space": 200
                            }]
                        }
                    }
                """.trimIndent())
                .setResponseCode(200)
        )

        val result = SgtChecker.checkWallet(
            walletAddress = testWalletAddress,
            rpcUrl = mockServer.url("/").toString()
        )

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow())
    }

    @Test
    fun `checkWallet makes correct RPC method calls`() = runTest {
        // Enqueue empty responses
        mockServer.enqueue(
            MockResponse()
                .setBody("""
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "result": {
                            "context": {"slot": 12345},
                            "value": []
                        }
                    }
                """.trimIndent())
                .setResponseCode(200)
        )

        SgtChecker.checkWallet(
            walletAddress = testWalletAddress,
            rpcUrl = mockServer.url("/").toString()
        )

        // Verify the RPC request
        val request = mockServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("getTokenAccountsByOwner"))
        assertTrue(body.contains(SgtConstants.TOKEN_2022_PROGRAM_ID))
        assertTrue(body.contains(testWalletAddress))
    }

    // --- getWalletSgtInfo tests ---

    @Test
    fun `getWalletSgtInfo returns SgtInfo with hasSgt false for empty wallet`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setBody("""
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "result": {
                            "context": {"slot": 12345},
                            "value": []
                        }
                    }
                """.trimIndent())
                .setResponseCode(200)
        )

        val result = SgtChecker.getWalletSgtInfo(
            walletAddress = testWalletAddress,
            rpcUrl = mockServer.url("/").toString()
        )

        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertFalse(info.hasSgt)
        assertNull(info.memberNumber)
        assertNull(info.sgtMintAddress)
        assertNull(info.sgtTokenAccountAddress)
    }

    @Test
    fun `getWalletSgtInfo returns SgtInfo with member number for valid SGT`() = runTest {
        // Build a valid SGT token account and mint
        val sgtMintAuthority = Base58.decode(SgtConstants.SGT_MINT_AUTHORITY)
        val sgtMetadataAddr = Base58.decode(SgtConstants.SGT_METADATA_ADDRESS)

        // Build token account data: first 32 bytes = mint pubkey
        val fakeSgtMint = ByteArray(32) { (it + 10).toByte() }
        val tokenAccountData = ByteArray(165)
        System.arraycopy(fakeSgtMint, 0, tokenAccountData, 0, 32)
        val tokenAccountBase64 = java.util.Base64.getEncoder().encodeToString(tokenAccountData)
        val fakeSgtMintBase58 = Base58.encode(fakeSgtMint)

        // Response 1: getTokenAccountsByOwner returns one token account
        mockServer.enqueue(
            MockResponse()
                .setBody("""
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "result": {
                            "context": {"slot": 12345},
                            "value": [{
                                "pubkey": "tokenAcctAddr123",
                                "account": {
                                    "data": ["$tokenAccountBase64", "base64"],
                                    "executable": false,
                                    "lamports": 2039280,
                                    "owner": "${SgtConstants.TOKEN_2022_PROGRAM_ID}",
                                    "rentEpoch": 0,
                                    "space": 165
                                }
                            }]
                        }
                    }
                """.trimIndent())
                .setResponseCode(200)
        )

        // Build a valid SGT mint account with all extensions
        // MetadataPointer: authority (32) + address (32)
        val metadataPointerValue = sgtMintAuthority + sgtMetadataAddr
        // TokenGroupMember: mint (32) + group (32) + memberNumber (8)
        val memberNumberBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        memberNumberBuf.putLong(4217L)
        val groupMemberValue = fakeSgtMint + sgtMetadataAddr + memberNumberBuf.array()

        // Build mint account binary
        val extensionsSize = (4 + 64) + (4 + 72) // MetadataPointer + TokenGroupMember
        val mintAccountData = ByteArray(166 + extensionsSize)
        val mintBuf = ByteBuffer.wrap(mintAccountData).order(ByteOrder.LITTLE_ENDIAN)

        // mint_authority_option = 1 (Some)
        mintBuf.putInt(0, 1)
        // mint_authority
        System.arraycopy(sgtMintAuthority, 0, mintAccountData, 4, 32)
        // AccountType = 1 (Mint)
        mintAccountData[165] = 1

        // TLV extensions at offset 166
        var offset = 166
        // MetadataPointer (type 18, length 64)
        mintBuf.putShort(offset, 18.toShort()); offset += 2
        mintBuf.putShort(offset, 64.toShort()); offset += 2
        System.arraycopy(metadataPointerValue, 0, mintAccountData, offset, 64); offset += 64
        // TokenGroupMember (type 23, length 72)
        mintBuf.putShort(offset, 23.toShort()); offset += 2
        mintBuf.putShort(offset, 72.toShort()); offset += 2
        System.arraycopy(groupMemberValue, 0, mintAccountData, offset, 72)

        val mintAccountBase64 = java.util.Base64.getEncoder().encodeToString(mintAccountData)

        // Response 2: getMultipleAccounts returns the SGT mint
        mockServer.enqueue(
            MockResponse()
                .setBody("""
                    {
                        "jsonrpc": "2.0",
                        "id": 2,
                        "result": {
                            "context": {"slot": 12345},
                            "value": [{
                                "data": ["$mintAccountBase64", "base64"],
                                "executable": false,
                                "lamports": 1461600,
                                "owner": "${SgtConstants.TOKEN_2022_PROGRAM_ID}",
                                "rentEpoch": 0,
                                "space": ${166 + extensionsSize}
                            }]
                        }
                    }
                """.trimIndent())
                .setResponseCode(200)
        )

        val result = SgtChecker.getWalletSgtInfo(
            walletAddress = testWalletAddress,
            rpcUrl = mockServer.url("/").toString()
        )

        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertTrue(info.hasSgt)
        assertEquals(4217L, info.memberNumber)
        assertEquals(fakeSgtMintBase58, info.sgtMintAddress)
        assertEquals("tokenAcctAddr123", info.sgtTokenAccountAddress)
    }

    @Test
    fun `getWalletSgtInfo returns failure on RPC error`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setBody("""
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "error": {
                            "code": -32600,
                            "message": "Invalid request"
                        }
                    }
                """.trimIndent())
                .setResponseCode(200)
        )

        val result = SgtChecker.getWalletSgtInfo(
            walletAddress = testWalletAddress,
            rpcUrl = mockServer.url("/").toString()
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SgtException)
    }
}
