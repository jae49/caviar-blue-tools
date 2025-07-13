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
import cb.core.tools.sss.crypto.PolynomialGenerator
import cb.core.tools.sss.crypto.SecureRandomGenerator
import cb.core.tools.sss.models.SSSConfig
import cb.core.tools.sss.models.SSSResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Security validation tests for Shamir Secret Sharing implementation.
 * Tests cryptographic security properties, randomness quality, and attack resistance.
 */
class SecurityValidationTest {
    private val sss = ShamirSecretSharing()
    private val randomGenerator = SecureRandomGenerator()
    
    @Test
    fun `test coefficient randomness distribution`() {
        // Generate many polynomials and check coefficient distribution
        val sampleSize = 10000
        val coefficientCounts = IntArray(256)
        val generator = PolynomialGenerator(randomGenerator)
        
        repeat(sampleSize) {
            val config = SSSConfig(threshold = 5, totalShares = 10)
            val coefficients = generator.generateCoefficients(42.toByte(), config)
            // Skip the first coefficient (which is the secret) and only count random coefficients
            for (i in 1 until coefficients.size) {
                coefficientCounts[coefficients[i] and 0xFF]++
            }
        }
        
        // Check for uniform distribution using chi-square test
        val expected = (sampleSize * 4) / 256.0  // 4 random coefficients per polynomial
        var chiSquare = 0.0
        
        coefficientCounts.forEach { count ->
            val diff = count - expected
            chiSquare += (diff * diff) / expected
        }
        
        // Critical value for 255 degrees of freedom at 0.01 significance level
        val criticalValue = 310.5
        assertTrue(chiSquare < criticalValue, 
            "Coefficient distribution failed chi-square test: $chiSquare > $criticalValue")
    }
    
    @Test
    fun `test share value randomness`() {
        // Test that share values are uniformly distributed
        val secret = "test secret".toByteArray()
        val config = SSSConfig(threshold = 3, totalShares = 10)
        val shareValueCounts = IntArray(256)
        val iterations = 1000
        
        repeat(iterations) {
            val result = sss.split(secret, config)
            assertTrue(result.isSuccess())
            
            result.getOrNull()?.shares?.forEach { share ->
                share.data.forEach { byte ->
                    shareValueCounts[byte.toInt() and 0xFF]++
                }
            }
        }
        
        // Statistical test for uniformity
        val totalValues = shareValueCounts.sum()
        val expectedCount = totalValues / 256.0
        val chiSquare = shareValueCounts.sumOf { count ->
            val diff = count - expectedCount
            (diff * diff) / expectedCount
        }
        
        // Critical value for 255 degrees of freedom at 0.01 significance level
        val criticalValue = 310.5
        assertTrue(chiSquare < criticalValue,
            "Share value distribution failed chi-square test: $chiSquare > $criticalValue")
    }
    
    @Test
    fun `test sequential share independence`() {
        // Verify that sequential shares are statistically independent
        val secret = ByteArray(100) { it.toByte() }
        val config = SSSConfig(threshold = 5, totalShares = 10)
        val correlations = mutableListOf<Double>()
        
        repeat(100) {
            val result = sss.split(secret, config)
            val shares = result.getOrNull()?.shares ?: fail("Split failed")
            
            // Calculate correlation between adjacent shares
            for (i in 0 until shares.size - 1) {
                val correlation = calculateCorrelation(shares[i].data, shares[i + 1].data)
                correlations.add(correlation)
            }
        }
        
        // Check that average correlation is close to 0
        val avgCorrelation = correlations.average()
        assertTrue(abs(avgCorrelation) < 0.1,
            "Adjacent shares show correlation: $avgCorrelation")
    }
    
    @Test
    fun `test information theoretic security with k-1 shares`() {
        // Verify that k-1 shares reveal no information about the secret
        val secrets = listOf(
            ByteArray(50) { 0x00 },
            ByteArray(50) { 0xFF.toByte() },
            ByteArray(50) { it.toByte() }
        )
        
        val config = SSSConfig(threshold = 5, totalShares = 10)
        val shareSetsBySecret = secrets.map { secret ->
            val result = sss.split(secret, config)
            result.getOrNull()?.shares?.take(4) ?: fail("Split failed")
        }
        
        // Check that share distributions are indistinguishable
        val distributions = shareSetsBySecret.map { shares ->
            calculateDistribution(shares.flatMap { it.data.toList() })
        }
        
        // Compare distributions pairwise
        for (i in 0 until distributions.size - 1) {
            for (j in i + 1 until distributions.size) {
                val distance = calculateDistributionDistance(distributions[i], distributions[j])
                assertTrue(distance < 0.65,
                    "Share distributions are distinguishable: distance = $distance")
            }
        }
    }
    
    @Test
    @Tag("slow")
    fun `test entropy of generated coefficients`() {
        // Measure entropy of polynomial coefficients
        val sampleSize = 100000
        val generator = PolynomialGenerator(randomGenerator)
        val byteCounts = IntArray(256)
        
        repeat(sampleSize) {
            val config = SSSConfig(threshold = 10, totalShares = 20)
            val coefficients = generator.generateCoefficients(0.toByte(), config)
            coefficients.forEach { coeff ->
                byteCounts[coeff.toInt() and 0xFF]++
            }
        }
        
        // Calculate Shannon entropy
        val total = byteCounts.sum().toDouble()
        val entropy = byteCounts
            .filter { it > 0 }
            .sumOf { count ->
                val probability = count / total
                -probability * (Math.log(probability) / Math.log(2.0))
            }
        
        // Entropy should be close to 8 bits for uniform distribution
        assertTrue(entropy > 7.95,
            "Coefficient entropy too low: $entropy bits (expected ~8)")
    }
    
    @Test
    fun `test resistance to share substitution attack`() {
        // Test that substituting shares from different operations is detected
        val secret1 = "secret one".toByteArray()
        val secret2 = "secret two".toByteArray()
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        val shares1 = sss.split(secret1, config).getOrNull()?.shares ?: fail("Split 1 failed")
        val shares2 = sss.split(secret2, config).getOrNull()?.shares ?: fail("Split 2 failed")
        
        // Try to mix shares from different operations
        val mixedShares = listOf(shares1[0], shares1[1], shares2[2])
        val result = sss.reconstruct(mixedShares)
        
        assertTrue(result.isFailure())
        val error = (result as? SSSResult.Failure)?.message ?: ""
        assertTrue(error.contains("different split operations") || 
                  error.contains("Incompatible shares"),
            "Mixed share attack not detected")
    }
    
    @Test
    fun `test no timing side channels in reconstruction`() {
        // Test that reconstruction time doesn't leak information about shares
        val secret = ByteArray(100) { it.toByte() }
        val config = SSSConfig(threshold = 5, totalShares = 10)
        val shares = sss.split(secret, config).getOrNull()?.shares ?: fail("Split failed")
        
        // Measure reconstruction times for different share combinations
        val timings = mutableListOf<Long>()
        
        repeat(50) {
            val selectedShares = shares.shuffled().take(5)
            val startTime = System.nanoTime()
            sss.reconstruct(selectedShares)
            val endTime = System.nanoTime()
            timings.add(endTime - startTime)
        }
        
        // Check that timing variance is low
        val mean = timings.average()
        val variance = timings.map { (it - mean) * (it - mean) }.average()
        val coefficientOfVariation = sqrt(variance) / mean
        
        assertTrue(coefficientOfVariation < 1.5,
            "High timing variance detected: CV = $coefficientOfVariation")
    }
    
    @Test
    fun `test secure random generator quality`() {
        // Test the quality of the SecureRandom implementation
        val generator = SecureRandomGenerator()
        val samples = 100000
        val bytes = ByteArray(samples)
        
        for (i in 0 until samples) {
            bytes[i] = generator.nextByte()
        }
        
        // Run NIST SP 800-22 inspired tests
        
        // 1. Frequency test
        val ones = bytes.sumOf { byte ->
            Integer.bitCount(byte.toInt() and 0xFF)
        }
        val zeros = samples * 8 - ones
        val chiSquare = ((ones - zeros) * (ones - zeros)) / (samples * 8.0)
        assertTrue(chiSquare < 3.84, // 95% confidence level
            "Frequency test failed: chi-square = $chiSquare")
        
        // 2. Runs test (sequences of consecutive identical bits)
        var runs = 1
        var previousBit = (bytes[0].toInt() and 0x80) != 0
        
        for (byte in bytes) {
            for (bit in 7 downTo 0) {
                val currentBit = (byte.toInt() and (1 shl bit)) != 0
                if (currentBit != previousBit) {
                    runs++
                    previousBit = currentBit
                }
            }
        }
        
        val expectedRuns = (samples * 8) / 2.0
        val runsDeviation = abs(runs - expectedRuns) / sqrt(expectedRuns)
        assertTrue(runsDeviation < 3.0,
            "Runs test failed: deviation = $runsDeviation")
    }
    
    // Helper functions
    
    private fun calculateCorrelation(data1: ByteArray, data2: ByteArray): Double {
        require(data1.size == data2.size) { "Data arrays must have same size" }
        
        val n = data1.size
        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumX2 = 0.0
        var sumY2 = 0.0
        
        for (i in 0 until n) {
            val x = data1[i].toInt() and 0xFF
            val y = data2[i].toInt() and 0xFF
            sumX += x
            sumY += y
            sumXY += x * y
            sumX2 += x * x
            sumY2 += y * y
        }
        
        val numerator = n * sumXY - sumX * sumY
        val denominator = sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY))
        
        return if (denominator == 0.0) 0.0 else numerator / denominator
    }
    
    private fun calculateDistribution(data: List<Byte>): DoubleArray {
        val counts = IntArray(256)
        data.forEach { byte ->
            counts[byte.toInt() and 0xFF]++
        }
        
        val total = data.size.toDouble()
        return DoubleArray(256) { i -> counts[i] / total }
    }
    
    private fun calculateDistributionDistance(dist1: DoubleArray, dist2: DoubleArray): Double {
        require(dist1.size == dist2.size) { "Distributions must have same size" }
        
        return dist1.indices.sumOf { i ->
            abs(dist1[i] - dist2[i])
        } / 2.0
    }
}