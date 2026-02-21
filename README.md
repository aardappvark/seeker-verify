# seeker-verify

Native Android/Kotlin library for verifying **Seeker Genesis Token (SGT)** ownership on Solana. Checks on-chain whether a wallet holds a valid SGT — a soulbound Token-2022 NFT that proves ownership of a physical [Solana Seeker](https://solanamobile.com/seeker) device.

## Features

- On-chain SGT verification via standard Solana JSON-RPC (works with any RPC provider)
- Extracts SGT member/serial number from Token-2022 extensions
- Returns SKR token constants for ecosystem integration
- No external API dependencies — all verification is on-chain
- Thread-safe, stateless, coroutine-based
- Comprehensive test suite with MockWebServer

## Installation

Add JitPack to your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}
```

Add the dependency to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.aardappvark:seeker-verify:1.1.0")
}
```

## Quick Start

### Check if a wallet holds SGT

```kotlin
import com.midmightbit.sgt.SgtChecker

val result = SgtChecker.checkWallet(
    walletAddress = "YourBase58WalletAddress...",
    rpcUrl = "https://mainnet.helius-rpc.com/?api-key=YOUR_KEY"
)

result.fold(
    onSuccess = { hasSgt ->
        if (hasSgt) {
            // Wallet holds a valid Seeker Genesis Token
        }
    },
    onFailure = { error ->
        // Network or parse error
    }
)
```

### Get detailed SGT info (including member number)

```kotlin
val result = SgtChecker.getWalletSgtInfo(
    walletAddress = "YourBase58WalletAddress...",
    rpcUrl = "https://mainnet.helius-rpc.com/?api-key=YOUR_KEY"
)

result.fold(
    onSuccess = { sgtInfo ->
        if (sgtInfo.hasSgt) {
            println("SGT Member #${sgtInfo.memberNumber}")
            println("Mint: ${sgtInfo.sgtMintAddress}")
        }
    },
    onFailure = { error ->
        // Handle error
    }
)
```

### Access SKR token constants

```kotlin
import com.midmightbit.sgt.SgtConstants

val skrMint = SgtConstants.SKR_TOKEN_MINT           // SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3
val tokenProgram = SgtConstants.TOKEN_PROGRAM_ID     // TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA
val token2022 = SgtConstants.TOKEN_2022_PROGRAM_ID   // TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb
```

## How It Works

1. Fetches all Token-2022 token accounts for the wallet via `getTokenAccountsByOwner`
2. Extracts mint addresses from token account data
3. Fetches mint account data in batches via `getMultipleAccounts`
4. Parses Token-2022 extensions on each mint and verifies:
   - **Mint Authority** matches SGT authority (`GT2zuHVaZQYZSyQMgJPLzvkmyztfyXg2NJunqFp4p3A4`)
   - **MetadataPointer** authority and address match SGT values
   - **TokenGroupMember** group matches SGT group
5. Extracts member number from the TokenGroupMember extension

## API Reference

### `SgtChecker`

| Method | Returns | Description |
|--------|---------|-------------|
| `checkWallet(walletAddress, rpcUrl)` | `Result<Boolean>` | Simple SGT ownership check |
| `getWalletSgtInfo(walletAddress, rpcUrl)` | `Result<SgtInfo>` | Detailed SGT info with member number |

### `SgtInfo`

| Property | Type | Description |
|----------|------|-------------|
| `hasSgt` | `Boolean` | Whether wallet holds valid SGT |
| `memberNumber` | `Long?` | SGT serial/member number |
| `sgtMintAddress` | `String?` | Base58 mint address of the SGT |
| `sgtTokenAccountAddress` | `String?` | Base58 token account holding the SGT |

### `SgtConstants`

SGT verification constants, SKR token addresses, and Solana program IDs.

## Requirements

- Android SDK 26+ (Android 8.0 Oreo)
- Kotlin 1.9+
- A Solana RPC endpoint (Helius, Alchemy, QuickNode, etc. recommended for production)

## Used By

- [AardAppvark Toolkit](https://github.com/aardappvark/ADappvarkToolkit) — Batch dApp management for Solana Seeker
- [Sol Clock](https://github.com/aardappvark/solclock-legal) — Solana clock screensaver for Seeker
- [RadioSol](https://github.com/aardappvark) — Internet radio player for Seeker

## License

Apache License 2.0 — see [LICENSE](LICENSE)

## Contributing

Issues and pull requests welcome at [github.com/aardappvark/seeker-verify](https://github.com/aardappvark/seeker-verify).
