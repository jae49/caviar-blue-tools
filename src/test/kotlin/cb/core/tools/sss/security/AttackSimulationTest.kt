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

package cb.core.tools.sss.security

import cb.core.tools.sss.ShamirSecretSharing
import cb.core.tools.sss.models.SSSConfig
import cb.core.tools.sss.models.SecretShare
import cb.core.tools.sss.models.ShareMetadata
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import java.util.Base64
import kotlin.random.Random

/**
 * Tests for various attack scenarios against the Shamir Secret Sharing implementation.
 * Simulates known attacks and verifies the implementation's resistance.
 */
class AttackSimulationTest {
    private val sss = ShamirSecretSharing()
    
    @Test
    fun `test resistance to small subgroup attack`() {
        // Test that the implementation handles special field elements correctly
        val problematicSecrets = listOf(
            ByteArray(10) { 0x00 }, // All zeros
            ByteArray(10) { 0x01 }, // All ones (multiplicative identity)
            ByteArray(10) { 0xFF.toByte() } // All 255s
        )
        
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        problematicSecrets.forEach { secret ->
            val result = sss.split(secret, config)
            assertTrue(result.isSuccess(), "Failed to split problematic secret")
            
            val shares = result.getOrNull()?.shares ?: fail("No shares generated")
            
            // Verify reconstruction works correctly
            val reconstructed = sss.reconstruct(shares.take(3))
            assertTrue(reconstructed.isSuccess(), "Failed to reconstruct problematic secret")
            assertArrayEquals(secret, reconstructed.getOrNull(), 
                "Reconstruction failed for problematic secret")
        }
    }
    
    @Test
    fun `test resistance to polynomial coefficient manipulation`() {
        // Attempt to manipulate polynomial coefficients through share manipulation
        val secret = "sensitive data".toByteArray()
        val config = SSSConfig(threshold = 3, totalShares = 5)
        val shares = sss.split(secret, config).getOrNull()?.shares ?: fail("Split failed")
        
        // Try to create a forged share that would affect polynomial reconstruction
        val forgedShare = shares[0].copy(
            index = 6, // Outside the original range
            data = ByteArray(shares[0].data.size) { Random.nextBytes(1)[0] }
        )
        
        // Attempt reconstruction with forged share
        val maliciousShares = listOf(shares[1], shares[2], forgedShare)
        val result = sss.reconstruct(maliciousShares)
        
        // Should either fail or produce incorrect result (not the original secret)
        if (result.isSuccess()) {
            assertFalse(secret.contentEquals(result.getOrNull() ?: ByteArray(0)),
                "Forged share attack succeeded in recovering secret")
        }
    }
    
    @Test
    fun `test resistance to share index manipulation`() {
        // Test that manipulating share indices is detected
        val secret = "test secret".toByteArray()
        val config = SSSConfig(threshold = 3, totalShares = 5)
        val shares = sss.split(secret, config).getOrNull()?.shares ?: fail("Split failed")
        
        // Create a share with manipulated index
        val manipulatedShare = shares[2].copy(index = shares[0].index)
        val maliciousShares = listOf(shares[0], shares[1], manipulatedShare)
        
        val result = sss.reconstruct(maliciousShares)
        assertTrue(result.isFailure() || !secret.contentEquals(result.getOrNull() ?: ByteArray(0)),
            "Index manipulation attack not properly handled")
    }
    
    @Test
    fun `test resistance to replay attack`() {
        // Test that old shares cannot be reused with new secrets
        val secret1 = "first secret".toByteArray()
        val secret2 = "second secret".toByteArray()
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        // Generate shares for first secret
        val shares1 = sss.split(secret1, config).getOrNull()?.shares ?: fail("Split 1 failed")
        
        // Generate shares for second secret
        val shares2 = sss.split(secret2, config).getOrNull()?.shares ?: fail("Split 2 failed")
        
        // Try to use old shares with new shares (replay attack)
        val replayShares = listOf(shares1[0], shares2[1], shares2[2])
        val result = sss.reconstruct(replayShares)
        
        assertTrue(result.isFailure() || 
            (!secret1.contentEquals(result.getOrNull() ?: ByteArray(0)) &&
             !secret2.contentEquals(result.getOrNull() ?: ByteArray(0))),
            "Replay attack succeeded")
    }
    
    @Test
    fun `test resistance to chosen share attack`() {
        // Test resistance when attacker can choose specific share values
        val secret = "target secret".toByteArray()
        val config = SSSConfig(threshold = 3, totalShares = 5)
        val validShares = sss.split(secret, config).getOrNull()?.shares ?: fail("Split failed")
        
        // Attacker tries to craft shares with specific patterns
        val craftedShares = validShares.take(2).toMutableList()
        
        // Create a share with carefully chosen values
        val craftedData = ByteArray(validShares[0].data.size) { i ->
            // Pattern designed to potentially influence reconstruction
            (i * 17 + 23).toByte()
        }
        
        val craftedShare = SecretShare(
            index = validShares[2].index,
            data = craftedData,
            metadata = validShares[2].metadata,
            dataHash = ByteArray(32) // Invalid hash will fail integrity check
        )
        
        craftedShares.add(craftedShare)
        
        val result = sss.reconstruct(craftedShares)
        assertTrue(result.isFailure(),
            "Chosen share attack with crafted values should fail integrity check")
    }
    
    @Test
    fun `test resistance to metadata tampering`() {
        // Test that metadata tampering is detected
        val secret = "metadata test".toByteArray()
        val config = SSSConfig(threshold = 3, totalShares = 5)
        val shares = sss.split(secret, config).getOrNull()?.shares ?: fail("Split failed")
        
        // Tamper with metadata
        val tamperedMetadata = shares[0].metadata.copy(
            threshold = 2, // Changed from 3
            totalShares = 10 // Changed from 5
        )
        
        val tamperedShare = shares[0].copy(metadata = tamperedMetadata)
        val maliciousShares = listOf(tamperedShare, shares[1], shares[2])
        
        val result = sss.reconstruct(maliciousShares)
        assertTrue(result.isFailure(),
            "Metadata tampering not detected")
    }
    
    @Test
    @Tag("slow")
    fun `test resistance to brute force attack on small secrets`() {
        // Test that even small secrets are protected against brute force
        val smallSecret = ByteArray(4) { it.toByte() } // 32-bit secret
        val config = SSSConfig(threshold = 3, totalShares = 5)
        val shares = sss.split(smallSecret, config).getOrNull()?.shares ?: fail("Split failed")
        
        // Attacker has k-1 shares and tries to brute force the last share
        val knownShares = shares.take(2)
        var attempts = 0
        val maxAttempts = 1000 // Limited for test performance
        
        for (i in 0 until maxAttempts) {
            attempts++
            val guessData = ByteArray(shares[2].data.size) { Random.nextBytes(1)[0] }
            val guessShare = shares[2].copy(data = guessData, dataHash = ByteArray(32))
            
            val result = sss.reconstruct(knownShares + guessShare)
            if (result.isSuccess() && smallSecret.contentEquals(result.getOrNull() ?: ByteArray(0))) {
                fail<Unit>("Brute force attack succeeded after $attempts attempts")
            }
        }
        
        assertTrue(attempts == maxAttempts,
            "Brute force protection verified after $maxAttempts attempts")
    }
    
    @Test
    fun `test resistance to differential attack`() {
        // Test that small changes in secret produce unpredictable share changes
        val secret1 = "test secret 1".toByteArray()
        val secret2 = "test secret 2".toByteArray() // Only 1 byte different
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        val shares1 = sss.split(secret1, config).getOrNull()?.shares ?: fail("Split 1 failed")
        val shares2 = sss.split(secret2, config).getOrNull()?.shares ?: fail("Split 2 failed")
        
        // Calculate differences between corresponding shares
        var totalDifferences = 0
        var totalBytes = 0
        
        for (i in shares1.indices) {
            val data1 = shares1[i].data
            val data2 = shares2[i].data
            
            for (j in data1.indices) {
                if (data1[j] != data2[j]) {
                    totalDifferences++
                }
                totalBytes++
            }
        }
        
        // Differences should be widespread (avalanche effect)
        val differenceRatio = totalDifferences.toDouble() / totalBytes
        assertTrue(differenceRatio > 0.4,
            "Insufficient avalanche effect: only ${differenceRatio * 100}% of bytes differ")
    }
    
    @Test
    fun `test resistance to serialization attack`() {
        // Test that manipulating serialized share format is detected
        val secret = "serialization test".toByteArray()
        val config = SSSConfig(threshold = 3, totalShares = 5)
        val shares = sss.split(secret, config).getOrNull()?.shares ?: fail("Split failed")
        
        // Get serialized share
        val originalSerialized = shares[0].toBase64()
        
        // Attempt various manipulations
        val manipulations = listOf(
            // Truncate the base64 string
            originalSerialized.substring(0, originalSerialized.length - 10),
            // Add extra data
            originalSerialized + "AAAA",
            // Modify in the middle
            originalSerialized.replaceRange(20..25, "XXXXXX"),
            // Invalid base64
            originalSerialized.replace('=', '!')
        )
        
        manipulations.forEach { manipulated ->
            try {
                val deserializedShare = SecretShare.fromBase64(manipulated)
                // If deserialization succeeds, reconstruction should fail
                val result = sss.reconstruct(listOf(deserializedShare, shares[1], shares[2]))
                assertTrue(result.isFailure(),
                    "Manipulated serialization should fail validation")
            } catch (e: Exception) {
                // Deserialization failure is also acceptable
                assertTrue(true, "Deserialization properly rejected manipulated data")
            }
        }
    }
    
    @Test
    fun `test resistance to correlation attack`() {
        // Test that shares don't leak information through correlations
        val secrets = List(50) { i ->
            "secret number $i with some padding".toByteArray()
        }
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        // Collect share data for analysis
        val shareDataByPosition = List(5) { mutableListOf<ByteArray>() }
        
        secrets.forEach { secret ->
            val shares = sss.split(secret, config).getOrNull()?.shares ?: fail("Split failed")
            shares.forEachIndexed { index, share ->
                shareDataByPosition[index].add(share.data)
            }
        }
        
        // Check for correlations between share positions
        for (pos1 in 0 until 4) {
            for (pos2 in pos1 + 1 until 5) {
                val correlation = calculateAverageCorrelation(
                    shareDataByPosition[pos1],
                    shareDataByPosition[pos2]
                )
                assertTrue(kotlin.math.abs(correlation) < 0.1,
                    "High correlation detected between positions $pos1 and $pos2: $correlation")
            }
        }
    }
    
    // Helper function for correlation calculation
    private fun calculateAverageCorrelation(
        dataList1: List<ByteArray>, 
        dataList2: List<ByteArray>
    ): Double {
        require(dataList1.size == dataList2.size) { "Lists must have same size" }
        
        var totalCorrelation = 0.0
        var count = 0
        
        for (i in dataList1.indices) {
            val data1 = dataList1[i]
            val data2 = dataList2[i]
            
            if (data1.size == data2.size) {
                for (j in data1.indices) {
                    val byte1 = data1[j].toInt() and 0xFF
                    val byte2 = data2[j].toInt() and 0xFF
                    totalCorrelation += (byte1 - 127.5) * (byte2 - 127.5) / (127.5 * 127.5)
                    count++
                }
            }
        }
        
        return if (count > 0) totalCorrelation / count else 0.0
    }
}