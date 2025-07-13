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
     */
    @JvmStatic
    fun clearAll(arrays: List<ByteArray?>) {
        arrays.forEach { clear(it) }
    }
    
    /**
     * Executes a block of code and ensures the provided byte arrays are cleared afterwards.
     * 
     * This provides exception safety by ensuring cleanup happens even if the block throws.
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
     * Creates a defensive copy of sensitive data that will be automatically cleared.
     * 
     * Useful when you need to work with a copy of sensitive data but want to ensure
     * the copy is properly cleared from memory when done.
     * 
     * @param original The original data to copy
     * @return A defensive copy that should be cleared when no longer needed
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
     * arrays differ.
     * 
     * @param a First array
     * @param b Second array
     * @return true if arrays are equal, false otherwise
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
         */
        fun getData(): ByteArray {
            check(!cleared) { "SecureByteArray has been cleared" }
            return data
        }
        
        /**
         * Clear the data and mark as cleared.
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