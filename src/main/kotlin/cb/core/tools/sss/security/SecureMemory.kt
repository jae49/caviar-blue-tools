package cb.core.tools.sss.security

import java.security.SecureRandom
import java.util.Arrays

/**
 * Utility class for secure memory handling of sensitive data.
 * 
 * Provides methods to securely clear sensitive data from memory to prevent
 * information leakage through memory dumps, swap files, or other side channels.
 */
object SecureMemory {
    
    private val secureRandom = SecureRandom()
    
    /**
     * Securely clears a byte array by overwriting it with random data multiple times.
     * 
     * This helps prevent sensitive data from being recovered from memory even after
     * the array is no longer in use. The multiple overwrites with random data make
     * it much harder to recover the original values.
     * 
     * @param data The byte array to clear
     * @param passes Number of overwrite passes (default: 3)
     */
    @JvmStatic
    fun clear(data: ByteArray?, passes: Int = 3) {
        if (data == null || data.isEmpty()) return
        
        try {
            // Multiple passes with different patterns
            repeat(passes) { pass ->
                when (pass % 3) {
                    0 -> {
                        // Random overwrite
                        secureRandom.nextBytes(data)
                    }
                    1 -> {
                        // All ones
                        Arrays.fill(data, 0xFF.toByte())
                    }
                    2 -> {
                        // All zeros
                        Arrays.fill(data, 0x00.toByte())
                    }
                }
            }
            
            // Final zero fill
            Arrays.fill(data, 0x00.toByte())
        } catch (e: Exception) {
            // Even if clearing fails, we tried our best
            // Don't throw to avoid disrupting the main flow
        }
    }
    
    /**
     * Securely clears a list of byte arrays.
     * 
     * Applies the secure clearing process to each array in the list.
     * Null arrays are safely ignored.
     * 
     * @param arrays List of byte arrays to clear
     */
    @JvmStatic
    fun clearAll(arrays: List<ByteArray?>) {
        arrays.forEach { clear(it) }
    }
    
    /**
     * Executes a block of code and ensures the provided byte arrays are cleared afterwards.
     * 
     * This provides exception safety by ensuring cleanup happens even if the block throws.
     * Use this when working with sensitive data that must be cleared from memory.
     * 
     * Example:
     * ```kotlin
     * val result = SecureMemory.withSecureCleanup(sensitiveData, tempBuffer) {
     *     // Process sensitive data
     *     computeResult(sensitiveData, tempBuffer)
     * }
     * // Arrays are now cleared
     * ```
     * 
     * @param arrays Arrays to clear after the block executes
     * @param block The code block to execute
     * @return The result of the block
     */
    inline fun <T> withSecureCleanup(vararg arrays: ByteArray?, block: () -> T): T {
        return try {
            block()
        } finally {
            arrays.forEach { clear(it) }
        }
    }
    
    /**
     * Creates a defensive copy of sensitive data.
     * 
     * Useful when you need to work with a copy of sensitive data to prevent
     * external modification of the original. The caller is responsible for
     * clearing the copy when done using the clear() method.
     * 
     * @param original The original data to copy
     * @return A new array containing a copy of the original data
     */
    @JvmStatic
    fun defensiveCopy(original: ByteArray): ByteArray {
        return original.copyOf()
    }
    
    /**
     * Compares two byte arrays in constant time to prevent timing attacks.
     * 
     * Standard array comparison can leak information about the data through
     * timing differences. This method takes the same time regardless of where
     * arrays differ, making it suitable for comparing cryptographic values
     * like hashes or secrets.
     * 
     * @param a First array to compare
     * @param b Second array to compare
     * @return true if arrays are equal (same length and content), false otherwise
     */
    @JvmStatic
    fun constantTimeEquals(a: ByteArray?, b: ByteArray?): Boolean {
        if (a === b) return true
        if (a == null || b == null) return false
        if (a.size != b.size) return false
        
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        
        return result == 0
    }
    
    /**
     * Wrapper class for sensitive byte arrays that ensures cleanup.
     * 
     * Implements AutoCloseable so it can be used with try-with-resources.
     */
    class SecureByteArray(private val data: ByteArray) : AutoCloseable {
        
        @Volatile
        private var cleared = false
        
        /**
         * Get the underlying data. Throws if already cleared.
         * 
         * @return The wrapped byte array
         * @throws IllegalStateException if the data has been cleared
         */
        fun getData(): ByteArray {
            check(!cleared) { "SecureByteArray has been cleared" }
            return data
        }
        
        /**
         * Clear the data and mark as cleared.
         * 
         * Can be called multiple times safely. After calling close(),
         * getData() will throw IllegalStateException.
         */
        override fun close() {
            if (!cleared) {
                clear(data)
                cleared = true
            }
        }
        
        protected fun finalize() {
            // Fallback cleanup if not properly closed
            close()
        }
    }
}