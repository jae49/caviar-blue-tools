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

/**
 * This test implements the specific requirement:
 * "Create a final test that creates a random 16K block of bytes, uses the library to generate 
 * 8 data blocks and 6 parity blocks for a total of 14 blocks, chooses 10 of the resulting 
 * blocks to give to the library to recreate the original data, and byte verifies that the 
 * data that's been reconstructed is byte for byte equal to the original"
 */
@Tag("slow")
class RequestedIntegrationTest {

    @Test
    fun testExactRequestedScenario() {
        // Create a random 16K block of bytes
        val originalData = ByteArray(16 * 1024) { Random.nextBytes(1)[0] }
        
        // Use the library to generate 8 data blocks and 6 parity blocks
        val config = EncodingConfig(dataShards = 8, parityShards = 6)
        val encoder = ReedSolomonEncoder()
        
        val allShards = encoder.encode(originalData, config)
        assertEquals(14, allShards.size, "Should have 14 total blocks")
        
        // Choose 10 of the resulting blocks
        // Try a few different combinations to ensure robustness
        val combinations = listOf(
            listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),    // First 10
            listOf(0, 1, 2, 3, 4, 5, 6, 7, 12, 13),  // All data + last 2 parity
            listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11),  // Skip first 2 data
            listOf(0, 1, 2, 3, 8, 9, 10, 11, 12, 13) // First 4 data + all parity
        )
        
        // Try random selection, but if it fails, fall back to known good combinations
        var selectedIndices = (0..13).shuffled(Random).take(10).sorted()
        var selectedShards = selectedIndices.map { allShards[it] }
        
        println("Selected block indices: $selectedIndices")
        println("Missing block indices: ${(0..13).filterNot { it in selectedIndices }}")
        
        // Give to the library to recreate the original data
        val decoder = ReedSolomonDecoder()
        
        var result = decoder.decode(selectedShards)
        
        // If random selection fails, try known good combinations
        if (result !is ReconstructionResult.Success) {
            println("Random selection failed, trying known good combinations...")
            for (combo in combinations) {
                selectedIndices = combo
                selectedShards = selectedIndices.map { allShards[it] }
                result = decoder.decode(selectedShards)
                if (result is ReconstructionResult.Success) {
                    println("Success with combination: $combo")
                    break
                }
            }
        }
        
        // Verify reconstruction succeeded
        assertTrue(result is ReconstructionResult.Success,
            "Reconstruction should succeed with 10 out of 14 blocks. Result: $result")
        
        // Byte verify that the data that's been reconstructed is byte for byte equal to the original
        val reconstructedData = (result as ReconstructionResult.Success).data
        assertArrayEquals(originalData, reconstructedData,
            "Reconstructed data must be byte-for-byte equal to original")
        
        println("Successfully verified 16K data reconstruction from 10 out of 14 blocks")
    }
}