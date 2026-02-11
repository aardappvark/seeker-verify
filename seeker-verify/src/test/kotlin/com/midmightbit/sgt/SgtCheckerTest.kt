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
}
