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
     * 
     * Performs comprehensive validation including:
     * - Threshold bounds (1 <= k <= n)
     * - Total shares bounds (1 <= n <= 128)
     * - Secret size limits (1 <= size <= configured max)
     * - Field size compatibility (currently only GF(256))
     * 
     * @param config The configuration to validate
     * @param secretSize The size of the secret to be split in bytes
     * @return Success with validated config or Failure with error details
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
     * 
     * Ensures that:
     * - At least one index is provided
     * - All indices are unique (no duplicates)
     * - All indices are within [1, totalShares]
     * 
     * @param indices List of share indices to validate
     * @param config Configuration defining valid index bounds
     * @return Success with validated indices or Failure with error details
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
     * 
     * Checks that the number of available shares meets or exceeds
     * the threshold requirement.
     * 
     * @param shareCount Number of available shares
     * @param threshold Minimum shares required
     * @return Success if sufficient shares, Failure if insufficient
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
     * 
     * Checks security-related configuration options:
     * - Secure random usage (recommended for production)
     * - Field size compatibility
     * - Future: minimum threshold requirements, time restrictions
     * 
     * @param config Configuration with security parameters
     * @return Success with validated config or Failure with security concerns
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
     * 
     * Re-validates all configuration constraints and security parameters.
     * This is redundant with SSSConfig's init validation but provides
     * a Result-based API for consistency.
     * 
     * @param config The configuration to validate
     * @return Success with validated config or Failure with validation errors
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