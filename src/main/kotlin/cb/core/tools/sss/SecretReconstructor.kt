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

package cb.core.tools.sss

import cb.core.tools.erasure.math.GaloisField
import cb.core.tools.sss.models.*
import cb.core.tools.sss.validation.ShareValidator
import cb.core.tools.sss.security.SecureErrorHandler

/**
 * Core implementation for reconstructing secrets from shares using Shamir Secret Sharing.
 * 
 * This class handles the mathematical operations of recovering the original secret
 * from a threshold number of shares using Lagrange interpolation.
 */
class SecretReconstructor {
    
    /**
     * Reconstructs a secret from the provided shares.
     * 
     * @param shares The available secret shares
     * @param metadata Optional metadata for validation
     * @return Success result with reconstructed secret, or Failure with error details
     */
    fun reconstruct(
        shares: List<SecretShare>,
        metadata: ShareMetadata? = null
    ): SSSResult<ByteArray> {
        // Validate shares using ShareValidator
        val validationResult = ShareValidator.validateSharesForReconstruction(shares)
        if (validationResult is SSSResult.Failure) {
            // Map to secure error messages based on error type
            val errorCategory = when {
                validationResult.message.contains("hash", ignoreCase = true) -> 
                    SecureErrorHandler.ErrorCategory.INVALID_SHARE_FORMAT
                validationResult.message.contains("insufficient", ignoreCase = true) -> 
                    SecureErrorHandler.ErrorCategory.INSUFFICIENT_SHARES
                validationResult.message.contains("mismatch", ignoreCase = true) -> 
                    SecureErrorHandler.ErrorCategory.INCOMPATIBLE_SHARES
                else -> 
                    SecureErrorHandler.ErrorCategory.VALIDATION_FAILED
            }
            
            val errorType = when (errorCategory) {
                SecureErrorHandler.ErrorCategory.INVALID_SHARE_FORMAT -> SSSError.INVALID_SHARE
                SecureErrorHandler.ErrorCategory.INSUFFICIENT_SHARES -> SSSError.INSUFFICIENT_SHARES
                SecureErrorHandler.ErrorCategory.INCOMPATIBLE_SHARES -> SSSError.INCOMPATIBLE_SHARES
                else -> SSSError.INVALID_SHARE
            }
            
            val secureMessage = SecureErrorHandler.sanitizeError(
                IllegalArgumentException(validationResult.message), 
                errorCategory
            )
            return SSSResult.Failure(errorType, secureMessage)
        }
        
        // Extract threshold from first share's metadata
        val threshold = shares.first().metadata.threshold
        
        // Use only the first 'threshold' shares for reconstruction
        val sharesToUse = shares.take(threshold)
        val secretSize = sharesToUse.first().data.size

        // The Lagrange basis weights l_i(0) depend only on the share indices, which
        // are identical for every byte of the secret. Compute them once instead of
        // re-deriving an O(k^2) basis for each of `secretSize` bytes.
        val xs = IntArray(sharesToUse.size) { sharesToUse[it].index }
        val weights = lagrangeWeightsAtZero(xs)

        // Reconstruct each byte of the secret as f(0) = sum_i weight_i * y_i.
        val reconstructedSecret = ByteArray(secretSize)
        for (byteIndex in 0 until secretSize) {
            var acc = 0
            for (i in sharesToUse.indices) {
                val yi = sharesToUse[i].data[byteIndex].toInt() and 0xFF
                acc = GaloisField.add(acc, GaloisField.multiply(weights[i], yi))
            }
            reconstructedSecret[byteIndex] = acc.toByte()
        }
        
        // Validate against share metadata
        val shareMetadata = shares.first().metadata
        val reconstructionResult = validateReconstruction(reconstructedSecret, shareMetadata)
        if (reconstructionResult is SSSResult.Failure) {
            return reconstructionResult
        }
        
        return SSSResult.Success(reconstructedSecret)
    }
    
    /**
     * Computes the Lagrange basis weights `l_i(0)` for the given share indices.
     *
     * `l_i(0) = ∏_{j≠i} (0 - x_j) / ∏_{j≠i} (x_i - x_j)`. In GF(2^m) negation is a
     * no-op (`-x == x`), so the numerator is simply `∏_{j≠i} x_j`. These weights
     * depend only on which shares are present, so they are reused across every
     * byte of the secret.
     *
     * @param xs The share indices (x-coordinates)
     * @return The per-share weights, aligned with `xs`
     */
    private fun lagrangeWeightsAtZero(xs: IntArray): IntArray {
        val n = xs.size
        return IntArray(n) { i ->
            var numerator = 1
            var denominator = 1
            for (j in 0 until n) {
                if (i != j) {
                    numerator = GaloisField.multiply(numerator, xs[j])
                    denominator = GaloisField.multiply(denominator, GaloisField.subtract(xs[i], xs[j]))
                }
            }
            GaloisField.divide(numerator, denominator)
        }
    }
    
    /**
     * Validates the reconstructed secret against provided metadata.
     * 
     * @param secret The reconstructed secret
     * @param metadata The metadata to validate against
     * @return Success if valid, Failure with error details otherwise
     */
    private fun validateReconstruction(
        secret: ByteArray,
        metadata: ShareMetadata
    ): SSSResult<Unit> {
        // Validate secret size
        if (secret.size != metadata.secretSize) {
            val secureMessage = SecureErrorHandler.sanitizeError(
                IllegalArgumentException("Size mismatch"),
                SecureErrorHandler.ErrorCategory.VALIDATION_FAILED
            )
            return SSSResult.Failure(SSSError.RECONSTRUCTION_FAILED, secureMessage)
        }
        
        // Validate secret hash
        if (!metadata.validateSecret(secret)) {
            val secureMessage = SecureErrorHandler.sanitizeError(
                IllegalArgumentException("Hash mismatch"),
                SecureErrorHandler.ErrorCategory.VALIDATION_FAILED
            )
            return SSSResult.Failure(SSSError.RECONSTRUCTION_FAILED, secureMessage)
        }
        
        return SSSResult.Success(Unit)
    }
}