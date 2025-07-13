package cb.core.tools.sss.models

import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

/**
 * Metadata associated with each secret share for validation and reconstruction.
 * 
 * @property threshold The minimum number of shares required for reconstruction
 * @property totalShares The total number of shares that were generated
 * @property secretSize The size of the original secret in bytes
 * @property secretHash SHA-256 hash of the original secret for integrity verification
 * @property timestamp When the share was created
 * @property shareSetId Unique identifier for this set of shares
 */
data class ShareMetadata(
    val threshold: Int,
    val totalShares: Int,
    val secretSize: Int,
    val secretHash: ByteArray,
    val timestamp: Instant = Instant.now(),
    val shareSetId: String = generateShareSetId()
) {
    init {
        require(threshold > 0) { 
            "Threshold must be positive, got: $threshold" 
        }
        require(totalShares >= threshold) { 
            "Total shares ($totalShares) must be at least threshold ($threshold)" 
        }
        require(secretSize > 0) { 
            "Secret size must be positive, got: $secretSize" 
        }
        require(secretHash.size == 32) { 
            "Secret hash must be SHA-256 (32 bytes), got: ${secretHash.size} bytes" 
        }
        require(shareSetId.isNotBlank()) { 
            "Share set ID cannot be blank" 
        }
    }

    /**
     * Checks if this metadata is compatible with another for share combination.
     */
    fun isCompatibleWith(other: ShareMetadata): Boolean {
        return threshold == other.threshold &&
                totalShares == other.totalShares &&
                secretSize == other.secretSize &&
                secretHash.contentEquals(other.secretHash) &&
                shareSetId == other.shareSetId
    }

    /**
     * Validates if a reconstructed secret matches the expected hash.
     */
    fun validateSecret(secret: ByteArray): Boolean {
        return secret.size == secretSize && 
                computeSecretHash(secret).contentEquals(secretHash)
    }

    /**
     * Serializes metadata to Base64 for embedding in shares.
     */
    fun toBase64(): String {
        val parts = listOf(
            threshold.toString(),
            totalShares.toString(),
            secretSize.toString(),
            Base64.getEncoder().encodeToString(secretHash),
            timestamp.toEpochMilli().toString(),
            shareSetId
        )
        return Base64.getEncoder().encodeToString(parts.joinToString("|").toByteArray())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShareMetadata) return false

        return threshold == other.threshold &&
                totalShares == other.totalShares &&
                secretSize == other.secretSize &&
                secretHash.contentEquals(other.secretHash) &&
                shareSetId == other.shareSetId
    }

    override fun hashCode(): Int {
        var result = threshold
        result = 31 * result + totalShares
        result = 31 * result + secretSize
        result = 31 * result + secretHash.contentHashCode()
        result = 31 * result + shareSetId.hashCode()
        return result
    }

    override fun toString(): String {
        return "ShareMetadata(threshold=$threshold, totalShares=$totalShares, " +
                "secretSize=$secretSize, shareSetId=$shareSetId, timestamp=$timestamp)"
    }

    companion object {
        private val digest = MessageDigest.getInstance("SHA-256")

        /**
         * Computes SHA-256 hash of a secret.
         */
        fun computeSecretHash(secret: ByteArray): ByteArray {
            return digest.digest(secret)
        }

        /**
         * Generates a unique share set identifier.
         */
        fun generateShareSetId(): String {
            val timestamp = System.currentTimeMillis()
            val random = (0..999999).random()
            return "$timestamp-$random"
        }

        /**
         * Deserializes metadata from Base64.
         */
        fun fromBase64(encoded: String): ShareMetadata {
            val decoded = String(Base64.getDecoder().decode(encoded))
            val parts = decoded.split("|")
            
            require(parts.size == 6) { 
                "Invalid metadata format, expected 6 parts, got ${parts.size}" 
            }

            return ShareMetadata(
                threshold = parts[0].toInt(),
                totalShares = parts[1].toInt(),
                secretSize = parts[2].toInt(),
                secretHash = Base64.getDecoder().decode(parts[3]),
                timestamp = Instant.ofEpochMilli(parts[4].toLong()),
                shareSetId = parts[5]
            )
        }

        /**
         * Creates metadata for a secret and configuration.
         */
        fun create(secret: ByteArray, config: SSSConfig): ShareMetadata {
            return ShareMetadata(
                threshold = config.threshold,
                totalShares = config.totalShares,
                secretSize = secret.size,
                secretHash = computeSecretHash(secret)
            )
        }
    }
}