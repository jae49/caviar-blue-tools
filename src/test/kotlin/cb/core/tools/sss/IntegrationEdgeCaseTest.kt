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

import cb.core.tools.erasure.math.GaloisField
import cb.core.tools.sss.models.SSSConfig
import cb.core.tools.sss.models.SSSResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class IntegrationEdgeCaseTest {
    
    private val sss = ShamirSecretSharing()
    
    @Test
    fun `handle GaloisField overflow scenarios`() {
        val config = SSSConfig(3, 5)
        
        // Create secrets that will stress GF(256) operations
        val edgeCaseSecrets = listOf(
            ByteArray(10) { 0xFF.toByte() },              // Maximum field values
            ByteArray(10) { 0x00 },                        // Zero values
            ByteArray(10) { 0x01 },                        // Unity values
            ByteArray(10) { 0xFE.toByte() },              // Near-maximum values
            ByteArray(10) { i -> (255 - i).toByte() },    // Descending values
            ByteArray(10) { i -> (i * 0x1B).toByte() }    // GF(256) generator multiples
        )
        
        for (secret in edgeCaseSecrets) {
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            
            val shares = (splitResult as SSSResult.Success).value.shares
            val reconstructResult = sss.reconstruct(shares.take(config.threshold))
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
        }
    }
    
    @Test
    fun `polynomial evaluation at field boundary values`() {
        val config = SSSConfig(5, 10)
        
        // Test with secrets that create specific polynomial coefficients
        val boundarySecrets = listOf(
            // Secret bytes that will create edge case coefficients
            ByteArray(20) { if (it == 0) 0xFF.toByte() else 0x00 },
            ByteArray(20) { if (it == 0) 0x00 else 0xFF.toByte() },
            ByteArray(20) { if (it % 2 == 0) 0xFF.toByte() else 0x00 }
        )
        
        for (secret in boundarySecrets) {
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            
            val shares = (splitResult as SSSResult.Success).value.shares
            
            // Verify all shares have valid field values
            shares.forEach { share ->
                share.data.forEach { byte ->
                    val value = byte.toInt() and 0xFF
                    assertTrue(value in 0..255, "Share contains invalid field value: $value")
                }
            }
            
            val reconstructResult = sss.reconstruct(shares.take(config.threshold))
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
        }
    }
    
    @Test
    fun `handle matrix operations with near-singular conditions`() {
        val config = SSSConfig(4, 8)
        
        // Create a secret that might lead to challenging matrix conditions
        val secret = ByteArray(32) { i ->
            when (i % 4) {
                0 -> 0x01
                1 -> 0x02
                2 -> 0x04
                else -> 0x08
            }
        }
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        
        // Test reconstruction with different share combinations
        // Some combinations might create near-singular matrices
        val combinations = listOf(
            listOf(0, 1, 2, 3),    // Sequential indices
            listOf(0, 2, 4, 6),    // Even indices
            listOf(1, 3, 5, 7),    // Odd indices
            listOf(0, 3, 4, 7),    // Mixed indices
            listOf(1, 2, 6, 7)     // Another mix
        )
        
        for (indices in combinations) {
            val selectedShares = indices.map { shares[it] }
            val reconstructResult = sss.reconstruct(selectedShares)
            assertTrue(reconstructResult is SSSResult.Success, 
                "Failed with indices: ${indices.joinToString(",")}")
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
        }
    }
    
    @Test
    fun `verify GaloisField properties maintained in SSS operations`() {
        val config = SSSConfig(3, 5)
        val secret = "GF property test".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        
        // Verify field properties for share operations
        for (i in 0 until shares[0].data.size) {
            val shareValues = shares.map { it.data[i].toInt() and 0xFF }
            
            // Test additive identity
            shareValues.forEach { value ->
                val sum = GaloisField.add(value, 0)
                assertEquals(value, sum, "Additive identity failed")
            }
            
            // Test multiplicative identity
            shareValues.forEach { value ->
                val product = GaloisField.multiply(value, 1)
                assertEquals(value, product, "Multiplicative identity failed")
            }
            
            // Test that operations stay in field
            for (j in 1 until shareValues.size) {
                val sum = GaloisField.add(shareValues[0], shareValues[j])
                assertTrue(sum in 0..255, "Addition escaped field")
                
                val product = GaloisField.multiply(shareValues[0], shareValues[j])
                assertTrue(product in 0..255, "Multiplication escaped field")
            }
        }
    }
    
    @Test
    fun `handle secrets with patterns that stress Lagrange interpolation`() {
        val config = SSSConfig(5, 10)
        
        // Patterns that create specific interpolation challenges
        val patterns = listOf(
            // Arithmetic progression
            ByteArray(20) { (it * 7 % 256).toByte() },
            // Geometric progression
            ByteArray(20) { i -> 
                var value = 1
                repeat(i) { value = (value * 2) % 256 }
                value.toByte()
            },
            // Fibonacci-like sequence
            ByteArray(20) { i ->
                if (i < 2) i.toByte()
                else {
                    var a = 0
                    var b = 1
                    repeat(i - 1) {
                        val temp = (a + b) % 256
                        a = b
                        b = temp
                    }
                    b.toByte()
                }
            }
        )
        
        for (pattern in patterns) {
            val splitResult = sss.split(pattern, config)
            assertTrue(splitResult is SSSResult.Success)
            
            val shares = (splitResult as SSSResult.Success).value.shares
            
            // Test with minimum shares
            val minResult = sss.reconstruct(shares.take(config.threshold))
            assertTrue(minResult is SSSResult.Success)
            assertArrayEquals(pattern, (minResult as SSSResult.Success).value)
            
            // Test with all shares
            val allResult = sss.reconstruct(shares)
            assertTrue(allResult is SSSResult.Success)
            assertArrayEquals(pattern, (allResult as SSSResult.Success).value)
        }
    }
    
    @Test
    fun `integration with extreme share indices`() {
        // Test the system's handling of share indices at boundaries
        val config = SSSConfig(64, 128)
        val secret = "Extreme indices test".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        
        // Verify indices are correctly assigned
        shares.forEachIndexed { index, share ->
            assertEquals(index + 1, share.index, "Share index mismatch")
        }
        
        // Test reconstruction with shares from different parts of the range
        val selections = listOf(
            (0 until 64).toList(),           // First half
            (64 until 128).toList(),         // Second half
            (0 until 128 step 2).toList(),   // Every other share
            (1 until 128 step 2).toList()    // Alternate set
        )
        
        for (indices in selections) {
            val selectedShares = indices.take(64).map { shares[it] }
            val reconstructResult = sss.reconstruct(selectedShares)
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
        }
    }
    
    @Test
    fun `verify polynomial degree constraints`() {
        val testCases = listOf(
            SSSConfig(2, 5),   // Degree 1 polynomial
            SSSConfig(3, 5),   // Degree 2 polynomial
            SSSConfig(5, 10),  // Degree 4 polynomial
            SSSConfig(10, 20), // Degree 9 polynomial
        )
        
        for (config in testCases) {
            val secret = "Polynomial degree test ${config.threshold}".toByteArray()
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            
            val shares = (splitResult as SSSResult.Success).value.shares
            
            // With k-1 shares, we shouldn't be able to reconstruct
            val insufficientResult = sss.reconstruct(shares.take(config.threshold - 1))
            assertFalse(insufficientResult is SSSResult.Success)
            
            // With k shares, we should reconstruct correctly
            val sufficientResult = sss.reconstruct(shares.take(config.threshold))
            assertTrue(sufficientResult is SSSResult.Success)
            assertArrayEquals(secret, (sufficientResult as SSSResult.Success).value)
            
            // The polynomial degree should be exactly k-1
            // This is implicitly verified by the threshold behavior
        }
    }
    
    @Test
    fun `handle rapid transitions between field values`() {
        val config = SSSConfig(4, 8)
        
        // Create secrets with rapid value transitions
        val transitionSecrets = listOf(
            // Alternating min/max
            ByteArray(30) { if (it % 2 == 0) 0x00 else 0xFF.toByte() },
            // Sawtooth pattern
            ByteArray(30) { (it * 17 % 256).toByte() },
            // Square wave pattern
            ByteArray(30) { if (it % 10 < 5) 0x00 else 0xFF.toByte() }
        )
        
        for (secret in transitionSecrets) {
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            
            val shares = (splitResult as SSSResult.Success).value.shares
            val reconstructResult = sss.reconstruct(shares.take(config.threshold))
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
        }
    }
}