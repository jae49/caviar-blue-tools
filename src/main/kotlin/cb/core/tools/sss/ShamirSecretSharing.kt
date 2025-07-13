package cb.core.tools.sss

import cb.core.tools.sss.models.*
import cb.core.tools.sss.validation.ShareValidator

/**
 * Main API interface for Shamir Secret Sharing operations.
 * 
 * This class provides a high-level, user-friendly interface for splitting
 * secrets into shares and reconstructing them. It handles all the complexity
 * of polynomial generation, share evaluation, and Lagrange interpolation
 * internally.
 * 
 * Example usage:
 * ```kotlin
 * val sss = ShamirSecretSharing()
 * val config = SSSConfig(threshold = 3, totalShares = 5)
 * 
 * // Split a secret
 * val secret = "my secret data".toByteArray()
 * val splitResult = sss.split(secret, config)
 * 
 * // Reconstruct from shares
 * val shares = splitResult.getOrNull()?.shares?.take(3) ?: emptyList()
 * val reconstructed = sss.reconstruct(shares)
 * ```
 */
class ShamirSecretSharing(
    private val splitter: SecretSplitter = SecretSplitter(),
    private val reconstructor: SecretReconstructor = SecretReconstructor()
) {
    
    /**
     * Splits a secret into shares according to the specified configuration.
     * 
     * @param secret The secret data to split (up to maxSecretSize bytes)
     * @param config Configuration specifying threshold and total shares
     * @return Success with shares and metadata, or Failure with error details
     */
    fun split(secret: ByteArray, config: SSSConfig): SSSResult<SplitResult> {
        return splitter.split(secret, config).map { splitResult ->
            SplitResult(splitResult.shares, splitResult.metadata)
        }
    }
    
    /**
     * Splits a string secret into shares.
     * 
     * Convenience method that converts the string to UTF-8 bytes before splitting.
     * 
     * @param secret The secret string to split
     * @param config Configuration specifying threshold and total shares
     * @return Success with shares and metadata, or Failure with error details
     */
    fun split(secret: String, config: SSSConfig): SSSResult<SplitResult> {
        return split(secret.toByteArray(Charsets.UTF_8), config)
    }
    
    /**
     * Reconstructs a secret from the provided shares.
     * 
     * Requires at least 'threshold' shares to successfully reconstruct the secret.
     * If metadata is provided, it will be used to validate the reconstruction.
     * 
     * @param shares The available secret shares
     * @param metadata Optional metadata for validation
     * @return Success with reconstructed secret bytes, or Failure with error details
     */
    fun reconstruct(
        shares: List<SecretShare>,
        metadata: ShareMetadata? = null
    ): SSSResult<ByteArray> {
        return reconstructor.reconstruct(shares, metadata)
    }
    
    /**
     * Reconstructs a string secret from the provided shares.
     * 
     * Convenience method that converts the reconstructed bytes to UTF-8 string.
     * 
     * @param shares The available secret shares
     * @param metadata Optional metadata for validation
     * @return Success with reconstructed secret string, or Failure with error details
     */
    fun reconstructString(
        shares: List<SecretShare>,
        metadata: ShareMetadata? = null
    ): SSSResult<String> {
        return reconstruct(shares, metadata).map { bytes ->
            String(bytes, Charsets.UTF_8)
        }
    }
    
    /**
     * Validates a set of shares without reconstructing the secret.
     * 
     * Checks that:
     * - Shares are well-formed
     * - Share indices are unique
     * - All shares have consistent data sizes
     * - Sufficient shares are provided (if metadata available)
     * 
     * @param shares The shares to validate
     * @param metadata Optional metadata for additional validation
     * @return Success if shares are valid, Failure with error details otherwise
     */
    fun validateShares(
        shares: List<SecretShare>,
        metadata: ShareMetadata? = null
    ): SSSResult<Unit> {
        // Use ShareValidator for comprehensive validation
        val validationResult = ShareValidator.validateSharesForReconstruction(shares)
        if (validationResult is SSSResult.Failure) {
            return when {
                validationResult.message.contains("hash", ignoreCase = true) -> 
                    SSSResult.Failure(SSSError.INVALID_SHARE, validationResult.message)
                validationResult.message.contains("insufficient", ignoreCase = true) -> 
                    SSSResult.Failure(SSSError.INSUFFICIENT_SHARES, validationResult.message)
                validationResult.message.contains("mismatch", ignoreCase = true) -> 
                    SSSResult.Failure(SSSError.INCOMPATIBLE_SHARES, validationResult.message)
                else -> 
                    SSSResult.Failure(SSSError.INVALID_SHARE, validationResult.message)
            }
        }
        
        // Additional validation against provided metadata if present
        if (metadata != null) {
            val shareMetadata = shares.first().metadata
            if (!shareMetadata.isCompatibleWith(metadata)) {
                return SSSResult.Failure(
                    SSSError.INCOMPATIBLE_SHARES,
                    "Shares metadata incompatible with provided metadata"
                )
            }
        }
        
        return SSSResult.Success(Unit)
    }
    
    /**
     * Result of a split operation containing shares and metadata.
     */
    data class SplitResult(
        val shares: List<SecretShare>,
        val metadata: ShareMetadata
    ) {
        /**
         * Gets shares as a map indexed by share index for easier access.
         */
        fun toShareMap(): Map<Int, SecretShare> {
            return shares.associateBy { it.index }
        }
        
        /**
         * Gets a specific subset of shares by their indices.
         */
        fun getSharesByIndices(indices: List<Int>): List<SecretShare> {
            val shareMap = toShareMap()
            return indices.mapNotNull { shareMap[it] }
        }
    }
    
    companion object {
        /**
         * Creates a default configuration for k-of-n secret sharing.
         * 
         * @param k The threshold (minimum shares needed)
         * @param n The total number of shares to create
         * @return SSS configuration
         */
        fun createConfig(k: Int, n: Int): SSSConfig {
            return SSSConfig(threshold = k, totalShares = n)
        }
    }
}