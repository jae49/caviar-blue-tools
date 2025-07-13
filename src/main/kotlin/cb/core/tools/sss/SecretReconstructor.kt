package cb.core.tools.sss

import cb.core.tools.erasure.math.GaloisField
import cb.core.tools.erasure.math.PolynomialMath
import cb.core.tools.sss.models.*

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
        // Validate we have shares
        if (shares.isEmpty()) {
            return SSSResult.Failure(
                SSSError.INSUFFICIENT_SHARES,
                "No shares provided for reconstruction"
            )
        }
        
        // Extract threshold from metadata or infer from shares
        val threshold = metadata?.threshold ?: shares.size
        
        // Validate sufficient shares
        if (shares.size < threshold) {
            return SSSResult.Failure(
                SSSError.INSUFFICIENT_SHARES,
                "Insufficient shares: provided ${shares.size}, required $threshold"
            )
        }
        
        // Validate share indices are unique
        val uniqueIndices = shares.map { it.index }.toSet()
        if (uniqueIndices.size != shares.size) {
            return SSSResult.Failure(
                SSSError.INVALID_SHARE,
                "Duplicate share indices found"
            )
        }
        
        // Validate all shares have the same data length
        val dataLengths = shares.map { it.data.size }.toSet()
        if (dataLengths.size > 1) {
            return SSSResult.Failure(
                SSSError.INCOMPATIBLE_SHARES,
                "Inconsistent share data sizes: $dataLengths"
            )
        }
        
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
        
        // Validate against metadata if provided
        if (metadata != null) {
            val validationResult = validateReconstruction(reconstructedSecret, metadata)
            if (validationResult is SSSResult.Failure) {
                return validationResult
            }
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