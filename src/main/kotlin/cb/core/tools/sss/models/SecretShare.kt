package cb.core.tools.sss.models

import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

/**
 * Represents a single share of a split secret.
 * 
 * @property index The share index (x-coordinate in polynomial evaluation)
 * @property data The share data (y-coordinates for each byte of the secret)
 * @property metadata Additional metadata for validation and reconstruction
 * @property dataHash SHA-256 hash of the share data for integrity checking
 */
data class SecretShare(
    val index: Int,
    val data: ByteArray,
    val metadata: ShareMetadata,
    val dataHash: ByteArray = computeDataHash(index, data, metadata)
) {
    init {
        require(index > 0) { 
            "Share index must be positive, got: $index" 
        }
        require(index <= SSSConfig.MAX_SHARES) { 
            "Share index ($index) exceeds maximum allowed (${SSSConfig.MAX_SHARES})" 
        }
        require(data.isNotEmpty()) { 
            "Share data cannot be empty" 
        }
        require(data.size == metadata.secretSize) { 
            "Share data size (${data.size}) must match secret size (${metadata.secretSize})" 
        }
        require(dataHash.size == 32) { 
            "Data hash must be SHA-256 (32 bytes), got: ${dataHash.size} bytes" 
        }
    }

    /**
     * Verifies the integrity of the share data using the stored hash.
     */
    fun verifyIntegrity(): Boolean {
        val expectedHash = computeDataHash(index, data, metadata)
        return dataHash.contentEquals(expectedHash)
    }

    /**
     * Validates this share against expected configuration.
     */
    fun isValidFor(config: SSSConfig): Boolean {
        return index <= config.totalShares &&
                metadata.threshold == config.threshold &&
                metadata.totalShares == config.totalShares &&
                metadata.secretSize <= config.secretMaxSize &&
                verifyIntegrity()
    }

    /**
     * Checks if this share is compatible with another share for reconstruction.
     */
    fun isCompatibleWith(other: SecretShare): Boolean {
        return metadata.isCompatibleWith(other.metadata) && index != other.index
    }

    /**
     * Serializes this share to a Base64-encoded string for storage/transmission.
     */
    fun toBase64(): String {
        return "${SHARE_HEADER}_${SHARE_VERSION}_${index}_${metadata.toBase64()}_${Base64.getEncoder().encodeToString(data)}_${Base64.getEncoder().encodeToString(dataHash)}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SecretShare) return false

        return index == other.index &&
                data.contentEquals(other.data) &&
                metadata == other.metadata &&
                dataHash.contentEquals(other.dataHash)
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + data.contentHashCode()
        result = 31 * result + metadata.hashCode()
        result = 31 * result + dataHash.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "SecretShare(index=$index, dataSize=${data.size}, metadata=$metadata)"
    }

    companion object {
        const val SHARE_HEADER = "SSS"
        const val SHARE_VERSION = "2.0"  // Updated for integrity checking
        const val SHARE_VERSION_LEGACY = "1.0"
        private val SHARE_REGEX = Regex("^${SHARE_HEADER}_([^_]+)_([0-9]+)_([^_]+)_([^_]+)_(.*)$")
        private val SHARE_REGEX_LEGACY = Regex("^${SHARE_HEADER}_([^_]+)_([0-9]+)_([^_]+)_(.+)$")
        private val digest = MessageDigest.getInstance("SHA-256")

        /**
         * Computes SHA-256 hash of share data including index and metadata.
         */
        fun computeDataHash(index: Int, data: ByteArray, metadata: ShareMetadata): ByteArray {
            digest.update(index.toString().toByteArray())
            digest.update(data)
            digest.update(metadata.shareSetId.toByteArray())
            return digest.digest()
        }

        /**
         * Deserializes a share from a Base64-encoded string.
         */
        fun fromBase64(encoded: String): SecretShare {
            // Try new format first
            val match = SHARE_REGEX.matchEntire(encoded)
            if (match != null) {
                val groupValues = match.groupValues
                val version = groupValues[1]
                val indexStr = groupValues[2]
                val metadataStr = groupValues[3]
                val dataStr = groupValues[4]
                val hashStr = if (groupValues.size > 5) groupValues[5] else ""
                
                require(version == SHARE_VERSION) { 
                    "Unsupported share version: $version" 
                }

                val index = indexStr.toIntOrNull() 
                    ?: throw IllegalArgumentException("Invalid share index: $indexStr")
                
                val metadata = ShareMetadata.fromBase64(metadataStr)
                val data = Base64.getDecoder().decode(dataStr)
                val dataHash = if (hashStr.isNotEmpty()) {
                    Base64.getDecoder().decode(hashStr)
                } else {
                    computeDataHash(index, data, metadata)
                }

                return SecretShare(index, data, metadata, dataHash)
            }
            
            // Try legacy format
            val legacyMatch = SHARE_REGEX_LEGACY.matchEntire(encoded)
                ?: throw IllegalArgumentException("Invalid share format")

            val (_, version, indexStr, metadataStr, dataStr) = legacyMatch.groupValues
            
            require(version == SHARE_VERSION_LEGACY) { 
                "Unsupported share version: $version" 
            }

            val index = indexStr.toIntOrNull() 
                ?: throw IllegalArgumentException("Invalid share index: $indexStr")
            
            val metadata = ShareMetadata.fromBase64(metadataStr)
            val data = Base64.getDecoder().decode(dataStr)

            return SecretShare(index, data, metadata)
        }
    }
}