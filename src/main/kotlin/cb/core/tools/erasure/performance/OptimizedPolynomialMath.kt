package cb.core.tools.erasure.performance

import cb.core.tools.erasure.math.GaloisField
import java.util.concurrent.ConcurrentHashMap

object OptimizedPolynomialMath {
    
    // Cache for generator polynomials
    private val generatorCache = ConcurrentHashMap<Int, IntArray>()
    
    // Cache for encoding matrices
    private val encodingMatrixCache = ConcurrentHashMap<Pair<Int, Int>, Array<IntArray>>()
    
    // Pre-computed powers for common shard counts
    private val powerCache = Array(256) { i ->
        IntArray(256) { j ->
            GaloisField.power(i, j)
        }
    }
    
    fun generateGenerator(parityShards: Int): IntArray {
        return generatorCache.computeIfAbsent(parityShards) {
            var generator = intArrayOf(1)
            
            for (i in 0 until parityShards) {
                val root = GaloisField.exp(i)
                generator = multiplyByMonomial(generator, root)
            }
            
            generator
        }
    }
    
    private fun multiplyByMonomial(polynomial: IntArray, root: Int): IntArray {
        val result = IntArray(polynomial.size + 1)
        
        for (i in polynomial.indices) {
            result[i] = GaloisField.add(result[i], GaloisField.multiply(polynomial[i], root))
            result[i + 1] = GaloisField.add(result[i + 1], polynomial[i])
        }
        
        return result
    }
    
    // Optimized encoding using matrix multiplication
    fun encodeMatrix(dataShards: Int, parityShards: Int): Array<IntArray> {
        return encodingMatrixCache.computeIfAbsent(Pair(dataShards, parityShards)) {
            createEncodingMatrix(dataShards, parityShards)
        }
    }
    
    private fun createEncodingMatrix(dataShards: Int, parityShards: Int): Array<IntArray> {
        val matrix = Array(parityShards) { IntArray(dataShards) }
        
        // Create Vandermonde matrix for systematic Reed-Solomon
        for (row in 0 until parityShards) {
            for (col in 0 until dataShards) {
                matrix[row][col] = powerCache[col + 1][row]
            }
        }
        
        return matrix
    }
    
    // Batch encoding for multiple data arrays
    fun encodeBatch(dataArrays: List<IntArray>, encodingMatrix: Array<IntArray>): List<IntArray> {
        val parityShards = encodingMatrix.size
        val results = ArrayList<IntArray>(dataArrays.size)
        
        for (data in dataArrays) {
            val parity = IntArray(parityShards)
            
            // Matrix multiplication with loop unrolling
            for (row in 0 until parityShards) {
                var sum = 0
                val matrixRow = encodingMatrix[row]
                
                // Unroll by 4 for better performance
                var col = 0
                while (col + 3 < data.size) {
                    sum = GaloisField.add(sum, GaloisField.multiply(matrixRow[col], data[col]))
                    sum = GaloisField.add(sum, GaloisField.multiply(matrixRow[col + 1], data[col + 1]))
                    sum = GaloisField.add(sum, GaloisField.multiply(matrixRow[col + 2], data[col + 2]))
                    sum = GaloisField.add(sum, GaloisField.multiply(matrixRow[col + 3], data[col + 3]))
                    col += 4
                }
                
                // Handle remaining elements
                while (col < data.size) {
                    sum = GaloisField.add(sum, GaloisField.multiply(matrixRow[col], data[col]))
                    col++
                }
                
                parity[row] = sum
            }
            
            results.add(parity)
        }
        
        return results
    }
    
    // Optimized encoding for specific shard counts
    fun encodeOptimized20Shards(data: IntArray, dataShards: Int, parityShards: Int): IntArray {
        require(dataShards + parityShards == 20) { "This method is optimized for exactly 20 total shards" }
        
        val parity = IntArray(parityShards)
        
        // Use pre-computed matrix
        val matrix = encodeMatrix(dataShards, parityShards)
        
        // Parallel computation for large parity counts
        if (parityShards >= 8 && data.size >= 8) {
            // Process in chunks of 8 for SIMD-like behavior
            for (row in 0 until parityShards) {
                var sum = 0
                val matrixRow = matrix[row]
                
                // Process 8 elements at a time
                var col = 0
                while (col + 7 < data.size) {
                    sum = GaloisField.add(sum, GaloisField.multiply(matrixRow[col], data[col]))
                    sum = GaloisField.add(sum, GaloisField.multiply(matrixRow[col + 1], data[col + 1]))
                    sum = GaloisField.add(sum, GaloisField.multiply(matrixRow[col + 2], data[col + 2]))
                    sum = GaloisField.add(sum, GaloisField.multiply(matrixRow[col + 3], data[col + 3]))
                    sum = GaloisField.add(sum, GaloisField.multiply(matrixRow[col + 4], data[col + 4]))
                    sum = GaloisField.add(sum, GaloisField.multiply(matrixRow[col + 5], data[col + 5]))
                    sum = GaloisField.add(sum, GaloisField.multiply(matrixRow[col + 6], data[col + 6]))
                    sum = GaloisField.add(sum, GaloisField.multiply(matrixRow[col + 7], data[col + 7]))
                    col += 8
                }
                
                // Handle remaining elements
                while (col < data.size) {
                    sum = GaloisField.add(sum, GaloisField.multiply(matrixRow[col], data[col]))
                    col++
                }
                
                parity[row] = sum
            }
        } else {
            // Fallback to standard matrix multiplication
            for (row in 0 until parityShards) {
                var sum = 0
                for (col in 0 until data.size) {
                    sum = GaloisField.add(sum, GaloisField.multiply(matrix[row][col], data[col]))
                }
                parity[row] = sum
            }
        }
        
        return parity
    }
    
    // Clear caches if memory is a concern
    fun clearCaches() {
        generatorCache.clear()
        encodingMatrixCache.clear()
    }
}