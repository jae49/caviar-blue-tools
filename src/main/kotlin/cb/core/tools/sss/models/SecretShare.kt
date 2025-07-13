/*
 * Copyright 2025 John Engelman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
     * 
     * Computes the expected hash from the current data and compares it to the stored hash.
     * This detects any tampering or corruption of the share data.
     * 
     * @return true if the integrity check passes, false if data has been modified
     */
    fun verifyIntegrity(): Boolean {
        val expectedHash = computeDataHash(index, data, metadata)
        return dataHash.contentEquals(expectedHash)
    }

    /**
     * Validates this share against expected configuration.
     * 
     * Checks that:
     * - The share index is within the allowed range for the configuration
     * - The share metadata matches the configuration parameters
     * - The secret size is within allowed bounds
     * - The share data integrity is intact
     * 
     * @param config The configuration to validate against
     * @return true if the share is valid for the given configuration
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
     * 
     * Two shares are compatible if:
     * - They have compatible metadata (same secret, configuration, etc.)
     * - They have different indices (no duplicate shares)
     * 
     * @param other The other share to check compatibility with
     * @return true if the shares can be used together for reconstruction
     */
    fun isCompatibleWith(other: SecretShare): Boolean {
        return metadata.isCompatibleWith(other.metadata) && index != other.index
    }

    /**
     * Serializes this share to a Base64-encoded string for storage/transmission.
     * 
     * The format is: SSS_version_index_metadata_data_hash
     * All binary data is Base64-encoded for safe text transmission.
     * 
     * @return Base64-encoded string representation of this share
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
         * 
         * The hash includes:
         * - Share index (to detect index tampering)
         * - Share data (to detect data corruption)
         * - Share set ID (to detect mixing shares from different operations)
         * 
         * @param index The share index
         * @param data The share data bytes
         * @param metadata The share metadata containing shareSetId
         * @return 32-byte SHA-256 hash
         */
        fun computeDataHash(index: Int, data: ByteArray, metadata: ShareMetadata): ByteArray {
            digest.update(index.toString().toByteArray())
            digest.update(data)
            digest.update(metadata.shareSetId.toByteArray())
            return digest.digest()
        }

        /**
         * Deserializes a share from a Base64-encoded string.
         * 
         * Supports both v2.0 format (with integrity hash) and v1.0 legacy format.
         * For legacy shares, the integrity hash is computed during deserialization.
         * 
         * @param encoded Base64-encoded share string
         * @return Deserialized SecretShare instance
         * @throws IllegalArgumentException if the format is invalid or unsupported
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