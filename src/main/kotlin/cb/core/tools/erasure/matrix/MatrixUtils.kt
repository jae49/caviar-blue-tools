package cb.core.tools.erasure.matrix

import cb.core.tools.erasure.math.GaloisField
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlin.math.min

/**
 * Utility class for matrix operations in GF(256).
 * 
 * This class provides the mathematical foundation for systematic Reed-Solomon encoding
 * using Vandermonde matrices. All operations are performed in the finite field GF(256).
 * 
 * Phase 3 optimizations include:
 * - Matrix caching for common configurations
 * - Parallel processing for large matrices
 * - Optimized matrix-vector multiplication
 * - Cauchy matrix generation as an alternative
 */
object MatrixUtils {
    
    // Cache for commonly used matrices
    private val matrixCache = ConcurrentHashMap<MatrixCacheKey, Array<IntArray>>()
    private const val MAX_CACHE_SIZE = 100
    
    // Block size for cache-efficient operations
    private const val BLOCK_SIZE = 64
    
    // Threshold for using parallel processing
    private const val PARALLEL_THRESHOLD = 16
    
    private data class MatrixCacheKey(
        val k: Int,
        val n: Int,
        val type: MatrixType
    )
    
    private enum class MatrixType {
        VANDERMONDE,
        CAUCHY
    }
    
    /**
     * Generates a Vandermonde matrix for Reed-Solomon encoding.
     * 
     * The Vandermonde matrix has the form:
     * ```
     * [1  1  1  ... 1  ]
     * [1  α  α² ... α^(k-1)]
     * [1  α² α⁴ ... α^(2(k-1))]
     * ...
     * [1  α^(n-1) α^(2(n-1)) ... α^((n-1)(k-1))]
     * ```
     * 
     * @param k Number of data shards (columns)
     * @param n Total number of shards (rows)
     * @return n×k Vandermonde matrix
     */
    fun generateVandermondeMatrix(k: Int, n: Int): Array<IntArray> {
        require(k > 0) { "k must be positive" }
        require(n >= k) { "n must be at least k" }
        require(n <= 256) { "n must be at most 256 for GF(256)" }
        
        // Check cache first
        val cacheKey = MatrixCacheKey(k, n, MatrixType.VANDERMONDE)
        matrixCache[cacheKey]?.let { return deepCopy(it) }
        
        val matrix = Array(n) { IntArray(k) }
        
        // Use parallel processing for large matrices
        if (n >= PARALLEL_THRESHOLD) {
            runBlocking {
                val jobs = mutableListOf<Job>()
                for (i in 0 until n) {
                    jobs.add(launch(Dispatchers.Default) {
                        for (j in 0 until k) {
                            val alpha = i + 1
                            matrix[i][j] = GaloisField.power(alpha, j)
                        }
                    })
                }
                jobs.joinAll()
            }
        } else {
            for (i in 0 until n) {
                for (j in 0 until k) {
                    val alpha = i + 1
                    matrix[i][j] = GaloisField.power(alpha, j)
                }
            }
        }
        
        // Cache the result if cache size permits
        if (matrixCache.size < MAX_CACHE_SIZE) {
            matrixCache[cacheKey] = deepCopy(matrix)
        }
        
        return matrix
    }
    
    /**
     * Generates a Cauchy matrix as an alternative to Vandermonde.
     * 
     * Cauchy matrices have better numerical properties and can be more efficient
     * for certain configurations. The matrix has the form:
     * C[i,j] = 1 / (xi + yj) in GF(256)
     * 
     * @param k Number of data shards (columns)
     * @param n Total number of shards (rows)
     * @return n×k Cauchy matrix
     */
    fun generateCauchyMatrix(k: Int, n: Int): Array<IntArray> {
        require(k > 0) { "k must be positive" }
        require(n >= k) { "n must be at least k" }
        require(n + k <= 256) { "n + k must be at most 256 for GF(256)" }
        
        // Check cache first
        val cacheKey = MatrixCacheKey(k, n, MatrixType.CAUCHY)
        matrixCache[cacheKey]?.let { return deepCopy(it) }
        
        val matrix = Array(n) { IntArray(k) }
        
        // Generate distinct x and y values
        val x = IntArray(n) { it }
        val y = IntArray(k) { n + it }
        
        // Generate Cauchy matrix
        for (i in 0 until n) {
            for (j in 0 until k) {
                val denominator = GaloisField.add(x[i], y[j])
                matrix[i][j] = if (denominator == 0) 0 else GaloisField.inverse(denominator)
            }
        }
        
        // Cache the result
        if (matrixCache.size < MAX_CACHE_SIZE) {
            matrixCache[cacheKey] = deepCopy(matrix)
        }
        
        return matrix
    }
    
    /**
     * Inverts a square matrix in GF(256) using Gaussian elimination.
     * 
     * @param matrix Square matrix to invert
     * @return Inverted matrix, or null if the matrix is singular
     */
    fun invertMatrix(matrix: Array<IntArray>): Array<IntArray>? {
        val n = matrix.size
        require(n > 0) { "Matrix cannot be empty" }
        require(matrix.all { it.size == n }) { "Matrix must be square" }
        
        // Create augmented matrix [A | I]
        val augmented = Array(n) { i ->
            IntArray(2 * n) { j ->
                if (j < n) {
                    matrix[i][j]
                } else {
                    if (i == j - n) 1 else 0
                }
            }
        }
        
        // Gaussian elimination with partial pivoting
        for (col in 0 until n) {
            // Find pivot
            var pivotRow = -1
            for (row in col until n) {
                if (augmented[row][col] != 0) {
                    pivotRow = row
                    break
                }
            }
            
            // Matrix is singular
            if (pivotRow == -1) {
                return null
            }
            
            // Swap rows if necessary
            if (pivotRow != col) {
                val temp = augmented[col]
                augmented[col] = augmented[pivotRow]
                augmented[pivotRow] = temp
            }
            
            // Scale pivot row
            val pivot = augmented[col][col]
            val pivotInv = GaloisField.inverse(pivot)
            for (j in 0 until 2 * n) {
                augmented[col][j] = GaloisField.multiply(augmented[col][j], pivotInv)
            }
            
            // Eliminate column
            for (row in 0 until n) {
                if (row != col && augmented[row][col] != 0) {
                    val factor = augmented[row][col]
                    for (j in 0 until 2 * n) {
                        augmented[row][j] = GaloisField.add(
                            augmented[row][j],
                            GaloisField.multiply(factor, augmented[col][j])
                        )
                    }
                }
            }
        }
        
        // Extract the inverse matrix from the right half
        return Array(n) { i ->
            IntArray(n) { j ->
                augmented[i][j + n]
            }
        }
    }
    
    /**
     * Multiplies a matrix by a vector in GF(256).
     * 
     * @param matrix m×n matrix
     * @param vector n-dimensional vector
     * @return m-dimensional result vector
     */
    fun multiplyMatrixVector(matrix: Array<IntArray>, vector: IntArray): IntArray {
        require(matrix.isNotEmpty()) { "Matrix cannot be empty" }
        require(matrix[0].size == vector.size) { 
            "Matrix columns (${matrix[0].size}) must match vector size (${vector.size})" 
        }
        
        val m = matrix.size
        val n = vector.size
        val result = IntArray(m)
        
        // Use parallel processing for large matrices
        if (m >= PARALLEL_THRESHOLD) {
            runBlocking {
                val jobs = mutableListOf<Job>()
                for (i in 0 until m) {
                    jobs.add(launch(Dispatchers.Default) {
                        result[i] = multiplyRowVector(matrix[i], vector)
                    })
                }
                jobs.joinAll()
            }
        } else {
            // Optimized sequential version with loop unrolling
            for (i in 0 until m) {
                result[i] = multiplyRowVector(matrix[i], vector)
            }
        }
        
        return result
    }
    
    /**
     * Optimized row-vector multiplication with loop unrolling.
     */
    private fun multiplyRowVector(row: IntArray, vector: IntArray): Int {
        var sum = 0
        val n = vector.size
        
        // Process 4 elements at a time for better performance
        var j = 0
        while (j + 3 < n) {
            sum = GaloisField.add(sum, GaloisField.multiply(row[j], vector[j]))
            sum = GaloisField.add(sum, GaloisField.multiply(row[j + 1], vector[j + 1]))
            sum = GaloisField.add(sum, GaloisField.multiply(row[j + 2], vector[j + 2]))
            sum = GaloisField.add(sum, GaloisField.multiply(row[j + 3], vector[j + 3]))
            j += 4
        }
        
        // Process remaining elements
        while (j < n) {
            sum = GaloisField.add(sum, GaloisField.multiply(row[j], vector[j]))
            j++
        }
        
        return sum
    }
    
    /**
     * Block-wise matrix-vector multiplication for better cache locality.
     * 
     * @param matrix m×n matrix
     * @param vector n-dimensional vector
     * @return m-dimensional result vector
     */
    fun multiplyMatrixVectorBlocked(matrix: Array<IntArray>, vector: IntArray): IntArray {
        require(matrix.isNotEmpty()) { "Matrix cannot be empty" }
        require(matrix[0].size == vector.size) { 
            "Matrix columns (${matrix[0].size}) must match vector size (${vector.size})" 
        }
        
        val m = matrix.size
        val n = vector.size
        val result = IntArray(m)
        
        // Process in blocks for better cache performance
        for (i0 in 0 until m step BLOCK_SIZE) {
            val iMax = min(i0 + BLOCK_SIZE, m)
            for (j0 in 0 until n step BLOCK_SIZE) {
                val jMax = min(j0 + BLOCK_SIZE, n)
                
                // Process block
                for (i in i0 until iMax) {
                    var sum = result[i]
                    for (j in j0 until jMax) {
                        sum = GaloisField.add(sum, GaloisField.multiply(matrix[i][j], vector[j]))
                    }
                    result[i] = sum
                }
            }
        }
        
        return result
    }
    
    /**
     * Extracts a submatrix from the given matrix using specified row indices.
     * 
     * @param matrix Original matrix
     * @param rowIndices List of row indices to extract
     * @return Submatrix containing only the specified rows
     */
    fun extractSubmatrix(matrix: Array<IntArray>, rowIndices: List<Int>): Array<IntArray> {
        require(rowIndices.isNotEmpty()) { "Row indices cannot be empty" }
        require(rowIndices.all { it in matrix.indices }) { "Invalid row indices" }
        
        return Array(rowIndices.size) { i ->
            matrix[rowIndices[i]].clone()
        }
    }
    
    /**
     * Deep copy a matrix to avoid cache mutation.
     */
    private fun deepCopy(matrix: Array<IntArray>): Array<IntArray> {
        return Array(matrix.size) { i ->
            matrix[i].clone()
        }
    }
    
    /**
     * Clears the matrix cache.
     */
    fun clearCache() {
        matrixCache.clear()
    }
    
    /**
     * Pre-populates the cache with common configurations.
     * 
     * @param configurations List of (k, n) pairs to pre-cache
     */
    fun prePopulateCache(configurations: List<Pair<Int, Int>>) {
        for ((k, n) in configurations) {
            if (matrixCache.size >= MAX_CACHE_SIZE) break
            
            // Generate and cache both Vandermonde and Cauchy matrices
            generateVandermondeMatrix(k, n)
            if (n + k <= 256) {
                generateCauchyMatrix(k, n)
            }
        }
    }
    
    /**
     * Multiplies two matrices in GF(256).
     * 
     * @param a m×n matrix
     * @param b n×p matrix
     * @return m×p result matrix
     */
    fun multiplyMatrices(a: Array<IntArray>, b: Array<IntArray>): Array<IntArray> {
        require(a.isNotEmpty() && b.isNotEmpty()) { "Matrices cannot be empty" }
        require(a[0].size == b.size) { 
            "Matrix A columns (${a[0].size}) must match matrix B rows (${b.size})" 
        }
        
        val m = a.size
        val n = a[0].size
        val p = b[0].size
        val result = Array(m) { IntArray(p) }
        
        // Use blocked multiplication for better cache performance
        for (i0 in 0 until m step BLOCK_SIZE) {
            val iMax = min(i0 + BLOCK_SIZE, m)
            for (j0 in 0 until p step BLOCK_SIZE) {
                val jMax = min(j0 + BLOCK_SIZE, p)
                for (k0 in 0 until n step BLOCK_SIZE) {
                    val kMax = min(k0 + BLOCK_SIZE, n)
                    
                    // Process block
                    for (i in i0 until iMax) {
                        for (j in j0 until jMax) {
                            var sum = result[i][j]
                            for (k in k0 until kMax) {
                                sum = GaloisField.add(sum, 
                                    GaloisField.multiply(a[i][k], b[k][j]))
                            }
                            result[i][j] = sum
                        }
                    }
                }
            }
        }
        
        return result
    }
}