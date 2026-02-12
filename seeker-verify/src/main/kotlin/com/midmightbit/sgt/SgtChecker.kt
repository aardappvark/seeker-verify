package com.midmightbit.sgt

/**
 * Seeker Genesis Token (SGT) verification for Solana Seeker devices.
 *
 * Checks whether a Solana wallet holds a valid SGT — a soulbound Token-2022
 * NFT that proves ownership of a physical Seeker device.
 *
 * ## Usage
 * ```kotlin
 * val result = SgtChecker.checkWallet(
 *     walletAddress = "Base58WalletAddress...",
 *     rpcUrl = "https://mainnet.helius-rpc.com/?api-key=YOUR_KEY"
 * )
 * result.fold(
 *     onSuccess = { hasSgt -> /* true if wallet holds SGT */ },
 *     onFailure = { error -> /* network/parse error */ }
 * )
 * ```
 *
 * ## Verification Steps
 * 1. Fetch all Token-2022 token accounts for the wallet
 * 2. Extract mint addresses from those token accounts
 * 3. Fetch mint account data in batches
 * 4. Parse Token-2022 extensions and verify:
 *    - Mint Authority matches SGT authority
 *    - MetadataPointer authority + address match SGT values
 *    - TokenGroupMember group matches SGT group
 * 5. Returns `true` if any mint passes all checks
 *
 * ## Design
 * - Uses standard Solana JSON-RPC (works with any provider)
 * - All verification is on-chain — no external API dependency
 * - Thread-safe, stateless singleton
 *
 * @see SgtConstants for the known SGT constant addresses
 */
object SgtChecker {

    // Lazily decode the expected constant addresses from Base58 to raw bytes
    private val expectedMintAuthority: ByteArray by lazy {
        Base58.decode(SgtConstants.SGT_MINT_AUTHORITY)
    }
    private val expectedMetadataAddress: ByteArray by lazy {
        Base58.decode(SgtConstants.SGT_METADATA_ADDRESS)
    }

    /**
     * Detailed SGT information for a wallet.
     *
     * @property hasSgt Whether the wallet holds a valid SGT
     * @property memberNumber The SGT serial/member number (from TokenGroupMember extension)
     * @property sgtMintAddress The Base58-encoded mint address of the SGT token
     * @property sgtTokenAccountAddress The Base58-encoded token account holding the SGT
     */
    data class SgtInfo(
        val hasSgt: Boolean,
        val memberNumber: Long? = null,
        val sgtMintAddress: String? = null,
        val sgtTokenAccountAddress: String? = null
    )

    /**
     * Check if the wallet at [walletAddress] holds a valid Seeker Genesis Token.
     *
     * This makes 2+ RPC calls (getTokenAccountsByOwner + getMultipleAccounts)
     * and should be called from a coroutine scope with IO dispatcher.
     *
     * @param walletAddress Base58-encoded Solana wallet public key
     * @param rpcUrl Solana JSON-RPC endpoint URL (defaults to public mainnet-beta)
     * @return [Result.success] with `true` if wallet holds SGT, `false` if not.
     *         [Result.failure] with [SgtException] on network/parse errors.
     */
    suspend fun checkWallet(
        walletAddress: String,
        rpcUrl: String = SgtConstants.DEFAULT_RPC_URL
    ): Result<Boolean> {
        return getWalletSgtInfo(walletAddress, rpcUrl).map { it.hasSgt }
    }

    /**
     * Get detailed SGT information for a wallet, including the member/serial number.
     *
     * This makes 2+ RPC calls (getTokenAccountsByOwner + getMultipleAccounts)
     * and should be called from a coroutine scope with IO dispatcher.
     *
     * @param walletAddress Base58-encoded Solana wallet public key
     * @param rpcUrl Solana JSON-RPC endpoint URL (defaults to public mainnet-beta)
     * @return [Result.success] with [SgtInfo] containing SGT details.
     *         [Result.failure] with [SgtException] on network/parse errors.
     */
    suspend fun getWalletSgtInfo(
        walletAddress: String,
        rpcUrl: String = SgtConstants.DEFAULT_RPC_URL
    ): Result<SgtInfo> {
        val rpcClient = SolanaRpcClient(rpcUrl)

        // Step 1: Get all Token-2022 token accounts for the wallet
        val tokenAccountsResult = rpcClient.getTokenAccountsByOwner(walletAddress)
        val tokenAccounts = tokenAccountsResult.getOrElse {
            return Result.failure(it)
        }

        if (tokenAccounts.isEmpty()) {
            return Result.success(SgtInfo(hasSgt = false))
        }

        // Step 2: Extract mint addresses from token accounts, tracking which token account holds which mint
        val mintToTokenAccount = mutableMapOf<String, String>()
        val mintAddresses = tokenAccounts.mapNotNull { (tokenAccountPubkey, base64Data) ->
            Token2022Parser.extractMintFromTokenAccount(base64Data)?.let {
                val mintAddr = Base58.encode(it)
                mintToTokenAccount[mintAddr] = tokenAccountPubkey
                mintAddr
            }
        }.distinct()

        if (mintAddresses.isEmpty()) {
            return Result.success(SgtInfo(hasSgt = false))
        }

        // Step 3: Fetch mint account data (auto-batched in 100s)
        val mintDataResult = rpcClient.getMultipleAccountsInfo(mintAddresses)
        val mintDataMap = mintDataResult.getOrElse {
            return Result.failure(it)
        }

        // Step 4: Parse each mint and check SGT criteria
        for ((mintAddr, base64Data) in mintDataMap) {
            val parsed = Token2022Parser.parseMintAccount(base64Data) ?: continue

            if (isValidSgt(parsed)) {
                return Result.success(SgtInfo(
                    hasSgt = true,
                    memberNumber = parsed.groupMemberNumber,
                    sgtMintAddress = mintAddr,
                    sgtTokenAccountAddress = mintToTokenAccount[mintAddr]
                ))
            }
        }

        return Result.success(SgtInfo(hasSgt = false))
    }

    /**
     * Verifies that a parsed mint's extension data matches all SGT criteria.
     *
     * Three checks must ALL pass:
     * 1. Mint authority == SGT_MINT_AUTHORITY
     * 2. MetadataPointer authority == SGT_MINT_AUTHORITY AND address == SGT_METADATA_ADDRESS
     * 3. TokenGroupMember group == SGT_METADATA_ADDRESS (same as group mint)
     */
    private fun isValidSgt(mint: Token2022Parser.MintExtensionData): Boolean {
        // Check 1: Mint authority
        val mintAuth = mint.mintAuthority ?: return false
        if (!mintAuth.contentEquals(expectedMintAuthority)) return false

        // Check 2: MetadataPointer authority and address
        val metaAuth = mint.metadataPointerAuthority ?: return false
        if (!metaAuth.contentEquals(expectedMintAuthority)) return false

        val metaAddr = mint.metadataPointerAddress ?: return false
        if (!metaAddr.contentEquals(expectedMetadataAddress)) return false

        // Check 3: TokenGroupMember group
        val group = mint.groupMemberGroup ?: return false
        if (!group.contentEquals(expectedMetadataAddress)) return false

        return true
    }
}
