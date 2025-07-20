package cb.core.tools.erasure

import cb.core.tools.erasure.models.EncodingConfig
import cb.core.tools.erasure.models.ReconstructionResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Assertions.*

class Phase1ValidationTest {
    private val encoder = ReedSolomonEncoder()
    private val decoder = ReedSolomonDecoder()
    
    @Test
    fun `verify issue exists - 8 shards with 5 threshold deleting 0,3,6`() {
        val config = EncodingConfig(dataShards = 5, parityShards = 3)
        val data = "Test data for phase 1 validation".toByteArray()
        
        // Encode
        val allShards = encoder.encode(data, config)
        assertEquals(8, allShards.size)
        
        // Delete shards 0, 3, 6 (keeping 1,2,4,5,7)
        val remainingShards = listOf(1, 2, 4, 5, 7).map { allShards[it] }
        
        // Try to decode - with polynomial algorithm, this is a known problematic case
        when (val result = decoder.decode(remainingShards)) {
            is ReconstructionResult.Success -> {
                // If polynomial algorithm succeeds, that's fine but unexpected
                println("SUCCESS: Polynomial algorithm decoded successfully!")
                assertArrayEquals(data, result.data)
            }
            is ReconstructionResult.Failure -> {
                // This is expected with polynomial algorithm for this specific case
                println("Expected failure with polynomial: ${result.error} - ${result.message ?: "No message"}")
                // Don't fail the test - this demonstrates the limitation
            }
            else -> fail("Unexpected result type")
        }
    }
    
    @Test
    fun `test simple case that should work`() {
        val config = EncodingConfig(dataShards = 3, parityShards = 2)
        val data = "Simple test".toByteArray()
        
        val shards = encoder.encode(data, config)
        
        // Keep all data shards
        val selectedShards = shards.take(3)
        
        when (val result = decoder.decode(selectedShards)) {
            is ReconstructionResult.Success -> {
                assertArrayEquals(data, result.data)
            }
            is ReconstructionResult.Failure -> {
                fail("Should work with all data shards: ${result.error}")
            }
            else -> fail("Unexpected result")
        }
    }
    
    // PHASE 4: Systematic Algorithm Validation
    
    
    @Test
    fun `verify algorithm succeeds for case with shards 0,3,6 missing`() {
        val config = EncodingConfig(
            dataShards = 5, 
            parityShards = 3
        )
        val data = "Test data for algorithm success".toByteArray()
        
        // Encode
        val allShards = encoder.encode(data, config)
        assertEquals(8, allShards.size)
        
        // Delete shards 0, 3, 6 (keeping 1,2,4,5,7)
        val remainingShards = listOf(1, 2, 4, 5, 7).map { allShards[it] }
        
        // Try to decode - this should succeed
        when (val result = decoder.decode(remainingShards)) {
            is ReconstructionResult.Success -> {
                println("SUCCESS!")
                assertArrayEquals(data, result.data)
            }
            is ReconstructionResult.Failure -> {
                fail("Should succeed: ${result.error} - ${result.message}")
            }
            is ReconstructionResult.Partial -> {
                fail("Unexpected partial result")
            }
        }
    }
    
    
    
}