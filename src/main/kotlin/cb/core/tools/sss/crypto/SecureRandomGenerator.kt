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

package cb.core.tools.sss.crypto

import java.security.SecureRandom

/**
 * Provides cryptographically secure random number generation for SSS operations.
 * 
 * This class ensures that all randomness used in the secret sharing process
 * meets cryptographic security requirements.
 */
class SecureRandomGenerator {
    private val secureRandom = SecureRandom()
    
    /**
     * Generates a cryptographically secure random byte.
     * 
     * The byte value is uniformly distributed in the range [0, 255].
     * Uses Java's SecureRandom for cryptographic strength.
     * 
     * @return Random byte value
     */
    fun nextByte(): Byte {
        return secureRandom.nextInt(256).toByte()
    }
    
    /**
     * Generates an array of cryptographically secure random bytes.
     * 
     * @param count The number of bytes to generate
     * @return Array of random bytes
     */
    fun nextBytes(count: Int): ByteArray {
        require(count >= 0) { "Count must be non-negative" }
        val bytes = ByteArray(count)
        secureRandom.nextBytes(bytes)
        return bytes
    }
    
    /**
     * Generates a cryptographically secure random field element.
     * 
     * Returns a value in the range [0, 255] suitable for GF(256) operations.
     * The distribution is uniform across all possible field elements.
     * 
     * @return Random field element in [0, 255]
     */
    fun nextFieldElement(): Int {
        return secureRandom.nextInt(256)
    }
    
    /**
     * Generates an array of cryptographically secure random field elements.
     * 
     * @param count The number of field elements to generate
     * @return Array of field elements in range [0, 255]
     */
    fun nextFieldElements(count: Int): IntArray {
        require(count >= 0) { "Count must be non-negative" }
        return IntArray(count) { nextFieldElement() }
    }
    
    /**
     * Generates a non-zero cryptographically secure random field element.
     * 
     * Returns a value in the range [1, 255] suitable for GF(256) operations.
     * This is useful for generating polynomial coefficients where zero values
     * would reduce the polynomial degree.
     * 
     * @return Random non-zero field element in [1, 255]
     */
    fun nextNonZeroFieldElement(): Int {
        var element: Int
        do {
            element = nextFieldElement()
        } while (element == 0)
        return element
    }
}