package cb.core.tools.erasure.math

object PolynomialMath {
    
    fun generateGenerator(parityShards: Int): IntArray {
        require(parityShards > 0) { "Number of parity shards must be positive" }
        require(parityShards < 256) { "Number of parity shards must be less than 256" }
        
        var generator = intArrayOf(1)
        
        for (i in 0 until parityShards) {
            val root = GaloisField.exp(i)
            generator = multiplyByMonomial(generator, root)
        }
        
        return generator
    }
    
    private fun multiplyByMonomial(polynomial: IntArray, root: Int): IntArray {
        val result = IntArray(polynomial.size + 1)
        
        for (i in polynomial.indices) {
            result[i] = GaloisField.add(result[i], GaloisField.multiply(polynomial[i], root))
            result[i + 1] = GaloisField.add(result[i + 1], polynomial[i])
        }
        
        return result
    }
    
    fun encode(data: IntArray, generator: IntArray): IntArray {
        val paddedData = IntArray(data.size + generator.size - 1)
        System.arraycopy(data, 0, paddedData, generator.size - 1, data.size)
        
        for (i in data.indices) {
            val coefficient = paddedData[i + generator.size - 1]
            if (coefficient != 0) {
                for (j in generator.indices) {
                    paddedData[i + j] = GaloisField.subtract(
                        paddedData[i + j],
                        GaloisField.multiply(generator[j], coefficient)
                    )
                }
            }
        }
        
        val parity = IntArray(generator.size - 1)
        System.arraycopy(paddedData, 0, parity, 0, generator.size - 1)
        return parity
    }
    
    fun decode(shards: Array<IntArray?>, erasures: IntArray, dataShards: Int, parityShards: Int): IntArray? {
        val totalShards = dataShards + parityShards
        require(shards.size == totalShards) { "Shards array size must equal total shards" }
        require(erasures.size <= parityShards) { "Too many erasures to recover" }
        
        if (erasures.isEmpty()) {
            return combineDataShards(shards, dataShards)
        }
        
        val matrix = createVandermondeMatrix(totalShards, dataShards)
        val reducedMatrix = removeErasedRows(matrix, erasures)
        val invertedMatrix = invertMatrix(reducedMatrix) ?: return null
        
        val receivedData = collectReceivedData(shards, erasures, dataShards)
        return multiplyMatrixVector(invertedMatrix, receivedData)
    }
    
    private fun combineDataShards(shards: Array<IntArray?>, dataShards: Int): IntArray {
        val shardSize = shards[0]?.size ?: 0
        val result = IntArray(dataShards * shardSize)
        
        for (i in 0 until dataShards) {
            val shard = shards[i] ?: continue
            System.arraycopy(shard, 0, result, i * shardSize, shard.size)
        }
        
        return result
    }
    
    private fun createVandermondeMatrix(totalShards: Int, dataShards: Int): Array<IntArray> {
        val matrix = Array(totalShards) { IntArray(dataShards) }
        
        for (i in 0 until totalShards) {
            for (j in 0 until dataShards) {
                matrix[i][j] = GaloisField.power(GaloisField.exp(i), j)
            }
        }
        
        return matrix
    }
    
    private fun removeErasedRows(matrix: Array<IntArray>, erasures: IntArray): Array<IntArray> {
        val erasureSet = erasures.toSet()
        return matrix.filterIndexed { index, _ -> index !in erasureSet }.toTypedArray()
    }
    
    private fun invertMatrix(matrix: Array<IntArray>): Array<IntArray>? {
        val n = matrix.size
        require(matrix.all { it.size == n }) { "Matrix must be square" }
        
        val augmented = Array(n) { i ->
            IntArray(2 * n) { j ->
                when {
                    j < n -> matrix[i][j]
                    j == i + n -> 1
                    else -> 0
                }
            }
        }
        
        for (i in 0 until n) {
            var pivot = i
            for (j in i + 1 until n) {
                if (augmented[j][i] != 0) {
                    pivot = j
                    break
                }
            }
            
            if (augmented[pivot][i] == 0) return null
            
            if (pivot != i) {
                val temp = augmented[i]
                augmented[i] = augmented[pivot]
                augmented[pivot] = temp
            }
            
            val pivotValue = augmented[i][i]
            val pivotInverse = GaloisField.inverse(pivotValue)
            
            for (j in 0 until 2 * n) {
                augmented[i][j] = GaloisField.multiply(augmented[i][j], pivotInverse)
            }
            
            for (j in 0 until n) {
                if (j != i && augmented[j][i] != 0) {
                    val factor = augmented[j][i]
                    for (k in 0 until 2 * n) {
                        augmented[j][k] = GaloisField.subtract(
                            augmented[j][k],
                            GaloisField.multiply(factor, augmented[i][k])
                        )
                    }
                }
            }
        }
        
        return Array(n) { i ->
            IntArray(n) { j ->
                augmented[i][j + n]
            }
        }
    }
    
    private fun collectReceivedData(shards: Array<IntArray?>, erasures: IntArray, dataShards: Int): IntArray {
        val erasureSet = erasures.toSet()
        val receivedShards = mutableListOf<IntArray>()
        
        for (i in shards.indices) {
            if (i !in erasureSet && shards[i] != null) {
                receivedShards.add(shards[i]!!)
            }
        }
        
        require(receivedShards.size >= dataShards) { "Not enough shards to reconstruct data" }
        
        val shardSize = receivedShards[0].size
        val result = IntArray(dataShards * shardSize)
        
        for (i in 0 until minOf(dataShards, receivedShards.size)) {
            System.arraycopy(receivedShards[i], 0, result, i * shardSize, shardSize)
        }
        
        return result
    }
    
    private fun multiplyMatrixVector(matrix: Array<IntArray>, vector: IntArray): IntArray {
        val result = IntArray(matrix.size)
        
        for (i in matrix.indices) {
            var sum = 0
            for (j in matrix[i].indices) {
                if (j < vector.size) {
                    sum = GaloisField.add(sum, GaloisField.multiply(matrix[i][j], vector[j]))
                }
            }
            result[i] = sum
        }
        
        return result
    }
    
    fun interpolate(points: List<Pair<Int, Int>>): IntArray {
        val n = points.size
        require(n > 0) { "At least one point is required for interpolation" }
        
        val xValues = points.map { it.first }.toIntArray()
        val yValues = points.map { it.second }.toIntArray()
        
        var result = intArrayOf(yValues[0])
        
        for (i in 1 until n) {
            var term = intArrayOf(yValues[i])
            
            for (j in 0 until i) {
                val denominator = GaloisField.subtract(xValues[i], xValues[j])
                val factor = GaloisField.divide(1, denominator)
                
                term = GaloisField.multiplyPolynomial(term, intArrayOf(GaloisField.multiply(-xValues[j], factor), factor))
            }
            
            result = addPolynomials(result, term)
        }
        
        return result
    }
    
    private fun addPolynomials(poly1: IntArray, poly2: IntArray): IntArray {
        val maxSize = maxOf(poly1.size, poly2.size)
        val result = IntArray(maxSize)
        
        for (i in 0 until maxSize) {
            val a = if (i < poly1.size) poly1[i] else 0
            val b = if (i < poly2.size) poly2[i] else 0
            result[i] = GaloisField.add(a, b)
        }
        
        return result
    }
}