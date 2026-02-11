package com.midmightbit.sgt

/**
 * Constants for Seeker Genesis Token (SGT) verification.
 *
 * These values are defined by Solana Mobile and used to identify
 * legitimate SGT tokens on-chain via Token-2022 extensions.
 */
object SgtConstants {

    /** The expected mint authority for SGT tokens */
    const val SGT_MINT_AUTHORITY = "GT2zuHVaZQYZSyQMgJPLzvkmyztfyXg2NJunqFp4p3A4"

    /** The expected metadata address AND token group mint for SGT tokens */
    const val SGT_METADATA_ADDRESS = "GT22s89nU4iWFkNXj1Bw6uYhJJWDRPpShHt4Bk8f99Te"

    /** Token-2022 (Token Extensions) program ID */
    const val TOKEN_2022_PROGRAM_ID = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"

    /** Default Solana mainnet-beta RPC endpoint (public, rate-limited) */
    const val DEFAULT_RPC_URL = "https://api.mainnet-beta.solana.com"

    /** Maximum addresses per getMultipleAccounts call */
    internal const val MAX_BATCH_SIZE = 100
}
