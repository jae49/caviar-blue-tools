package cb.core.tools.sss.models

import java.time.Instant
import java.util.Base64

/**
 * Represents a single share of a split secret.
 * 
 * @property index The share index (x-coordinate in polynomial evaluation)
 * @property data The share data (y-coordinates for each byte of the secret)
 * @property metadata Additional metadata for validation and reconstruction
 */
data class SecretShare(
    val index: Int,
    val data: ByteArray,
    val metadata: ShareMetadata
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
    }

    /**
     * Validates this share against expected configuration.
     */
    fun isValidFor(config: SSSConfig): Boolean {
        return index <= config.totalShares &&
                metadata.threshold == config.threshold &&
                metadata.totalShares == config.totalShares &&
                metadata.secretSize <= config.secretMaxSize
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
        return "${SHARE_HEADER}_${SHARE_VERSION}_${index}_${metadata.toBase64()}_${Base64.getEncoder().encodeToString(data)}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SecretShare) return false

        return index == other.index &&
                data.contentEquals(other.data) &&
                metadata == other.metadata
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + data.contentHashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }

    override fun toString(): String {
        return "SecretShare(index=$index, dataSize=${data.size}, metadata=$metadata)"
    }

    companion object {
        const val SHARE_HEADER = "SSS"
        const val SHARE_VERSION = "1.0"
        private val SHARE_REGEX = Regex("^${SHARE_HEADER}_([^_]+)_([0-9]+)_([^_]+)_(.+)$")

        /**
         * Deserializes a share from a Base64-encoded string.
         */
        fun fromBase64(encoded: String): SecretShare {
            val match = SHARE_REGEX.matchEntire(encoded)
                ?: throw IllegalArgumentException("Invalid share format")

            val (_, version, indexStr, metadataStr, dataStr) = match.groupValues
            
            require(version == SHARE_VERSION) { 
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