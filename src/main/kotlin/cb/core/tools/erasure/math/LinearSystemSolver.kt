package cb.core.tools.erasure.math

/**
 * Solves the Reed-Solomon decoding problem using linear system solving in GF(256).
 * This handles the polynomial division encoding by solving for missing data values
 * that produce the observed parity values.
 */
class LinearSystemSolver {
    
    /**
     * Solve for missing data values given available data and parity constraints.
     * 
     * @param availableData Map of data position to value
     * @param availableParity Map of parity position to value
     * @param missingDataIndices List of missing data positions
     * @param dataShards Total number of data shards
     * @param parityShards Total number of parity shards
     * @return Array of data values or null if no solution exists
     */
    fun solve(
        availableData: Map<Int, Int>,
        availableParity: Map<Int, Int>,
        missingDataIndices: List<Int>,
        dataShards: Int,
        parityShards: Int
    ): IntArray? {
        if (missingDataIndices.size > availableParity.size) {
            return null // Not enough constraints
        }
        
        val generator = PolynomialMath.generateGenerator(parityShards)
        
        // Build system of linear equations
        // For each available parity, we have one equation
        val n = missingDataIndices.size
        val matrix = Array(n) { IntArray(n) }
        val constants = IntArray(n)
        
        // For each parity constraint
        var row = 0
        for ((parityIndex, parityValue) in availableParity) {
            if (row >= n) break
            
            // Calculate the contribution from known data values
            var knownContribution = 0
            
            // Simulate polynomial division to find coefficients
            // Create a test polynomial with only known values
            val testData = IntArray(dataShards)
            for ((pos, value) in availableData) {
                testData[pos] = value
            }
            
            // Calculate parity with known values only
            val knownParity = PolynomialMath.encode(testData, generator)
            knownContribution = knownParity[parityIndex]
            
            // Now find coefficients for unknown values
            for (col in 0 until n) {
                val pos = missingDataIndices[col]
                val unitData = IntArray(dataShards)
                unitData[pos] = 1
                val unitParity = PolynomialMath.encode(unitData, generator)
                matrix[row][col] = unitParity[parityIndex]
            }
            
            // The equation is: sum(coeff[i] * unknown[i]) = parityValue - knownContribution
            constants[row] = GaloisField.subtract(parityValue, knownContribution)
            row++
        }
        
        // Solve the linear system
        val solution = solveLinearSystem(matrix, constants)
        if (solution == null) {
            return null
        }
        
        // Reconstruct full data array
        val result = IntArray(dataShards)
        for ((pos, value) in availableData) {
            result[pos] = value
        }
        for (i in missingDataIndices.indices) {
            result[missingDataIndices[i]] = solution[i]
        }
        
        // Verify the solution
        val computedParity = PolynomialMath.encode(result, generator)
        for ((parityIndex, expectedValue) in availableParity) {
            if (computedParity[parityIndex] != expectedValue) {
                return null // Solution doesn't match constraints
            }
        }
        
        return result
    }
    
    /**
     * Solve a system of linear equations in GF(256) using Gaussian elimination.
     */
    private fun solveLinearSystem(matrix: Array<IntArray>, constants: IntArray): IntArray? {
        val n = matrix.size
        if (n == 0 || matrix[0].size != n || constants.size != n) {
            return null
        }
        
        // Create augmented matrix
        val augmented = Array(n) { i ->
            matrix[i].copyOf(n + 1).also { it[n] = constants[i] }
        }
        
        // Forward elimination
        for (col in 0 until n) {
            // Find pivot
            var pivotRow = -1
            for (row in col until n) {
                if (augmented[row][col] != 0) {
                    pivotRow = row
                    break
                }
            }
            
            if (pivotRow == -1) {
                return null // No unique solution
            }
            
            // Swap rows
            if (pivotRow != col) {
                val temp = augmented[col]
                augmented[col] = augmented[pivotRow]
                augmented[pivotRow] = temp
            }
            
            // Scale pivot row
            val pivot = augmented[col][col]
            if (pivot != 1) {
                val pivotInv = GaloisField.inverse(pivot)
                for (j in col..n) {
                    augmented[col][j] = GaloisField.multiply(augmented[col][j], pivotInv)
                }
            }
            
            // Eliminate column
            for (row in 0 until n) {
                if (row != col && augmented[row][col] != 0) {
                    val factor = augmented[row][col]
                    for (j in col..n) {
                        augmented[row][j] = GaloisField.add(
                            augmented[row][j],
                            GaloisField.multiply(factor, augmented[col][j])
                        )
                    }
                }
            }
        }
        
        // Extract solution
        return IntArray(n) { i -> augmented[i][n] }
    }
}