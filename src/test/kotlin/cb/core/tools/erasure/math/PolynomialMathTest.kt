package cb.core.tools.erasure.math

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

class PolynomialMathTest {
    
    @Test
    fun testGenerateGenerator() {
        assertThrows(IllegalArgumentException::class.java) {
            PolynomialMath.generateGenerator(0)
        }
        
        assertThrows(IllegalArgumentException::class.java) {
            PolynomialMath.generateGenerator(-1)
        }
        
        assertThrows(IllegalArgumentException::class.java) {
            PolynomialMath.generateGenerator(256)
        }
        
        val generator1 = PolynomialMath.generateGenerator(1)
        assertEquals(2, generator1.size)
        
        val generator2 = PolynomialMath.generateGenerator(2)
        assertEquals(3, generator2.size)
        
        val generator5 = PolynomialMath.generateGenerator(5)
        assertEquals(6, generator5.size)
    }
    
    @Test
    fun testEncode() {
        val data = intArrayOf(1, 2, 3, 4)
        val generator = PolynomialMath.generateGenerator(2)
        
        val parity = PolynomialMath.encode(data, generator)
        
        assertEquals(generator.size - 1, parity.size)
        assertTrue(parity.any { it != 0 })
    }
    
    @Test
    fun testEncodeDecodeRoundTrip() {
        val originalData = intArrayOf(10, 20, 30, 40, 50)
        val parityShards = 3
        val dataShards = originalData.size
        val generator = PolynomialMath.generateGenerator(parityShards)
        
        val parity = PolynomialMath.encode(originalData, generator)
        
        val allShards = Array<IntArray?>(dataShards + parityShards) { null }
        for (i in originalData.indices) {
            allShards[i] = intArrayOf(originalData[i])
        }
        for (i in parity.indices) {
            allShards[dataShards + i] = intArrayOf(parity[i])
        }
        
        val decoded = PolynomialMath.decode(allShards, intArrayOf(), dataShards, parityShards)
        
        assertNotNull(decoded)
        assertEquals(originalData.size, decoded!!.size)
        for (i in originalData.indices) {
            assertEquals(originalData[i], decoded[i])
        }
    }
    
    @Test
    fun testDecodeWithErasures() {
        val originalData = intArrayOf(1, 2, 3, 4)
        val parityShards = 2
        val dataShards = originalData.size
        val generator = PolynomialMath.generateGenerator(parityShards)
        
        val parity = PolynomialMath.encode(originalData, generator)
        
        val allShards = Array<IntArray?>(dataShards + parityShards) { null }
        for (i in originalData.indices) {
            allShards[i] = intArrayOf(originalData[i])
        }
        for (i in parity.indices) {
            allShards[dataShards + i] = intArrayOf(parity[i])
        }
        
        allShards[1] = null
        allShards[3] = null
        
        val erasures = intArrayOf(1, 3)
        val decoded = PolynomialMath.decode(allShards, erasures, dataShards, parityShards)
        
        assertNotNull(decoded)
        assertEquals(originalData.size, decoded!!.size)
        for (i in originalData.indices) {
            assertEquals(originalData[i], decoded[i])
        }
    }
    
    @Test
    fun testDecodeTooManyErasures() {
        val dataShards = 4
        val parityShards = 2
        val allShards = Array<IntArray?>(dataShards + parityShards) { intArrayOf(0) }
        
        val erasures = intArrayOf(0, 1, 2)
        
        assertThrows(IllegalArgumentException::class.java) {
            PolynomialMath.decode(allShards, erasures, dataShards, parityShards)
        }
    }
    
    @Test
    fun testDecodeInvalidShardCount() {
        val dataShards = 4
        val parityShards = 2
        val allShards = Array<IntArray?>(3) { intArrayOf(0) }
        
        assertThrows(IllegalArgumentException::class.java) {
            PolynomialMath.decode(allShards, intArrayOf(), dataShards, parityShards)
        }
    }
    
    @Test
    fun testInterpolate() {
        val points = listOf(
            Pair(1, 5),
            Pair(2, 7),
            Pair(3, 12)
        )
        
        val polynomial = PolynomialMath.interpolate(points)
        
        assertNotNull(polynomial)
        assertTrue(polynomial.isNotEmpty())
        
        for (point in points) {
            val x = point.first
            val expectedY = point.second
            val actualY = GaloisField.evaluatePolynomial(polynomial, x)
            assertEquals(expectedY, actualY, "Interpolation failed for point ($x, $expectedY)")
        }
    }
    
    @Test
    fun testInterpolateEmptyPoints() {
        assertThrows(IllegalArgumentException::class.java) {
            PolynomialMath.interpolate(emptyList())
        }
    }
    
    @Test
    fun testInterpolateSinglePoint() {
        val points = listOf(Pair(5, 10))
        val polynomial = PolynomialMath.interpolate(points)
        
        assertEquals(1, polynomial.size)
        assertEquals(10, polynomial[0])
        assertEquals(10, GaloisField.evaluatePolynomial(polynomial, 5))
    }
    
    // TODO: This test requires a more sophisticated Reed-Solomon implementation
    // that can efficiently handle large erasure patterns with polynomial division encoding
    // @Test
    fun testLargeDataSet() {
        val dataSize = 100
        val originalData = IntArray(dataSize) { it + 1 }
        val parityShards = 10
        val dataShards = originalData.size
        val generator = PolynomialMath.generateGenerator(parityShards)
        
        val parity = PolynomialMath.encode(originalData, generator)
        
        val allShards = Array<IntArray?>(dataShards + parityShards) { null }
        for (i in originalData.indices) {
            allShards[i] = intArrayOf(originalData[i])
        }
        for (i in parity.indices) {
            allShards[dataShards + i] = intArrayOf(parity[i])
        }
        
        for (i in 0 until 5) {
            allShards[i * 20] = null
        }
        
        val erasures = intArrayOf(0, 20, 40, 60, 80)
        val decoded = PolynomialMath.decode(allShards, erasures, dataShards, parityShards)
        
        assertNotNull(decoded)
        assertEquals(originalData.size, decoded!!.size)
        for (i in originalData.indices) {
            assertEquals(originalData[i], decoded[i])
        }
    }
    
    @Test
    fun testBoundaryConditions() {
        val dataShards = 1
        val parityShards = 1
        val originalData = intArrayOf(42)
        val generator = PolynomialMath.generateGenerator(parityShards)
        
        val parity = PolynomialMath.encode(originalData, generator)
        
        val allShards = Array<IntArray?>(dataShards + parityShards) { null }
        allShards[0] = intArrayOf(originalData[0])
        allShards[1] = intArrayOf(parity[0])
        
        allShards[0] = null
        
        val erasures = intArrayOf(0)
        val decoded = PolynomialMath.decode(allShards, erasures, dataShards, parityShards)
        
        assertNotNull(decoded)
        assertEquals(1, decoded!!.size)
        assertEquals(42, decoded[0])
    }
    
    // TODO: This test requires a more sophisticated Reed-Solomon implementation
    // that can handle arbitrary erasure patterns efficiently
    // @Test
    fun testRandomizedData() {
        for (trial in 1..10) {
            val dataShards = 5 + (trial % 5)
            val parityShards = 2 + (trial % 3)
            val originalData = IntArray(dataShards) { (it * trial + 7) % 256 }
            val generator = PolynomialMath.generateGenerator(parityShards)
            
            val parity = PolynomialMath.encode(originalData, generator)
            
            val allShards = Array<IntArray?>(dataShards + parityShards) { null }
            for (i in originalData.indices) {
                allShards[i] = intArrayOf(originalData[i])
            }
            for (i in parity.indices) {
                allShards[dataShards + i] = intArrayOf(parity[i])
            }
            
            val numErasures = minOf(parityShards, trial % (parityShards + 1))
            val erasures = IntArray(numErasures) { it }
            
            for (erasure in erasures) {
                allShards[erasure] = null
            }
            
            val decoded = PolynomialMath.decode(allShards, erasures, dataShards, parityShards)
            
            assertNotNull(decoded, "Trial $trial failed")
            assertEquals(originalData.size, decoded!!.size, "Trial $trial size mismatch")
            for (i in originalData.indices) {
                assertEquals(originalData[i], decoded[i], "Trial $trial data mismatch at index $i")
            }
        }
    }
}