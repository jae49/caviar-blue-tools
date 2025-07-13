package cb.core.tools.sss.crypto

import cb.core.tools.sss.models.SSSConfig

/**
 * Generates cryptographically secure polynomials for Shamir Secret Sharing.
 * 
 * Creates random polynomials of degree k-1 where the constant term is the secret,
 * ensuring information-theoretic security of the sharing scheme.
 */
class PolynomialGenerator(
    private val randomGenerator: SecureRandomGenerator = SecureRandomGenerator()
) {
    
    /**
     * Generates polynomial coefficients for a single byte of the secret.
     * 
     * The polynomial has the form: f(x) = secret + a₁x + a₂x² + ... + aₖ₋₁xᵏ⁻¹
     * where all coefficients (except the secret) are randomly generated.
     * 
     * @param secretByte The secret value to be the constant term
     * @param config The SSS configuration specifying the threshold
     * @return Array of polynomial coefficients [secret, a₁, a₂, ..., aₖ₋₁]
     */
    fun generateCoefficients(secretByte: Byte, config: SSSConfig): IntArray {
        val coefficients = IntArray(config.threshold)
        
        // The constant term is the secret
        coefficients[0] = secretByte.toInt() and 0xFF
        
        // Generate random coefficients for terms x¹ through xᵏ⁻¹
        for (i in 1 until config.threshold) {
            coefficients[i] = randomGenerator.nextFieldElement()
        }
        
        return coefficients
    }
    
    /**
     * Generates polynomial coefficients for multiple bytes of the secret.
     * 
     * Creates a separate polynomial for each byte of the secret, all with
     * the same degree but independent random coefficients.
     * 
     * @param secretBytes The secret bytes to encode
     * @param config The SSS configuration
     * @return List of coefficient arrays, one per secret byte
     */
    fun generateCoefficientsForSecret(secretBytes: ByteArray, config: SSSConfig): List<IntArray> {
        return secretBytes.map { byte ->
            generateCoefficients(byte, config)
        }
    }
    
    /**
     * Validates that generated coefficients meet security requirements.
     * 
     * Ensures that:
     * - All coefficients are valid field elements
     * - The polynomial has the correct degree
     * - Higher-degree coefficients are not all zero (which would reduce security)
     * 
     * @param coefficients The polynomial coefficients to validate
     * @param config The SSS configuration
     * @return true if coefficients are valid, false otherwise
     */
    fun validateCoefficients(coefficients: IntArray, config: SSSConfig): Boolean {
        // Check correct number of coefficients
        if (coefficients.size != config.threshold) {
            return false
        }
        
        // Check all coefficients are valid field elements
        if (coefficients.any { it < 0 || it > 255 }) {
            return false
        }
        
        // For threshold > 1, ensure not all higher coefficients are zero
        // (This would effectively reduce the threshold)
        if (config.threshold > 1) {
            val hasNonZeroHigherCoefficient = (1 until coefficients.size).any { coefficients[it] != 0 }
            if (!hasNonZeroHigherCoefficient) {
                return false
            }
        }
        
        return true
    }
}