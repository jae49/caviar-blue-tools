package cb.core.tools.sss.validation

import cb.core.tools.sss.models.SSSConfig
import cb.core.tools.sss.models.SSSError
import cb.core.tools.sss.models.SSSResult

/**
 * Validates SSS configuration parameters.
 */
object ConfigValidator {

    /**
     * Validates a configuration for secret splitting.
     */
    fun validateForSplitting(config: SSSConfig, secretSize: Int): SSSResult<SSSConfig> {
        // Check threshold bounds
        if (config.threshold < 1) {
            return SSSResult.failure(
                SSSError.INVALID_CONFIG,
                "Threshold must be at least 1, got: ${config.threshold}"
            )
        }

        if (config.threshold > config.totalShares) {
            return SSSResult.failure(
                SSSError.INVALID_CONFIG,
                "Threshold (${config.threshold}) cannot exceed total shares (${config.totalShares})"
            )
        }

        // Check total shares bounds
        if (config.totalShares < 1) {
            return SSSResult.failure(
                SSSError.INVALID_CONFIG,
                "Total shares must be at least 1, got: ${config.totalShares}"
            )
        }

        if (config.totalShares > SSSConfig.MAX_SHARES) {
            return SSSResult.failure(
                SSSError.INVALID_CONFIG,
                "Total shares (${config.totalShares}) exceeds maximum allowed (${SSSConfig.MAX_SHARES})"
            )
        }

        // Check secret size
        if (secretSize < 1) {
            return SSSResult.failure(
                SSSError.INVALID_SECRET,
                "Secret size must be at least 1 byte, got: $secretSize"
            )
        }

        if (secretSize > config.secretMaxSize) {
            return SSSResult.failure(
                SSSError.INVALID_SECRET,
                "Secret size ($secretSize bytes) exceeds configured maximum (${config.secretMaxSize} bytes)"
            )
        }

        if (secretSize > SSSConfig.MAX_SECRET_SIZE) {
            return SSSResult.failure(
                SSSError.INVALID_SECRET,
                "Secret size ($secretSize bytes) exceeds absolute maximum (${SSSConfig.MAX_SECRET_SIZE} bytes)"
            )
        }

        // Check field size
        if (config.fieldSize != SSSConfig.DEFAULT_FIELD_SIZE) {
            return SSSResult.failure(
                SSSError.INVALID_CONFIG,
                "Only GF(256) is currently supported, got field size: ${config.fieldSize}"
            )
        }

        // Warn about trivial configurations
        if (config.threshold == 1) {
            // This is valid but potentially insecure
            // In production, might want to log a warning
        }

        if (config.threshold == config.totalShares) {
            // This requires all shares for reconstruction
            // Valid but defeats redundancy purpose
        }

        return SSSResult.success(config)
    }

    /**
     * Validates share indices are unique and within bounds.
     */
    fun validateShareIndices(indices: List<Int>, config: SSSConfig): SSSResult<List<Int>> {
        if (indices.isEmpty()) {
            return SSSResult.failure(
                SSSError.INVALID_SHARE,
                "No share indices provided"
            )
        }

        // Check for duplicates
        val uniqueIndices = indices.toSet()
        if (uniqueIndices.size != indices.size) {
            return SSSResult.failure(
                SSSError.INVALID_SHARE,
                "Duplicate share indices found"
            )
        }

        // Check bounds
        for (index in indices) {
            if (index < 1 || index > config.totalShares) {
                return SSSResult.failure(
                    SSSError.INVALID_SHARE,
                    "Share index $index out of bounds [1, ${config.totalShares}]"
                )
            }
        }

        return SSSResult.success(indices)
    }

    /**
     * Validates if enough shares are available for reconstruction.
     */
    fun validateShareCount(shareCount: Int, threshold: Int): SSSResult<Int> {
        return when {
            shareCount < threshold -> SSSResult.failure(
                SSSError.INSUFFICIENT_SHARES,
                "Insufficient shares for reconstruction: have $shareCount, need at least $threshold"
            )
            else -> SSSResult.success(shareCount)
        }
    }

    /**
     * Validates cryptographic parameters.
     */
    fun validateSecurityParameters(config: SSSConfig): SSSResult<SSSConfig> {
        // For now, we only support GF(256) with secure random
        if (!config.useSecureRandom) {
            // Warning: using non-secure random is only for testing
            // In production, this should be enforced
        }

        // Additional security checks can be added here
        // - Minimum threshold requirements
        // - Maximum share distribution limits
        // - Time-based restrictions

        return SSSResult.success(config)
    }

    /**
     * Performs comprehensive validation for a configuration.
     */
    fun validate(config: SSSConfig): SSSResult<SSSConfig> {
        // This validation is already done in the SSSConfig init block,
        // but we can add additional runtime checks here
        return try {
            // Force re-validation by creating a new instance
            val validated = config.copy()
            validateSecurityParameters(validated)
        } catch (e: IllegalArgumentException) {
            SSSResult.failure(
                SSSError.INVALID_CONFIG,
                e.message ?: "Invalid configuration",
                e
            )
        }
    }
}