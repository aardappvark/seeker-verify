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

    // --- SKR Token Constants ---

    /** SKR Token Mint address (standard SPL token, 6 decimals) */
    const val SKR_TOKEN_MINT = "SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3"

    /** SKR Staking Program ID */
    const val SKR_STAKING_PROGRAM = "SKRskrmtL83pcL4YqLWt6iPefDqwXQWHSw9S9vz94BZ"

    /** SKR Inflation Program ID */
    const val SKR_INFLATION_PROGRAM = "SKRiHLtLyB8bbhcJ5HBPYMiLh9GcFLdPaSwozqLteha"

    /** SKR Stake Vault address */
    const val SKR_STAKE_VAULT = "8isViKbwhuhFhsv2t8vaFL74pKCqaFPQXo1KkeQwZbB8"

    /** Standard SPL Token Program ID */
    const val TOKEN_PROGRAM_ID = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
}
