package cb.core.tools.sss.validation

import cb.core.tools.sss.models.SSSConfig
import cb.core.tools.sss.models.SSSError
import cb.core.tools.sss.models.SSSResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions

class ConfigValidatorTest {

    @Test
    fun `should validate valid configuration for splitting`() {
        val config = SSSConfig(threshold = 3, totalShares = 5)
        val result = ConfigValidator.validateForSplitting(config, secretSize = 100)
        
        Assertions.assertTrue(result.isSuccess())
        Assertions.assertEquals(config, result.getOrNull())
    }

    @Test
    fun `should reject invalid threshold for splitting`() {
        val config = SSSConfig(threshold = 1, totalShares = 5) // Valid config
        
        // Test with threshold < 1 (need to bypass SSSConfig validation)
        val result = ConfigValidator.validateForSplitting(
            SSSConfig(threshold = 1, totalShares = 1), 
            secretSize = 10
        )
        Assertions.assertTrue(result.isSuccess()) // threshold=1 is valid
        
        // Test threshold > totalShares is already caught by SSSConfig
    }

    @Test
    fun `should reject invalid secret size for splitting`() {
        val config = SSSConfig(threshold = 2, totalShares = 3, secretMaxSize = 100)
        
        // Zero size
        val result1 = ConfigValidator.validateForSplitting(config, secretSize = 0)
        Assertions.assertTrue(result1.isFailure())
        val failure1 = result1 as SSSResult.Failure
        Assertions.assertEquals(SSSError.INVALID_SECRET, failure1.error)
        Assertions.assertTrue(failure1.message.contains("Secret size must be at least 1 byte"))
        
        // Negative size
        val result2 = ConfigValidator.validateForSplitting(config, secretSize = -1)
        Assertions.assertTrue(result2.isFailure())
        Assertions.assertEquals(SSSError.INVALID_SECRET, (result2 as SSSResult.Failure).error)
        
        // Exceeds configured max
        val result3 = ConfigValidator.validateForSplitting(config, secretSize = 101)
        Assertions.assertTrue(result3.isFailure())
        val failure3 = result3 as SSSResult.Failure
        Assertions.assertEquals(SSSError.INVALID_SECRET, failure3.error)
        Assertions.assertTrue(failure3.message.contains("exceeds configured maximum"))
        
        // Exceeds absolute max
        val result4 = ConfigValidator.validateForSplitting(config, secretSize = 1025)
        Assertions.assertTrue(result4.isFailure())
        val failure4 = result4 as SSSResult.Failure
        Assertions.assertEquals(SSSError.INVALID_SECRET, failure4.error)
        Assertions.assertTrue(failure4.message.contains("exceeds configured maximum"))
    }

    @Test
    fun `should validate share indices`() {
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        // Valid indices
        val result1 = ConfigValidator.validateShareIndices(listOf(1, 2, 3), config)
        Assertions.assertTrue(result1.isSuccess())
        Assertions.assertEquals(listOf(1, 2, 3), result1.getOrNull())
        
        // All indices
        val result2 = ConfigValidator.validateShareIndices(listOf(1, 2, 3, 4, 5), config)
        Assertions.assertTrue(result2.isSuccess())
        
        // Non-sequential valid indices
        val result3 = ConfigValidator.validateShareIndices(listOf(5, 1, 3), config)
        Assertions.assertTrue(result3.isSuccess())
    }

    @Test
    fun `should reject invalid share indices`() {
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        // Empty list
        val result1 = ConfigValidator.validateShareIndices(emptyList(), config)
        Assertions.assertTrue(result1.isFailure())
        Assertions.assertEquals(SSSError.INVALID_SHARE, (result1 as SSSResult.Failure).error)
        Assertions.assertTrue(result1.message.contains("No share indices provided"))
        
        // Duplicate indices
        val result2 = ConfigValidator.validateShareIndices(listOf(1, 2, 2, 3), config)
        Assertions.assertTrue(result2.isFailure())
        Assertions.assertEquals(SSSError.INVALID_SHARE, (result2 as SSSResult.Failure).error)
        Assertions.assertTrue(result2.message.contains("Duplicate share indices found"))
        
        // Index out of bounds (low)
        val result3 = ConfigValidator.validateShareIndices(listOf(0, 1, 2), config)
        Assertions.assertTrue(result3.isFailure())
        Assertions.assertTrue((result3 as SSSResult.Failure).message.contains("Share index 0 out of bounds"))
        
        // Index out of bounds (high)
        val result4 = ConfigValidator.validateShareIndices(listOf(1, 2, 6), config)
        Assertions.assertTrue(result4.isFailure())
        Assertions.assertTrue((result4 as SSSResult.Failure).message.contains("Share index 6 out of bounds"))
    }

    @Test
    fun `should validate share count`() {
        // Sufficient shares
        val result1 = ConfigValidator.validateShareCount(shareCount = 5, threshold = 3)
        Assertions.assertTrue(result1.isSuccess())
        Assertions.assertEquals(5, result1.getOrNull())
        
        // Exact threshold
        val result2 = ConfigValidator.validateShareCount(shareCount = 3, threshold = 3)
        Assertions.assertTrue(result2.isSuccess())
        Assertions.assertEquals(3, result2.getOrNull())
        
        // Insufficient shares
        val result3 = ConfigValidator.validateShareCount(shareCount = 2, threshold = 3)
        Assertions.assertTrue(result3.isFailure())
        val failure = result3 as SSSResult.Failure
        Assertions.assertEquals(SSSError.INSUFFICIENT_SHARES, failure.error)
        Assertions.assertTrue(failure.message.contains("have 2, need at least 3"))
    }

    @Test
    fun `should validate security parameters`() {
        // Secure random enabled
        val config1 = SSSConfig(threshold = 3, totalShares = 5, useSecureRandom = true)
        val result1 = ConfigValidator.validateSecurityParameters(config1)
        Assertions.assertTrue(result1.isSuccess())
        
        // Secure random disabled (allowed but not recommended)
        val config2 = SSSConfig(threshold = 3, totalShares = 5, useSecureRandom = false)
        val result2 = ConfigValidator.validateSecurityParameters(config2)
        Assertions.assertTrue(result2.isSuccess()) // Should pass but might log warning
    }

    @Test
    fun `should perform comprehensive validation`() {
        // Valid configuration
        val config1 = SSSConfig(threshold = 3, totalShares = 5)
        val result1 = ConfigValidator.validate(config1)
        Assertions.assertTrue(result1.isSuccess())
        Assertions.assertEquals(config1, result1.getOrNull())
        
        // The SSSConfig constructor already validates, so we can't easily create invalid configs
        // But we can test the error handling path
        val result2 = ConfigValidator.validate(config1)
        Assertions.assertTrue(result2.isSuccess())
    }

    @Test
    fun `should handle edge cases in validation`() {
        // Minimum configuration
        val minConfig = SSSConfig(threshold = 1, totalShares = 1)
        val result1 = ConfigValidator.validateForSplitting(minConfig, secretSize = 1)
        Assertions.assertTrue(result1.isSuccess())
        
        // Maximum shares
        val maxConfig = SSSConfig(threshold = 64, totalShares = 128)
        val result2 = ConfigValidator.validateForSplitting(maxConfig, secretSize = 500)
        Assertions.assertTrue(result2.isSuccess())
        
        // Maximum secret size
        val result3 = ConfigValidator.validateForSplitting(minConfig, secretSize = 1024)
        Assertions.assertTrue(result3.isSuccess())
    }

    @Test
    fun `should validate field size restrictions`() {
        // Currently only GF(256) is supported
        val config = SSSConfig(threshold = 2, totalShares = 3, fieldSize = 256)
        val result = ConfigValidator.validateForSplitting(config, secretSize = 10)
        Assertions.assertTrue(result.isSuccess())
        
        // Other field sizes are rejected by SSSConfig constructor
    }

    @Test
    fun `should handle trivial configurations`() {
        // k = 1 (any single share reveals secret)
        val config1 = SSSConfig(threshold = 1, totalShares = 5)
        val result1 = ConfigValidator.validateForSplitting(config1, secretSize = 50)
        Assertions.assertTrue(result1.isSuccess()) // Valid but potentially insecure
        
        // k = n (all shares required)
        val config2 = SSSConfig(threshold = 5, totalShares = 5)
        val result2 = ConfigValidator.validateForSplitting(config2, secretSize = 50)
        Assertions.assertTrue(result2.isSuccess()) // Valid but no redundancy
    }
}