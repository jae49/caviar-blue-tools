package cb.core.tools.sss

import cb.core.tools.sss.models.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ShamirSecretSharingTest {
    
    private val sss = ShamirSecretSharing()
    
    @Test
    fun `split and reconstruct should work end-to-end`() {
        val secret = "Hello, Shamir Secret Sharing!".toByteArray()
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares.take(3)
        val reconstructResult = sss.reconstruct(shares, splitResult.value.metadata)
        
        assertTrue(reconstructResult is SSSResult.Success)
        assertArrayEquals(secret, (reconstructResult as SSSResult.Success).value)
    }
    
    @Test
    fun `split with string should work correctly`() {
        val secretString = "String secret test"
        val config = SSSConfig(threshold = 2, totalShares = 4)
        
        val splitResult = sss.split(secretString, config)
        
        assertTrue(splitResult is SSSResult.Success)
        val shares = (splitResult as SSSResult.Success).value.shares
        assertEquals(4, shares.size)
    }
    
    @Test
    fun `reconstructString should return original string`() {
        val secretString = "UTF-8 string: Hello 世界 🌍"
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        val splitResult = sss.split(secretString, config)
        val shares = splitResult.getOrNull()?.shares?.take(3) ?: emptyList()
        
        val reconstructResult = sss.reconstructString(shares)
        
        assertTrue(reconstructResult is SSSResult.Success)
        assertEquals(secretString, (reconstructResult as SSSResult.Success).value)
    }
    
    @Test
    fun `validateShares should accept valid shares`() {
        val secret = "validation test".toByteArray()
        val config = SSSConfig(threshold = 2, totalShares = 3)
        
        val splitResult = sss.split(secret, config)
        val shares = splitResult.getOrNull()?.shares ?: emptyList()
        val metadata = splitResult.getOrNull()?.metadata
        
        val validationResult = sss.validateShares(shares, metadata)
        
        assertTrue(validationResult is SSSResult.Success)
    }
    
    @Test
    fun `validateShares should reject empty shares`() {
        val validationResult = sss.validateShares(emptyList())
        
        assertTrue(validationResult is SSSResult.Failure)
        assertEquals(SSSError.INSUFFICIENT_SHARES, (validationResult as SSSResult.Failure).error)
    }
    
    @Test
    fun `validateShares should reject duplicate indices`() {
        val metadata = ShareMetadata(
            threshold = 2,
            totalShares = 3,
            secretSize = 3,
            secretHash = ByteArray(32) { 0 }
        )
        val share1 = SecretShare(index = 1, data = byteArrayOf(1, 2, 3), metadata = metadata)
        val share2 = SecretShare(index = 1, data = byteArrayOf(4, 5, 6), metadata = metadata)
        
        val validationResult = sss.validateShares(listOf(share1, share2))
        
        assertTrue(validationResult is SSSResult.Failure)
        assertEquals(SSSError.INVALID_SHARE, (validationResult as SSSResult.Failure).error)
    }
    
    @Test
    fun `validateShares should reject inconsistent sizes`() {
        val metadata1 = ShareMetadata(
            threshold = 2,
            totalShares = 3,
            secretSize = 3,
            secretHash = ByteArray(32) { 0 }
        )
        val metadata2 = ShareMetadata(
            threshold = 2,
            totalShares = 3,
            secretSize = 2,
            secretHash = ByteArray(32) { 0 }
        )
        val share1 = SecretShare(index = 1, data = byteArrayOf(1, 2, 3), metadata = metadata1)
        val share2 = SecretShare(index = 2, data = byteArrayOf(4, 5), metadata = metadata2)
        
        val validationResult = sss.validateShares(listOf(share1, share2))
        
        assertTrue(validationResult is SSSResult.Failure)
        assertEquals(SSSError.INCOMPATIBLE_SHARES, (validationResult as SSSResult.Failure).error)
    }
    
    @Test
    fun `validateShares should reject insufficient shares with metadata`() {
        val metadata = ShareMetadata(
            threshold = 3,
            totalShares = 5,
            secretSize = 10,
            secretHash = ByteArray(32) { 0 }
        )
        val share1 = SecretShare(index = 1, data = ByteArray(10), metadata = metadata)
        val share2 = SecretShare(index = 2, data = ByteArray(10), metadata = metadata)
        
        val validationResult = sss.validateShares(listOf(share1, share2), metadata)
        
        assertTrue(validationResult is SSSResult.Failure)
        assertEquals(SSSError.INSUFFICIENT_SHARES, (validationResult as SSSResult.Failure).error)
    }
    
    @Test
    fun `SplitResult toShareMap should create correct mapping`() {
        val secret = "map test".toByteArray()
        val config = SSSConfig(threshold = 2, totalShares = 3)
        
        val splitResult = sss.split(secret, config)
        val result = splitResult.getOrNull() ?: return
        
        val shareMap = result.toShareMap()
        
        assertEquals(3, shareMap.size)
        assertNotNull(shareMap[1])
        assertNotNull(shareMap[2])
        assertNotNull(shareMap[3])
        assertEquals(1, shareMap[1]?.index)
    }
    
    @Test
    fun `SplitResult getSharesByIndices should return correct shares`() {
        val secret = "indices test".toByteArray()
        val config = SSSConfig(threshold = 2, totalShares = 5)
        
        val splitResult = sss.split(secret, config)
        val result = splitResult.getOrNull() ?: return
        
        val selectedShares = result.getSharesByIndices(listOf(1, 3, 5))
        
        assertEquals(3, selectedShares.size)
        assertEquals(listOf(1, 3, 5), selectedShares.map { it.index })
    }
    
    @Test
    fun `createConfig companion function should create valid config`() {
        val config = ShamirSecretSharing.createConfig(k = 3, n = 5)
        
        assertEquals(3, config.threshold)
        assertEquals(5, config.totalShares)
        assertEquals(1024, config.secretMaxSize)
    }
    
    @Test
    fun `reconstruct without metadata should work`() {
        val secret = "no metadata".toByteArray()
        val config = SSSConfig(threshold = 2, totalShares = 4)
        
        val splitResult = sss.split(secret, config)
        val shares = splitResult.getOrNull()?.shares?.take(2) ?: emptyList()
        
        // Reconstruct without metadata
        val reconstructResult = sss.reconstruct(shares)
        
        assertTrue(reconstructResult is SSSResult.Success)
        assertArrayEquals(secret, (reconstructResult as SSSResult.Success).value)
    }
    
    @Test
    fun `should handle all possible byte values`() {
        val secret = ByteArray(256) { it.toByte() }
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        val splitResult = sss.split(secret, config)
        val shares = splitResult.getOrNull()?.shares?.take(3) ?: emptyList()
        
        val reconstructResult = sss.reconstruct(shares)
        
        assertTrue(reconstructResult is SSSResult.Success)
        assertArrayEquals(secret, (reconstructResult as SSSResult.Success).value)
    }
    
    @Test
    fun `should handle unicode strings correctly`() {
        val secretString = "Hello 世界! 🚀 Émojis and ñ special chars"
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        val splitResult = sss.split(secretString, config)
        val shares = splitResult.getOrNull()?.shares?.take(3) ?: emptyList()
        
        val reconstructResult = sss.reconstructString(shares)
        
        assertTrue(reconstructResult is SSSResult.Success)
        assertEquals(secretString, (reconstructResult as SSSResult.Success).value)
    }
}