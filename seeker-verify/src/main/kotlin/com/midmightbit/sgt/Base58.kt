package com.midmightbit.sgt

import java.math.BigInteger

/**
 * Standalone Base58 encoder/decoder for Solana public key conversion.
 * Uses the Bitcoin/Solana Base58 alphabet (no 0, O, I, l).
 */
internal object Base58 {

    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val BASE = BigInteger.valueOf(58)

    /**
     * Encode a byte array to a Base58 string.
     */
    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        var num = BigInteger(1, bytes)
        val sb = StringBuilder()

        while (num > BigInteger.ZERO) {
            val divRem = num.divideAndRemainder(BASE)
            sb.append(ALPHABET[divRem[1].toInt()])
            num = divRem[0]
        }

        // Preserve leading zeros as '1' characters
        for (byte in bytes) {
            if (byte.toInt() == 0) {
                sb.append(ALPHABET[0])
            } else {
                break
            }
        }

        return sb.reverse().toString()
    }

    /**
     * Decode a Base58 string to a byte array.
     * @throws IllegalArgumentException if the string contains invalid characters
     */
    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)

        var num = BigInteger.ZERO
        for (c in input) {
            val index = ALPHABET.indexOf(c)
            require(index >= 0) { "Invalid Base58 character: '$c'" }
            num = num.multiply(BASE).add(BigInteger.valueOf(index.toLong()))
        }

        val bytes = num.toByteArray()

        // BigInteger prepends a zero byte for positive numbers with high bit set
        val stripped = if (bytes.size > 1 && bytes[0].toInt() == 0) {
            bytes.copyOfRange(1, bytes.size)
        } else {
            bytes
        }

        // Count leading '1' characters (represent leading zero bytes)
        val leadingOnes = input.takeWhile { it == '1' }.length

        // Pad with leading zeros if needed to produce correct-length output
        return if (leadingOnes > 0) {
            ByteArray(leadingOnes) + stripped
        } else {
            stripped
        }
    }
}
