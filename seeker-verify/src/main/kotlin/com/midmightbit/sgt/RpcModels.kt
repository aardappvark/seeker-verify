package com.midmightbit.sgt

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/**
 * JSON-RPC 2.0 request/response models for Solana RPC calls.
 * All models are internal — they are implementation details of the module.
 */

// --- JSON-RPC Envelope ---

@Serializable
internal data class RpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int = 1,
    val method: String,
    val params: JsonArray
)

@Serializable
internal data class RpcError(
    val code: Int,
    val message: String
)

// --- getTokenAccountsByOwner Response ---

@Serializable
internal data class TokenAccountsResponse(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val result: TokenAccountsResult? = null,
    val error: RpcError? = null
)

@Serializable
internal data class TokenAccountsResult(
    val context: RpcContext,
    val value: List<TokenAccountInfo>
)

@Serializable
internal data class RpcContext(val slot: Long)

@Serializable
internal data class TokenAccountInfo(
    val pubkey: String,
    val account: AccountData
)

@Serializable
internal data class AccountData(
    val data: JsonArray, // ["base64_encoded_data", "base64"]
    val executable: Boolean = false,
    val lamports: Long = 0,
    val owner: String = "",
    val rentEpoch: JsonElement? = null, // u64 — can overflow Long, and we don't use it
    val space: Int = 0
)

// --- getMultipleAccounts Response ---

@Serializable
internal data class MultipleAccountsResponse(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val result: MultipleAccountsResult? = null,
    val error: RpcError? = null
)

@Serializable
internal data class MultipleAccountsResult(
    val context: RpcContext,
    val value: List<AccountData?> // null for accounts that don't exist
)
