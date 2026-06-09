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

import cb.core.tools.erasure.ReedSolomonEncoder
import cb.core.tools.erasure.ReedSolomonDecoder
import cb.core.tools.erasure.models.EncodingConfig
import cb.core.tools.erasure.models.ReconstructionResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.Random

/**
 * Guards the MDS ("recover from ANY k of n") property that the rest of the library
 * advertises. The previous stacked-Vandermonde generator was NOT MDS and failed on
 * specific erasure patterns (e.g. 20 of the 3003 8+6 subsets); the Cauchy-based
 * [RSMatrix] is. These tests fail loudly if that ever regresses.
 */
class RSMatrixMdsTest {

    private val configs = listOf(
        4 to 2,
        5 to 5,
        6 to 4,
        8 to 6,   // the flagship 8+6 case that the old matrix failed
        10 to 6,
        16 to 4,
    )

    @Test
    fun `every k-subset of the generator is invertible`() {
        for ((k, p) in configs) {
            val n = k + p
            val gen = RSMatrix.generator(k, n)
            var subsets = 0
            forEachKSubset(n, k) { rows ->
                subsets++
                val sub = MatrixUtils.extractSubmatrix(gen, rows)
                assertNotNull(
                    MatrixUtils.invertMatrix(sub),
                    "Singular k-subset $rows for k=$k p=$p — generator is not MDS",
                )
            }
            println("k=$k p=$p: all $subsets k-subsets invertible (MDS ✓)")
        }
    }

    @Test
    fun `data round-trips from every k-subset of shards`() {
        val rnd = Random(1234)
        // Keep the end-to-end sweep to the most important configurations; the
        // matrix-level test above already covers invertibility broadly.
        for ((k, p) in listOf(4 to 2, 6 to 4, 8 to 6)) {
            val n = k + p
            val config = EncodingConfig(dataShards = k, parityShards = p, shardSize = 16)
            val data = ByteArray(k * 16).also { rnd.nextBytes(it) }
            val shards = ReedSolomonEncoder().encode(data, config)
            val decoder = ReedSolomonDecoder()

            forEachKSubset(n, k) { rows ->
                val kept = rows.map { shards[it] }
                val result = decoder.decode(kept)
                assertTrue(result is ReconstructionResult.Success) {
                    "Decode failed for k=$k p=$p keeping shards $rows"
                }
                assertArrayEquals(data, (result as ReconstructionResult.Success).data)
            }
        }
    }

    /** Invokes [action] with each size-[k] subset (as a sorted index list) of `0 until n`. */
    private fun forEachKSubset(n: Int, k: Int, action: (List<Int>) -> Unit) {
        val chosen = ArrayList<Int>(k)
        fun recurse(start: Int) {
            if (chosen.size == k) { action(chosen.toList()); return }
            for (i in start until n) {
                chosen.add(i); recurse(i + 1); chosen.removeAt(chosen.size - 1)
            }
        }
        recurse(0)
    }
}
