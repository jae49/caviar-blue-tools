package cb.core.tools.sss

import cb.core.tools.erasure.math.GaloisField
import cb.core.tools.erasure.math.PolynomialMath
import cb.core.tools.sss.models.*
import cb.core.tools.sss.validation.ShareValidator

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
            return SSSResult.Failure(
                SSSError.RECONSTRUCTION_FAILED,
                "Reconstructed secret size mismatch: expected ${metadata.secretSize}, got ${secret.size}"
            )
        }
        
        // Validate secret hash
        if (!metadata.validateSecret(secret)) {
            return SSSResult.Failure(
                SSSError.RECONSTRUCTION_FAILED,
                "Reconstructed secret hash does not match expected hash"
            )
        }
        
        return SSSResult.Success(Unit)
    }
}