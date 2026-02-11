package com.midmightbit.sgt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight Solana JSON-RPC client using OkHttp.
 *
 * Makes standard Solana RPC calls — not vendor-specific (e.g. no Helius V2).
 * Works with any Solana RPC provider: public endpoint, Helius, QuickNode, etc.
 *
 * @param rpcUrl Solana JSON-RPC endpoint URL
 * @param client Optional OkHttpClient (injectable for testing)
 */
internal class SolanaRpcClient(
    private val rpcUrl: String = SgtConstants.DEFAULT_RPC_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mediaType = "application/json".toMediaType()
    private val requestIdCounter = AtomicInteger(1)

    /**
     * Fetches all Token-2022 token accounts owned by the given wallet.
     *
     * Uses standard `getTokenAccountsByOwner` filtered by Token-2022 program ID.
     * Returns the raw base64-encoded account data for each token account.
     *
     * @param walletAddress Base58-encoded wallet public key
     * @return List of (tokenAccountPubkey, base64AccountData) pairs
     */
    suspend fun getTokenAccountsByOwner(
        walletAddress: String
    ): Result<List<Pair<String, String>>> = withContext(Dispatchers.IO) {
        try {
            val params = buildJsonArray {
                add(walletAddress)
                addJsonObject {
                    put("programId", SgtConstants.TOKEN_2022_PROGRAM_ID)
                }
                addJsonObject {
                    put("encoding", "base64")
                }
            }

            val rpcRequest = RpcRequest(
                id = requestIdCounter.getAndIncrement(),
                method = "getTokenAccountsByOwner",
                params = params
            )

            val responseBody = executeRpc(rpcRequest)
            val response = json.decodeFromString<TokenAccountsResponse>(responseBody)

            if (response.error != null) {
                return@withContext Result.failure(
                    SgtException("RPC error ${response.error.code}: ${response.error.message}")
                )
            }

            val accounts = response.result?.value?.mapNotNull { tokenAccount ->
                try {
                    val base64Data = tokenAccount.account.data[0].jsonPrimitive.content
                    tokenAccount.pubkey to base64Data
                } catch (e: Exception) {
                    null // Skip malformed entries
                }
            } ?: emptyList()

            Result.success(accounts)
        } catch (e: SgtException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(SgtException("Failed to fetch token accounts: ${e.message}", e))
        }
    }

    /**
     * Fetches account info for multiple addresses in a single RPC call.
     * Automatically batches in chunks of [SgtConstants.MAX_BATCH_SIZE] (100).
     *
     * @param addresses List of Base58-encoded account addresses (typically mint addresses)
     * @return Map of address -> base64AccountData (missing accounts are excluded)
     */
    suspend fun getMultipleAccountsInfo(
        addresses: List<String>
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            val allResults = mutableMapOf<String, String>()

            for (batch in addresses.chunked(SgtConstants.MAX_BATCH_SIZE)) {
                val params = buildJsonArray {
                    addJsonArray {
                        batch.forEach { add(it) }
                    }
                    addJsonObject {
                        put("encoding", "base64")
                    }
                }

                val rpcRequest = RpcRequest(
                    id = requestIdCounter.getAndIncrement(),
                    method = "getMultipleAccounts",
                    params = params
                )

                val responseBody = executeRpc(rpcRequest)
                val response = json.decodeFromString<MultipleAccountsResponse>(responseBody)

                if (response.error != null) {
                    return@withContext Result.failure(
                        SgtException("RPC error ${response.error.code}: ${response.error.message}")
                    )
                }

                response.result?.value?.forEachIndexed { index, accountData ->
                    if (accountData != null && index < batch.size) {
                        try {
                            val base64Data = accountData.data[0].jsonPrimitive.content
                            allResults[batch[index]] = base64Data
                        } catch (e: Exception) {
                            // Skip malformed entries
                        }
                    }
                }
            }

            Result.success(allResults)
        } catch (e: SgtException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(SgtException("Failed to fetch account info: ${e.message}", e))
        }
    }

    /**
     * Executes a single JSON-RPC request and returns the raw response body string.
     */
    private fun executeRpc(rpcRequest: RpcRequest): String {
        val bodyString = json.encodeToString(RpcRequest.serializer(), rpcRequest)
        val body = bodyString.toRequestBody(mediaType)

        val httpRequest = Request.Builder()
            .url(rpcUrl)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        val response = client.newCall(httpRequest).execute()

        if (!response.isSuccessful) {
            throw SgtException("HTTP ${response.code}: ${response.message}")
        }

        return response.body?.string()
            ?: throw SgtException("Empty response body from RPC")
    }
}

/**
 * Exception type for SGT verification errors.
 * Used throughout the seeker-verify module for consistent error handling.
 */
class SgtException(message: String, cause: Throwable? = null) : Exception(message, cause)
