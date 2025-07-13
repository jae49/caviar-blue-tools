package cb.core.tools.sss

import cb.core.tools.sss.models.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SecretSplitterTest {
    
    private val splitter = SecretSplitter()
    
    @Test
    fun `split should create correct number of shares`() {
        val secret = "test secret".toByteArray()
        val config = SSSConfig(threshold = 2, totalShares = 5)
        
        val result = splitter.split(secret, config)
        
        assertTrue(result is SSSResult.Success)
        val splitResult = (result as SSSResult.Success).value
        assertEquals(5, splitResult.shares.size)
        assertEquals(2, splitResult.metadata.threshold)
        assertEquals(5, splitResult.metadata.totalShares)
    }
    
    @Test
    fun `split should assign correct indices to shares`() {
        val secret = "test".toByteArray()
        val config = SSSConfig(threshold = 2, totalShares = 3)
        
        val result = splitter.split(secret, config)
        
        val shares = result.getOrNull()?.shares ?: emptyList()
        assertEquals(listOf(1, 2, 3), shares.map { it.index })
    }
    
    @Test
    fun `split should create shares with same length as secret`() {
        val secret = "variable length secret".toByteArray()
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        val result = splitter.split(secret, config)
        
        val shares = result.getOrNull()?.shares ?: emptyList()
        shares.forEach { share ->
            assertEquals(secret.size, share.data.size)
        }
    }
    
    @Test
    fun `split should create different shares for same secret`() {
        val secret = "test".toByteArray()
        val config = SSSConfig(threshold = 2, totalShares = 3)
        
        val result1 = splitter.split(secret, config)
        val result2 = splitter.split(secret, config)
        
        val shares1 = result1.getOrNull()?.shares ?: emptyList()
        val shares2 = result2.getOrNull()?.shares ?: emptyList()
        
        // Due to random polynomial generation, shares should be different
        assertTrue(shares1.zip(shares2).any { (s1, s2) ->
            !s1.data.contentEquals(s2.data)
        })
    }
    
    @Test
    fun `split should handle single-byte secret`() {
        val secret = byteArrayOf(42)
        val config = SSSConfig(threshold = 2, totalShares = 3)
        
        val result = splitter.split(secret, config)
        
        assertTrue(result is SSSResult.Success)
        val shares = (result as SSSResult.Success).value.shares
        assertEquals(3, shares.size)
        shares.forEach { assertEquals(1, it.data.size) }
    }
    
    @Test
    fun `split should handle maximum size secret`() {
        val secret = ByteArray(1024) { it.toByte() }
        val config = SSSConfig(threshold = 5, totalShares = 10)
        
        val result = splitter.split(secret, config)
        
        assertTrue(result is SSSResult.Success)
        val shares = (result as SSSResult.Success).value.shares
        shares.forEach { assertEquals(1024, it.data.size) }
    }
    
    @Test
    fun `split should fail with empty secret`() {
        val secret = byteArrayOf()
        val config = SSSConfig(threshold = 2, totalShares = 3)
        
        val result = splitter.split(secret, config)
        
        assertTrue(result is SSSResult.Failure)
        assertEquals(SSSError.INVALID_SECRET, (result as SSSResult.Failure).error)
    }
    
    @Test
    fun `split should fail with oversized secret`() {
        val secret = ByteArray(1025) { 0 }
        val config = SSSConfig(threshold = 2, totalShares = 3)
        
        val result = splitter.split(secret, config)
        
        assertTrue(result is SSSResult.Failure)
        assertEquals(SSSError.INVALID_SECRET, (result as SSSResult.Failure).error)
    }
    
    @Test
    fun `split should fail with invalid config`() {
        val secret = "test".toByteArray()
        
        // Test that invalid config throws exception during construction
        val exception = assertThrows(IllegalArgumentException::class.java) {
            SSSConfig(threshold = 5, totalShares = 3) // threshold > totalShares
        }
        
        assertEquals("Threshold (5) cannot exceed total shares (3)", exception.message)
    }
    
    @Test
    fun `split should create valid metadata`() {
        val secret = "metadata test".toByteArray()
        val config = SSSConfig(threshold = 3, totalShares = 7)
        
        val result = splitter.split(secret, config)
        
        val metadata = result.getOrNull()?.metadata
        assertNotNull(metadata)
        assertEquals(secret.size, metadata?.secretSize)
        assertEquals(3, metadata?.threshold)
        assertEquals(7, metadata?.totalShares)
        assertArrayEquals(ShareMetadata.computeSecretHash(secret), metadata?.secretHash)
    }
    
    @Test
    fun `split should handle threshold = 1`() {
        val secret = "minimum threshold".toByteArray()
        val config = SSSConfig(threshold = 1, totalShares = 5)
        
        val result = splitter.split(secret, config)
        
        assertTrue(result is SSSResult.Success)
        // With threshold = 1, each share should contain the secret
        val shares = (result as SSSResult.Success).value.shares
        shares.forEach { share ->
            assertArrayEquals(secret, share.data)
        }
    }
    
    @Test
    fun `split should handle threshold = totalShares`() {
        val secret = "all shares needed".toByteArray()
        val config = SSSConfig(threshold = 4, totalShares = 4)
        
        val result = splitter.split(secret, config)
        
        assertTrue(result is SSSResult.Success)
        val shares = (result as SSSResult.Success).value.shares
        assertEquals(4, shares.size)
    }
}