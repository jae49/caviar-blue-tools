package cb.core.tools.erasure

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.random.Random
import cb.core.tools.erasure.models.EncodingConfig
import cb.core.tools.erasure.models.ReconstructionResult
import cb.core.tools.erasure.models.ReconstructionError

class FastIntegrationTest {

    @Test
    fun testBasicErasureRecovery() {
        // Create 256 bytes of random data
        val originalData = ByteArray(256) { Random.nextBytes(1)[0] }
        
        // Configure encoding: 4 data shards + 2 parity shards = 6 total shards
        val config = EncodingConfig(dataShards = 4, parityShards = 2)
        
        // Create encoder and decoder instances
        val encoder = ReedSolomonEncoder()
        val decoder = ReedSolomonDecoder()
        
        // Encode the data into shards
        val allShards = encoder.encode(originalData, config)
        assertEquals(6, allShards.size, "Should have 6 total shards (4 data + 2 parity)")
        
        // Test 1: Use all data shards (no reconstruction needed)
        val dataShards = allShards.take(4)
        val result1 = decoder.decode(dataShards)
        assertTrue(result1 is ReconstructionResult.Success)
        assertArrayEquals(originalData, (result1 as ReconstructionResult.Success).data)
        
        // Test 2: Use minimum shards with one erasure
        val minShards = listOf(allShards[0], allShards[1], allShards[2], allShards[4]) // Skip shard 3
        val result2 = decoder.decode(minShards)
        assertTrue(result2 is ReconstructionResult.Success)
        assertArrayEquals(originalData, (result2 as ReconstructionResult.Success).data)
        
        // Test 3: Use minimum shards with two erasures
        val minShards2 = listOf(allShards[0], allShards[1], allShards[4], allShards[5]) // Skip shards 2,3
        val result3 = decoder.decode(minShards2)
        assertTrue(result3 is ReconstructionResult.Success)
        assertArrayEquals(originalData, (result3 as ReconstructionResult.Success).data)
    }
    
    @Test
    fun testSmallDataSizes() {
        val encoder = ReedSolomonEncoder()
        val decoder = ReedSolomonDecoder()
        val config = EncodingConfig(dataShards = 2, parityShards = 1)
        
        // Test various small sizes
        val sizes = listOf(1, 10, 100, 512)
        
        for (size in sizes) {
            val data = ByteArray(size) { (it % 256).toByte() }
            val shards = encoder.encode(data, config)
            
            // Test with minimum shards
            val minShards = shards.take(2)
            val result = decoder.decode(minShards)
            
            assertTrue(result is ReconstructionResult.Success, 
                "Failed to reconstruct data of size $size")
            assertArrayEquals(data, (result as ReconstructionResult.Success).data,
                "Data mismatch for size $size")
        }
    }
    
    @Test
    fun testInsufficientShardsFailureFast() {
        val originalData = ByteArray(100) { Random.nextBytes(1)[0] }
        val config = EncodingConfig(dataShards = 4, parityShards = 2)
        
        val encoder = ReedSolomonEncoder()
        val decoder = ReedSolomonDecoder()
        
        val allShards = encoder.encode(originalData, config)
        
        // Use only 3 shards (less than the minimum required 4)
        val insufficientShards = allShards.take(3)
        
        val result = decoder.decode(insufficientShards)
        assertTrue(result is ReconstructionResult.Failure, 
            "Reconstruction should fail with insufficient shards")
        
        val failure = result as ReconstructionResult.Failure
        assertEquals(ReconstructionError.INSUFFICIENT_SHARDS, failure.error,
            "Should report insufficient shards error")
    }
}