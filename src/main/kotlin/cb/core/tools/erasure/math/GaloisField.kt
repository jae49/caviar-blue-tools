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

package cb.core.tools.erasure.math

object GaloisField {
    private const val FIELD_SIZE = 256
    private const val PRIMITIVE_POLYNOMIAL = 0x11d  // x^8 + x^4 + x^3 + x^2 + 1
    
    private val expTable = IntArray(512)  // Extended to avoid bounds checking
    private val logTable = IntArray(FIELD_SIZE)
    
    init {
        initializeTables()
    }
    
    private fun initializeTables() {
        var x = 1
        for (i in 0 until 255) {
            expTable[i] = x
            logTable[x] = i
            x = x shl 1
            if (x and 0x100 != 0) {  // If bit 8 is set
                x = x xor PRIMITIVE_POLYNOMIAL
            }
        }
        
        // Extend the table to avoid modular arithmetic
        for (i in 255 until 512) {
            expTable[i] = expTable[i - 255]
        }
    }
    
    fun add(a: Int, b: Int): Int = a xor b
    
    fun subtract(a: Int, b: Int): Int = a xor b
    
    fun multiply(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return expTable[logTable[a] + logTable[b]]
    }
    
    fun divide(a: Int, b: Int): Int {
        require(b != 0) { "Division by zero in Galois field" }
        if (a == 0) return 0
        return expTable[logTable[a] - logTable[b] + 255]
    }
    
    fun power(base: Int, exponent: Int): Int {
        if (exponent == 0) return 1
        if (base == 0) return 0
        return expTable[(logTable[base] * exponent) % 255]
    }
    
    fun inverse(a: Int): Int {
        require(a != 0) { "Cannot find inverse of zero in Galois field" }
        return expTable[255 - logTable[a]]
    }
    
    fun exp(exponent: Int): Int = expTable[exponent % 255]
    
    fun log(value: Int): Int {
        require(value != 0) { "Cannot find log of zero in Galois field" }
        return logTable[value]
    }
    
    fun isValid(value: Int): Boolean = value in 0 until FIELD_SIZE
    
    fun multiplyPolynomial(poly1: IntArray, poly2: IntArray): IntArray {
        val result = IntArray(poly1.size + poly2.size - 1)
        
        for (i in poly1.indices) {
            for (j in poly2.indices) {
                result[i + j] = add(result[i + j], multiply(poly1[i], poly2[j]))
            }
        }
        
        return result
    }
    
    fun evaluatePolynomial(polynomial: IntArray, x: Int): Int {
        var result = 0
        var xPower = 1
        
        for (coefficient in polynomial) {
            result = add(result, multiply(coefficient, xPower))
            xPower = multiply(xPower, x)
        }
        
        return result
    }
    
    fun dividePolynomial(dividend: IntArray, divisor: IntArray): Pair<IntArray, IntArray> {
        require(divisor.isNotEmpty() && divisor.last() != 0) { "Invalid divisor polynomial" }
        
        if (dividend.size < divisor.size) {
            return Pair(intArrayOf(0), dividend.copyOf())
        }
        
        val quotient = IntArray(dividend.size - divisor.size + 1)
        val remainder = dividend.copyOf()
        
        val leadCoeff = divisor.last()
        
        for (i in quotient.indices.reversed()) {
            val remainderPos = remainder.size - 1 - (quotient.size - 1 - i)
            if (remainderPos >= 0 && remainder[remainderPos] != 0) {
                val coeff = divide(remainder[remainderPos], leadCoeff)
                quotient[i] = coeff
                
                for (j in divisor.indices) {
                    val pos = remainderPos - (divisor.size - 1 - j)
                    if (pos >= 0) {
                        remainder[pos] = subtract(remainder[pos], multiply(coeff, divisor[j]))
                    }
                }
            }
        }
        
        val trimmedRemainder = remainder.dropWhile { it == 0 }.toIntArray()
        return Pair(quotient, if (trimmedRemainder.isEmpty()) intArrayOf(0) else trimmedRemainder)
    }
}