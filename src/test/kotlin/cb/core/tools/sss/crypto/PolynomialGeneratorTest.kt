package cb.core.tools.sss.crypto

import cb.core.tools.sss.models.SSSConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PolynomialGeneratorTest {
    
    private val generator = PolynomialGenerator()
    
    @Test
    fun `generateCoefficients should create array of correct size`() {
        val config = SSSConfig(threshold = 3, totalShares = 5)
        val secretByte: Byte = 42
        
        val coefficients = generator.generateCoefficients(secretByte, config)
        
        assertEquals(3, coefficients.size)
    }
    
    @Test
    fun `generateCoefficients should set secret as constant term`() {
        val config = SSSConfig(threshold = 3, totalShares = 5)
        val secretByte: Byte = 123
        
        val coefficients = generator.generateCoefficients(secretByte, config)
        
        assertEquals(secretByte.toInt() and 0xFF, coefficients[0])
    }
    
    @Test
    fun `generateCoefficients should create valid field elements`() {
        val config = SSSConfig(threshold = 5, totalShares = 10)
        val secretByte: Byte = -50 // Test negative byte
        
        val coefficients = generator.generateCoefficients(secretByte, config)
        
        coefficients.forEach { coeff ->
            assertTrue(coeff >= 0)
            assertTrue(coeff <= 255)
        }
    }
    
    @Test
    fun `generateCoefficients should handle edge byte values`() {
        val config = SSSConfig(threshold = 2, totalShares = 3)
        
        // Test minimum byte value
        val minCoeffs = generator.generateCoefficients(Byte.MIN_VALUE, config)
        assertEquals(Byte.MIN_VALUE.toInt() and 0xFF, minCoeffs[0])
        
        // Test maximum byte value
        val maxCoeffs = generator.generateCoefficients(Byte.MAX_VALUE, config)
        assertEquals(Byte.MAX_VALUE.toInt() and 0xFF, maxCoeffs[0])
    }
    
    @Test
    fun `generateCoefficientsForSecret should create coefficients for each byte`() {
        val config = SSSConfig(threshold = 3, totalShares = 5)
        val secret = "test".toByteArray()
        
        val allCoefficients = generator.generateCoefficientsForSecret(secret, config)
        
        assertEquals(secret.size, allCoefficients.size)
        allCoefficients.forEach { coeffs ->
            assertEquals(3, coeffs.size)
        }
    }
    
    @Test
    fun `generateCoefficientsForSecret should preserve secret bytes`() {
        val config = SSSConfig(threshold = 2, totalShares = 4)
        val secret = byteArrayOf(10, 20, 30, 40)
        
        val allCoefficients = generator.generateCoefficientsForSecret(secret, config)
        
        assertEquals(10, allCoefficients[0][0])
        assertEquals(20, allCoefficients[1][0])
        assertEquals(30, allCoefficients[2][0])
        assertEquals(40, allCoefficients[3][0])
    }
    
    @Test
    fun `validateCoefficients should accept valid coefficients`() {
        val config = SSSConfig(threshold = 3, totalShares = 5)
        val validCoeffs = intArrayOf(42, 100, 255)
        
        val isValid = generator.validateCoefficients(validCoeffs, config)
        
        assertTrue(isValid)
    }
    
    @Test
    fun `validateCoefficients should reject wrong size`() {
        val config = SSSConfig(threshold = 3, totalShares = 5)
        val wrongSizeCoeffs = intArrayOf(42, 100) // Too few
        
        val isValid = generator.validateCoefficients(wrongSizeCoeffs, config)
        
        assertFalse(isValid)
    }
    
    @Test
    fun `validateCoefficients should reject invalid field elements`() {
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        // Test negative value
        val negativeCoeffs = intArrayOf(42, -1, 100)
        assertFalse(generator.validateCoefficients(negativeCoeffs, config))
        
        // Test value > 255
        val largeCoeffs = intArrayOf(42, 256, 100)
        assertFalse(generator.validateCoefficients(largeCoeffs, config))
    }
    
    @Test
    fun `validateCoefficients should reject all-zero higher coefficients`() {
        val config = SSSConfig(threshold = 3, totalShares = 5)
        val allZeroHigher = intArrayOf(42, 0, 0) // Only constant term non-zero
        
        val isValid = generator.validateCoefficients(allZeroHigher, config)
        
        assertFalse(isValid)
    }
    
    @Test
    fun `validateCoefficients should accept threshold = 1`() {
        val config = SSSConfig(threshold = 1, totalShares = 5)
        val singleCoeff = intArrayOf(42)
        
        val isValid = generator.validateCoefficients(singleCoeff, config)
        
        assertTrue(isValid)
    }
    
    @Test
    fun `generated coefficients should pass validation`() {
        val configs = listOf(
            SSSConfig(threshold = 1, totalShares = 3),
            SSSConfig(threshold = 3, totalShares = 5),
            SSSConfig(threshold = 10, totalShares = 20)
        )
        
        configs.forEach { config ->
            repeat(10) { // Test multiple times due to randomness
                val coeffs = generator.generateCoefficients(42, config)
                val isValid = generator.validateCoefficients(coeffs, config)
                assertTrue(isValid)
            }
        }
    }
    
    @Test
    fun `generateCoefficients should produce different results on repeated calls`() {
        val config = SSSConfig(threshold = 3, totalShares = 5)
        val secretByte: Byte = 42
        
        val coeffs1 = generator.generateCoefficients(secretByte, config)
        val coeffs2 = generator.generateCoefficients(secretByte, config)
        
        // Constant terms should be the same
        assertEquals(coeffs1[0], coeffs2[0])
        
        // But higher coefficients should differ (with very high probability)
        val higherDiffer = (1 until coeffs1.size).any { i ->
            coeffs1[i] != coeffs2[i]
        }
        assertTrue(higherDiffer)
    }
}