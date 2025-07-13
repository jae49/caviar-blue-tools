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
import cb.core.tools.erasure.math.PolynomialMath
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
        
        // Reconstruct each byte of the secret
        val reconstructedSecret = ByteArray(secretSize)
        for (byteIndex in 0 until secretSize) {
            val points = sharesToUse.map { share ->
                Pair(share.index, share.data[byteIndex].toInt() and 0xFF)
            }
            
            // Use Lagrange interpolation to find f(0)
            val secretByte = interpolateSecretByte(points)
            reconstructedSecret[byteIndex] = secretByte.toByte()
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
     * Interpolates a single byte of the secret using Lagrange interpolation.
     * 
     * Given points (x_i, y_i) representing share indices and values,
     * reconstructs the polynomial and evaluates it at x = 0 to get the secret.
     * 
     * @param points List of (shareIndex, shareValue) pairs
     * @return The interpolated secret byte value
     */
    private fun interpolateSecretByte(points: List<Pair<Int, Int>>): Int {
        // Use Lagrange interpolation to find f(0)
        var result = 0
        
        for (i in points.indices) {
            val (xi, yi) = points[i]
            var numerator = 1
            var denominator = 1
            
            // Calculate Lagrange basis polynomial l_i(0)
            for (j in points.indices) {
                if (i != j) {
                    val xj = points[j].first
                    // l_i(0) = ∏(0 - x_j) / ∏(x_i - x_j) for j ≠ i
                    numerator = GaloisField.multiply(numerator, xj)
                    denominator = GaloisField.multiply(denominator, GaloisField.subtract(xi, xj))
                }
            }
            
            // Calculate y_i * l_i(0)
            val coefficient = GaloisField.divide(numerator, denominator)
            val term = GaloisField.multiply(yi, coefficient)
            result = GaloisField.add(result, term)
        }
        
        return result
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