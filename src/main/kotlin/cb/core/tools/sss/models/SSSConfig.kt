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

/**
 * Configuration for Shamir Secret Sharing operations.
 * 
 * @property threshold The minimum number of shares required to reconstruct the secret (k)
 * @property totalShares The total number of shares to generate (n)
 * @property secretMaxSize Maximum allowed size of the secret in bytes
 * @property fieldSize The size of the Galois Field (default: 256 for GF(256))
 * @property useSecureRandom Whether to use cryptographically secure random generation
 */
data class SSSConfig(
    val threshold: Int,
    val totalShares: Int,
    val secretMaxSize: Int = DEFAULT_SECRET_MAX_SIZE,
    val fieldSize: Int = DEFAULT_FIELD_SIZE,
    val useSecureRandom: Boolean = true
) {
    init {
        require(threshold > 0) { 
            "Threshold must be positive, got: $threshold" 
        }
        require(totalShares > 0) { 
            "Total shares must be positive, got: $totalShares" 
        }
        require(threshold <= totalShares) { 
            "Threshold ($threshold) cannot exceed total shares ($totalShares)" 
        }
        require(totalShares <= MAX_SHARES) { 
            "Total shares ($totalShares) cannot exceed maximum allowed ($MAX_SHARES)" 
        }
        require(secretMaxSize > 0) { 
            "Secret max size must be positive, got: $secretMaxSize" 
        }
        require(secretMaxSize <= MAX_SECRET_SIZE) { 
            "Secret max size ($secretMaxSize) cannot exceed maximum allowed ($MAX_SECRET_SIZE)" 
        }
        require(fieldSize == DEFAULT_FIELD_SIZE) { 
            "Currently only GF(256) is supported, got field size: $fieldSize" 
        }
    }

    /**
     * Validates if a secret can be processed with this configuration.
     * 
     * Checks that the secret size is within allowed bounds (1 to secretMaxSize bytes).
     * 
     * @param secretSize The size of the secret in bytes
     * @return true if the secret size is valid, false otherwise
     */
    fun validateSecretSize(secretSize: Int): Boolean {
        return secretSize in 1..secretMaxSize
    }

    /**
     * Returns the number of redundant shares (n - k).
     * 
     * This represents how many shares can be lost while still being able to 
     * reconstruct the secret. A higher redundancy provides more fault tolerance.
     * 
     * @return The number of shares that can be lost (totalShares - threshold)
     */
    val redundancy: Int
        get() = totalShares - threshold

    /**
     * Checks if this is a trivial configuration (k = 1 or k = n).
     * 
     * A configuration is considered trivial if:
     * - k = 1: Any single share can reconstruct the secret (no security)
     * - k = n: All shares are required (no fault tolerance)
     * 
     * @return true if the configuration is trivial, false otherwise
     */
    val isTrivial: Boolean
        get() = threshold == 1 || threshold == totalShares

    companion object {
        const val DEFAULT_SECRET_MAX_SIZE = 1024
        const val DEFAULT_FIELD_SIZE = 256
        const val MAX_SHARES = 128
        const val MAX_SECRET_SIZE = 1024

        /**
         * Creates a simple (k, n) configuration with default settings.
         */
        fun create(threshold: Int, totalShares: Int): SSSConfig {
            return SSSConfig(
                threshold = threshold,
                totalShares = totalShares
            )
        }

        /**
         * Creates a configuration for splitting a secret into n equal parts.
         */
        fun createAllRequired(shares: Int): SSSConfig {
            return SSSConfig(
                threshold = shares,
                totalShares = shares
            )
        }
    }
}