package cb.core.tools.sss

import cb.core.tools.erasure.math.GaloisField
import cb.core.tools.sss.crypto.PolynomialGenerator
import cb.core.tools.sss.crypto.SecureRandomGenerator
import cb.core.tools.sss.models.*
import cb.core.tools.sss.validation.ConfigValidator
import java.time.Instant

/**
 * Core implementation for splitting secrets into shares using Shamir Secret Sharing.
 * 
 * This class handles the mathematical operations of creating polynomial shares
 * from a secret, leveraging the existing GaloisField infrastructure for
 * field arithmetic operations.
 */
class SecretSplitter(
    private val polynomialGenerator: PolynomialGenerator = PolynomialGenerator(),
    private val configValidator: ConfigValidator = ConfigValidator
) {
    
    /**
     * Splits a secret into shares according to the provided configuration.
     * 
     * @param secret The secret bytes to split
     * @param config The SSS configuration specifying threshold and total shares
     * @return Success result with shares and metadata, or Failure with error details
     */
    fun split(secret: ByteArray, config: SSSConfig): SSSResult<SplitResult> {
        // Validate configuration
        val validationResult = configValidator.validate(config)
        if (validationResult is SSSResult.Failure) {
            return SSSResult.Failure(validationResult.error, validationResult.message, validationResult.cause)
        }
        
        // Validate secret size
        if (secret.isEmpty()) {
            return SSSResult.Failure(
                SSSError.INVALID_SECRET,
                "Secret cannot be empty"
            )
        }
        if (secret.size > config.secretMaxSize) {
            return SSSResult.Failure(
                SSSError.INVALID_SECRET,
                "Secret size (${secret.size}) exceeds maximum allowed (${config.secretMaxSize})"
            )
        }
        
        // Generate polynomials for each byte of the secret
        val polynomials = polynomialGenerator.generateCoefficientsForSecret(secret, config)
        
        // Create metadata for validation
        val metadata = ShareMetadata.create(secret, config)
        
        // Create shares by evaluating polynomials at x = 1, 2, ..., n
        val shares = createShares(polynomials, config, metadata)
        
        return SSSResult.Success(SplitResult(shares, metadata))
    }
    
    /**
     * Creates shares by evaluating polynomials at different points.
     * 
     * For each share index i (from 1 to n), evaluates all byte polynomials
     * at x = i to create the share's data.
     * 
     * @param polynomials List of coefficient arrays, one per secret byte
     * @param config The SSS configuration
     * @return List of created shares
     */
    private fun createShares(
        polynomials: List<IntArray>,
        config: SSSConfig,
        metadata: ShareMetadata
    ): List<SecretShare> {
        val shares = mutableListOf<SecretShare>()
        
        // Create shares for indices 1 through totalShares
        for (shareIndex in 1..config.totalShares) {
            val shareData = ByteArray(polynomials.size)
            
            // Evaluate each polynomial at x = shareIndex
            for ((byteIndex, coefficients) in polynomials.withIndex()) {
                val value = GaloisField.evaluatePolynomial(coefficients, shareIndex)
                shareData[byteIndex] = value.toByte()
            }
            
            shares.add(SecretShare(
                index = shareIndex,
                data = shareData,
                metadata = metadata
            ))
        }
        
        return shares
    }
    
    /**
     * Result of a successful split operation.
     * 
     * @property shares The generated secret shares
     * @property metadata Metadata for share validation and reconstruction
     */
    data class SplitResult(
        val shares: List<SecretShare>,
        val metadata: ShareMetadata
    )
}