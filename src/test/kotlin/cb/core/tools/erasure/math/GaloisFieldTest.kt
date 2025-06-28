package cb.core.tools.erasure.math

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class GaloisFieldTest {
    
    @Test
    fun testAddition() {
        assertEquals(0, GaloisField.add(0, 0))
        assertEquals(5, GaloisField.add(5, 0))
        assertEquals(5, GaloisField.add(0, 5))
        assertEquals(0, GaloisField.add(5, 5))
        assertEquals(7, GaloisField.add(3, 4))
        assertEquals(1, GaloisField.add(255, 254))
    }
    
    @Test
    fun testSubtraction() {
        assertEquals(0, GaloisField.subtract(0, 0))
        assertEquals(5, GaloisField.subtract(5, 0))
        assertEquals(5, GaloisField.subtract(0, 5))
        assertEquals(0, GaloisField.subtract(5, 5))
        assertEquals(7, GaloisField.subtract(3, 4))
        
        for (a in 0..255) {
            for (b in 0..255) {
                assertEquals(GaloisField.add(a, b), GaloisField.subtract(a, b))
            }
        }
    }
    
    @Test
    fun testMultiplication() {
        assertEquals(0, GaloisField.multiply(0, 0))
        assertEquals(0, GaloisField.multiply(0, 5))
        assertEquals(0, GaloisField.multiply(5, 0))
        assertEquals(1, GaloisField.multiply(1, 1))
        assertEquals(5, GaloisField.multiply(5, 1))
        assertEquals(5, GaloisField.multiply(1, 5))
        
        assertEquals(4, GaloisField.multiply(2, 2))
        assertEquals(6, GaloisField.multiply(2, 3))
        assertEquals(12, GaloisField.multiply(3, 4))
        assertEquals(15, GaloisField.multiply(3, 5))
    }
    
    @Test
    fun testDivision() {
        assertThrows(IllegalArgumentException::class.java) {
            GaloisField.divide(5, 0)
        }
        
        assertEquals(0, GaloisField.divide(0, 5))
        assertEquals(5, GaloisField.divide(5, 1))
        assertEquals(1, GaloisField.divide(5, 5))
        
        for (a in 1..255) {
            for (b in 1..255) {
                val product = GaloisField.multiply(a, b)
                assertEquals(a, GaloisField.divide(product, b))
                assertEquals(b, GaloisField.divide(product, a))
            }
        }
    }
    
    @Test
    fun testPower() {
        assertEquals(1, GaloisField.power(5, 0))
        assertEquals(5, GaloisField.power(5, 1))
        assertEquals(0, GaloisField.power(0, 5))
        assertEquals(1, GaloisField.power(0, 0))
        
        assertEquals(25, GaloisField.power(5, 2))
        assertEquals(125, GaloisField.power(5, 3))
        
        for (base in 1..10) {
            var expected = 1
            for (exp in 0..5) {
                assertEquals(expected, GaloisField.power(base, exp))
                expected = GaloisField.multiply(expected, base)
            }
        }
    }
    
    @Test
    fun testInverse() {
        assertThrows(IllegalArgumentException::class.java) {
            GaloisField.inverse(0)
        }
        
        assertEquals(1, GaloisField.inverse(1))
        
        for (a in 1..255) {
            val inv = GaloisField.inverse(a)
            assertEquals(1, GaloisField.multiply(a, inv))
            assertEquals(1, GaloisField.multiply(inv, a))
        }
    }
    
    @Test
    fun testExpAndLog() {
        assertEquals(1, GaloisField.exp(0))
        assertEquals(2, GaloisField.exp(1))
        
        for (i in 1..255) {
            val exp = GaloisField.exp(i - 1)
            assertEquals(i - 1, GaloisField.log(exp))
        }
        
        assertThrows(IllegalArgumentException::class.java) {
            GaloisField.log(0)
        }
    }
    
    @Test
    fun testIsValid() {
        assertTrue(GaloisField.isValid(0))
        assertTrue(GaloisField.isValid(255))
        assertTrue(GaloisField.isValid(128))
        
        assertFalse(GaloisField.isValid(-1))
        assertFalse(GaloisField.isValid(256))
        assertFalse(GaloisField.isValid(1000))
    }
    
    @Test
    fun testMultiplyPolynomial() {
        val poly1 = intArrayOf(1, 2)
        val poly2 = intArrayOf(3, 4)
        val result = GaloisField.multiplyPolynomial(poly1, poly2)
        
        assertEquals(3, result.size)
        assertEquals(3, result[0])
        assertEquals(GaloisField.add(GaloisField.multiply(1, 4), GaloisField.multiply(2, 3)), result[1])
        assertEquals(GaloisField.multiply(2, 4), result[2])
    }
    
    @Test
    fun testEvaluatePolynomial() {
        val polynomial = intArrayOf(1, 2, 3)
        val x = 5
        
        val expected = GaloisField.add(
            GaloisField.add(1, GaloisField.multiply(2, x)),
            GaloisField.multiply(3, GaloisField.multiply(x, x))
        )
        
        assertEquals(expected, GaloisField.evaluatePolynomial(polynomial, x))
    }
    
    @Test
    fun testDividePolynomial() {
        val dividend = intArrayOf(1, 2, 3, 4)
        val divisor = intArrayOf(1, 1)
        
        val (quotient, remainder) = GaloisField.dividePolynomial(dividend, divisor)
        
        assertTrue(quotient.isNotEmpty())
        assertTrue(remainder.isNotEmpty())
        
        val reconstructed = GaloisField.multiplyPolynomial(quotient, divisor)
        val paddedRemainder = IntArray(reconstructed.size) { if (it < remainder.size) remainder[it] else 0 }
        val result = IntArray(reconstructed.size) { GaloisField.add(reconstructed[it], paddedRemainder[it]) }
        
        assertEquals(dividend.size, result.size)
        for (i in dividend.indices) {
            assertEquals(dividend[i], result[i])
        }
    }
    
    @Test
    fun testFieldProperties() {
        for (a in 0..255) {
            for (b in 0..255) {
                for (c in 0..255) {
                    assertEquals(
                        GaloisField.add(a, GaloisField.add(b, c)),
                        GaloisField.add(GaloisField.add(a, b), c),
                        "Addition associativity failed for $a, $b, $c"
                    )
                    
                    assertEquals(
                        GaloisField.add(a, b),
                        GaloisField.add(b, a),
                        "Addition commutativity failed for $a, $b"
                    )
                    
                    if (a != 0 && b != 0 && c != 0) {
                        assertEquals(
                            GaloisField.multiply(a, GaloisField.multiply(b, c)),
                            GaloisField.multiply(GaloisField.multiply(a, b), c),
                            "Multiplication associativity failed for $a, $b, $c"
                        )
                        
                        assertEquals(
                            GaloisField.multiply(a, b),
                            GaloisField.multiply(b, a),
                            "Multiplication commutativity failed for $a, $b"
                        )
                    }
                }
            }
        }
    }
}