/*
 * Copyright 2025 John Engelman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cb.core.tools.erasure

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Assertions.*
import kotlin.random.Random
import cb.core.tools.erasure.models.EncodingConfig
import cb.core.tools.erasure.models.ReconstructionResult
import cb.core.tools.erasure.models.ReconstructionError

@Tag("slow")
class IntegrationTest {

    @Test
    fun testFullErasureRecoveryWithRandomData() {
        // Create 16K of random data
        val originalData = ByteArray(16 * 1024) { Random.nextBytes(1)[0] }
        
        // Configure encoding: 4 data shards + 2 parity shards = 6 total shards
        val config = EncodingConfig(dataShards = 4, parityShards = 2)
        
        // Create encoder and decoder instances
        val encoder = ReedSolomonEncoder()
        val decoder = ReedSolomonDecoder()
        
        // Encode the data into shards
        val allShards = encoder.encode(originalData, config)
        assertEquals(6, allShards.size, "Should have 6 total shards (4 data + 2 parity)")
        
        // Verify all shards were created
        allShards.forEachIndexed { index, shard ->
            assertEquals(index, shard.index, "Shard index should match its position")
            assertTrue(shard.data.isNotEmpty(), "Shard data should not be empty")
        }
        
        // Randomly select 4 shards out of 6 (simulating loss of 2 shards)
        val selectedIndices = (0..5).shuffled(Random).take(4).sorted()
        val selectedShards = selectedIndices.map { allShards[it] }
        
        println("Selected shard indices: $selectedIndices")
        println("Missing shard indices: ${(0..5).filterNot { it in selectedIndices }}")
        
        // Attempt to reconstruct the original data from only 4 shards
        val reconstructionResult = decoder.decode(selectedShards)
        
        // Verify reconstruction was successful
        assertTrue(reconstructionResult is ReconstructionResult.Success, 
            "Reconstruction should succeed with 4 out of 6 shards (need minimum 4)")
        
        val reconstructedData = (reconstructionResult as ReconstructionResult.Success).data
        
        // Verify the reconstructed data matches the original byte-for-byte
        assertArrayEquals(originalData, reconstructedData, 
            "Reconstructed data should exactly match the original data")
        
        println("Successfully reconstructed ${originalData.size} bytes from 4 out of 6 shards")
    }
    
    @Test
    fun testMinimumShardsRecovery() {
        // Additional test: Verify we can recover with exactly the minimum number of shards
        val originalData = ByteArray(8192) { Random.nextBytes(1)[0] }
        val config = EncodingConfig(dataShards = 4, parityShards = 2)
        
        val encoder = ReedSolomonEncoder()
        val decoder = ReedSolomonDecoder()
        
        val allShards = encoder.encode(originalData, config)
        
        // Use exactly 4 shards (the minimum required)
        val minimalShards = allShards.shuffled(Random).take(4)
        
        val result = decoder.decode(minimalShards)
        assertTrue(result is ReconstructionResult.Success, 
            "Should be able to reconstruct with exactly the minimum number of shards")
        
        val reconstructedData = (result as ReconstructionResult.Success).data
        assertArrayEquals(originalData, reconstructedData, 
            "Data reconstructed from minimum shards should match original")
    }
    
    @Test
    fun testInsufficientShardsFailure() {
        // Verify that reconstruction fails when we have fewer than the minimum shards
        val originalData = ByteArray(4096) { Random.nextBytes(1)[0] }
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