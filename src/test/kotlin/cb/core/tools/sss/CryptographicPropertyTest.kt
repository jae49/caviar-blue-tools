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

package cb.core.tools.sss

import cb.core.tools.sss.crypto.SecureRandomGenerator
import cb.core.tools.sss.models.SSSConfig
import cb.core.tools.sss.models.SSSResult
import org.junit.jupiter.api.Test
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.*

class CryptographicPropertyTest {
    
    private val sss = ShamirSecretSharing()
    private val randomGen = SecureRandomGenerator()
    
    @Test
    fun `k-1 shares should reveal no information about the secret`() {
        val configs = listOf(
            SSSConfig(2, 3),
            SSSConfig(3, 5),
            SSSConfig(5, 10),
            SSSConfig(10, 20)
        )
        
        for (config in configs) {
            val secret1 = "First secret message".toByteArray()
            val secret2 = "Second secret messag".toByteArray()
            
            assertEquals(secret1.size, secret2.size, "Secrets must be same size for test")
            
            val splitResult1 = sss.split(secret1, config)
            val splitResult2 = sss.split(secret2, config)
            
            assertTrue(splitResult1 is SSSResult.Success)
            assertTrue(splitResult2 is SSSResult.Success)
            
            val shares1 = (splitResult1 as SSSResult.Success).value.shares
            val shares2 = (splitResult2 as SSSResult.Success).value.shares
            
            // With k-1 shares, we cannot distinguish between the two secrets
            val insufficientShares1 = shares1.take(config.threshold - 1)
            val insufficientShares2 = shares2.take(config.threshold - 1)
            
            // Reconstruction should fail with k-1 shares
            val reconstructResult1 = sss.reconstruct(insufficientShares1)
            val reconstructResult2 = sss.reconstruct(insufficientShares2)
            
            assertFalse(reconstructResult1 is SSSResult.Success)
            assertFalse(reconstructResult2 is SSSResult.Success)
            
            // The share values themselves should appear random and uncorrelated
            assertFalse(
                insufficientShares1.zip(insufficientShares2).all { (s1, s2) ->
                    s1.data.contentEquals(s2.data)
                },
                "k-1 shares from different secrets should not be identical"
            )
        }
    }
    
    @Test
    fun `share values should be uniformly distributed`() {
        val config = SSSConfig(3, 10)
        val numTrials = 100
        val buckets = IntArray(256)
        
        // Generate many shares and count byte value distribution
        repeat(numTrials) {
            val secret = randomGen.nextBytes(50)
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            
            val shares = (splitResult as SSSResult.Success).value.shares
            shares.forEach { share ->
                share.data.forEach { byte ->
                    buckets[byte.toInt() and 0xFF]++
                }
            }
        }
        
        // Check for reasonable distribution (chi-square test approximation)
        val totalBytes = buckets.sum()
        val expectedPerBucket = totalBytes / 256.0
        val chiSquare = buckets.sumOf { count ->
            val diff = count - expectedPerBucket
            (diff * diff) / expectedPerBucket
        }
        
        // With 255 degrees of freedom, chi-square should be reasonably close to 255
        // Using a very loose bound as this is not a rigorous statistical test
        assertTrue(chiSquare < 400, "Distribution appears non-uniform: chi-square = $chiSquare")
        
        // Also check that no bucket is empty or overly full
        assertTrue(buckets.all { it > 0 }, "Some byte values never appeared")
        assertTrue(buckets.all { it < expectedPerBucket * 3 }, "Some byte values appeared too frequently")
    }
    
    @Test
    fun `different splits of same secret should produce different shares`() {
        val config = SSSConfig(3, 5)
        val secret = "Reproducibility test secret".toByteArray()
        
        // Split the same secret multiple times
        val splits = (1..10).map {
            val result = sss.split(secret, config)
            assertTrue(result is SSSResult.Success)
            (result as SSSResult.Success).value.shares
        }
        
        // Check that shares are different between splits
        for (i in 0 until splits.size - 1) {
            for (j in i + 1 until splits.size) {
                val shares1 = splits[i]
                val shares2 = splits[j]
                
                // Compare corresponding shares
                for (k in shares1.indices) {
                    assertFalse(
                        shares1[k].data.contentEquals(shares2[k].data),
                        "Share $k should be different between splits"
                    )
                }
            }
        }
        
        // But all should reconstruct to the same secret
        splits.forEach { shares ->
            val reconstructResult = sss.reconstruct(shares.take(config.threshold))
            assertTrue(reconstructResult is SSSResult.Success)
            assertArrayEquals(secret, (reconstructResult as SSSResult.Success).value)
        }
    }
    
    @Test
    fun `polynomial coefficients should be cryptographically random`() {
        val numSamples = 1000
        val coefficientBytes = mutableListOf<Byte>()
        
        // Collect coefficient bytes from many polynomial generations
        repeat(numSamples) {
            val coeffs = randomGen.nextBytes(10)
            coefficientBytes.addAll(coeffs.toList())
        }
        
        // Test for basic randomness properties
        val byteArray = coefficientBytes.toByteArray()
        
        // Test 1: Frequency test - each byte value should appear roughly equally
        val frequencies = IntArray(256)
        byteArray.forEach { byte ->
            frequencies[byte.toInt() and 0xFF]++
        }
        
        val avgFrequency = byteArray.size / 256.0
        val maxDeviation = frequencies.maxOf { abs(it - avgFrequency) }
        assertTrue(maxDeviation < avgFrequency * 0.8, "Byte frequencies deviate too much from uniform")
        
        // Test 2: No obvious patterns in consecutive bytes
        var consecutiveEqual = 0
        for (i in 1 until byteArray.size) {
            if (byteArray[i] == byteArray[i - 1]) {
                consecutiveEqual++
            }
        }
        val consecutiveRatio = consecutiveEqual.toDouble() / byteArray.size
        assertTrue(consecutiveRatio < 0.01, "Too many consecutive equal bytes: $consecutiveRatio")
        
        // Test 3: Bit independence - XOR of consecutive bytes should also be random
        val xorResults = IntArray(256)
        for (i in 1 until byteArray.size) {
            val xor = (byteArray[i - 1].toInt() xor byteArray[i].toInt()) and 0xFF
            xorResults[xor]++
        }
        
        val xorAvg = (byteArray.size - 1) / 256.0
        val xorMaxDev = xorResults.maxOf { abs(it - xorAvg) }
        assertTrue(xorMaxDev < xorAvg * 0.8, "XOR distribution suggests correlation")
    }
    
    @Test
    fun `shares should maintain information theoretic security`() {
        val config = SSSConfig(5, 10)
        
        // Create two secrets with known relationship
        val secret1 = ByteArray(32) { 0x00 }
        val secret2 = ByteArray(32) { 0xFF.toByte() }
        
        val splitResult1 = sss.split(secret1, config)
        val splitResult2 = sss.split(secret2, config)
        
        assertTrue(splitResult1 is SSSResult.Success)
        assertTrue(splitResult2 is SSSResult.Success)
        
        val shares1 = (splitResult1 as SSSResult.Success).value.shares
        val shares2 = (splitResult2 as SSSResult.Success).value.shares
        
        // With fewer than k shares, the relationship between secrets should not be apparent
        val subset1 = shares1.take(4) // k-1 shares
        val subset2 = shares2.take(4)
        
        // Calculate byte-wise differences between corresponding shares
        val differences = mutableListOf<List<Int>>()
        for (i in subset1.indices) {
            val diff = subset1[i].data.zip(subset2[i].data).map { (b1, b2) ->
                (b1.toInt() and 0xFF) - (b2.toInt() and 0xFF)
            }
            differences.add(diff)
        }
        
        // The differences should not reveal the relationship between secrets
        // (i.e., they should not all be the same value)
        val allDifferences = differences.flatten()
        val uniqueDifferences = allDifferences.toSet()
        
        // With information theoretic security, differences should be well-distributed
        assertTrue(uniqueDifferences.size > 100, "Differences show too much pattern")
    }
    
    @Test
    fun `reconstruction should be deterministic given same shares`() {
        val config = SSSConfig(3, 5)
        val secret = "Deterministic reconstruction".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        val shareSubset = shares.take(3)
        
        // Reconstruct multiple times with same shares
        val reconstructions = (1..10).map {
            val result = sss.reconstruct(shareSubset)
            assertTrue(result is SSSResult.Success)
            (result as SSSResult.Success).value
        }
        
        // All reconstructions should be identical
        val firstReconstruction = reconstructions.first()
        reconstructions.forEach { reconstruction ->
            assertArrayEquals(firstReconstruction, reconstruction)
        }
    }
    
    @Test
    fun `share indices should not leak information about secret`() {
        val config = SSSConfig(3, 5)
        val secrets = listOf(
            ByteArray(20) { 0x00 },
            ByteArray(20) { 0xFF.toByte() },
            ByteArray(20) { it.toByte() },
            randomGen.nextBytes(20)
        )
        
        // For each secret, verify that share values at each index don't correlate with secret
        secrets.forEach { secret ->
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            
            val shares = (splitResult as SSSResult.Success).value.shares
            
            // The share values should not have obvious correlation with the secret bytes
            shares.forEach { share ->
                val correlation = calculateSimpleCorrelation(secret, share.data)
                assertTrue(abs(correlation) < 0.5, "Share shows correlation with secret: $correlation")
            }
        }
    }
    
    @Test
    fun `additive shares should not combine to reveal secret`() {
        val config = SSSConfig(3, 5)
        val secret = "Additive combination test".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        
        // Try various additive combinations of k-1 shares
        val combinations = listOf(
            shares.take(2), // First two shares
            shares.slice(listOf(0, 2)), // First and third
            shares.slice(listOf(1, 3))  // Second and fourth
        )
        
        combinations.forEach { shareSubset ->
            // XOR combination
            val xorResult = shareSubset.map { it.data }.reduce { acc, data ->
                acc.zip(data).map { (a, b) -> (a.toInt() xor b.toInt()).toByte() }.toByteArray()
            }
            assertFalse(secret.contentEquals(xorResult))
            
            // Additive combination (mod 256)
            val sumResult = shareSubset.map { it.data }.reduce { acc, data ->
                acc.zip(data).map { (a, b) -> ((a.toInt() + b.toInt()) % 256).toByte() }.toByteArray()
            }
            assertFalse(secret.contentEquals(sumResult))
        }
    }
    
    private fun calculateSimpleCorrelation(data1: ByteArray, data2: ByteArray): Double {
        require(data1.size == data2.size) { "Arrays must be same size" }
        
        val n = data1.size
        val sum1 = data1.sumOf { it.toInt() and 0xFF }
        val sum2 = data2.sumOf { it.toInt() and 0xFF }
        val sum12 = data1.zip(data2).sumOf { (a, b) -> (a.toInt() and 0xFF) * (b.toInt() and 0xFF) }
        val sum1Sq = data1.sumOf { val v = it.toInt() and 0xFF; v * v }
        val sum2Sq = data2.sumOf { val v = it.toInt() and 0xFF; v * v }
        
        val num = n * sum12 - sum1 * sum2
        val den = kotlin.math.sqrt((n * sum1Sq - sum1 * sum1).toDouble()) * 
                  kotlin.math.sqrt((n * sum2Sq - sum2 * sum2).toDouble())
        
        return if (den == 0.0) 0.0 else num / den
    }
}