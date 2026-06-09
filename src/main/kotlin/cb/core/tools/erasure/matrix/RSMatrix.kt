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

package cb.core.tools.erasure.matrix

import cb.core.tools.erasure.math.GaloisField
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for the systematic Reed-Solomon generator matrix.
 *
 * Every encoder and the decoder MUST build their coefficients here so they agree.
 *
 * The parity block is a **Cauchy matrix**, which guarantees the Maximum Distance
 * Separable (MDS) property: every square submatrix of a Cauchy matrix is
 * non-singular, so every k×k submatrix of the full `[I | parity]` generator is
 * invertible. That is exactly what makes "recover from ANY k of n shards" true.
 *
 * A plain stacked Vandermonde (`[I | V]`) does **not** have this property over
 * GF(256): some generalized-Vandermonde minors vanish, so certain erasure
 * patterns (e.g. specific 8+6 and 10+6 combinations) cannot be reconstructed.
 */
object RSMatrix {

    private val parityCache = ConcurrentHashMap<Long, Array<IntArray>>()

    /**
     * The `parityShards × dataShards` Cauchy parity matrix.
     *
     * `C[i][j] = 1 / (x_i XOR y_j)` in GF(256) with `x_i = i` (i in `[0, m)`) and
     * `y_j = m + j` (j in `[0, k)`). The x-set and y-set are disjoint, so every
     * denominator is non-zero and every minor is non-singular.
     */
    fun parityMatrix(dataShards: Int, parityShards: Int): Array<IntArray> {
        require(dataShards > 0) { "dataShards must be positive" }
        require(parityShards > 0) { "parityShards must be positive" }
        require(dataShards + parityShards <= 256) {
            "dataShards + parityShards must be <= 256 for GF(256)"
        }
        val key = (dataShards.toLong() shl 32) or parityShards.toLong()
        return parityCache.getOrPut(key) {
            Array(parityShards) { i ->
                IntArray(dataShards) { j ->
                    GaloisField.inverse(i xor (parityShards + j))
                }
            }
        }.let { cached -> Array(cached.size) { cached[it].clone() } }
    }

    /**
     * The full `totalShards × dataShards` systematic generator: the first
     * `dataShards` rows are the identity matrix (systematic data shards) and the
     * remaining rows are the Cauchy parity matrix.
     */
    fun generator(dataShards: Int, totalShards: Int): Array<IntArray> {
        require(totalShards > dataShards) { "totalShards must exceed dataShards" }
        val parityShards = totalShards - dataShards
        val parity = parityMatrix(dataShards, parityShards)
        return Array(totalShards) { i ->
            if (i < dataShards) {
                IntArray(dataShards) { j -> if (i == j) 1 else 0 }
            } else {
                parity[i - dataShards]
            }
        }
    }
}
