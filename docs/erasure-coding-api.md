# Reed-Solomon Erasure Coding API Reference

## Package: `cb.core.tools.erasure`

### Core Classes

#### ReedSolomonEncoder

Main class for encoding data into erasure-coded shards.

```kotlin
class ReedSolomonEncoder {
    fun encode(data: ByteArray, config: EncodingConfig): List<Shard>
}
```

**Parameters:**
- `data`: The input data to encode
- `config`: Configuration specifying shard counts and sizes

**Returns:** List of encoded shards (data + parity)

**Implementation:**
- Uses systematic matrix-based Reed-Solomon encoding
- Guarantees reconstruction from ANY k valid shards

**Throws:**
- `IllegalArgumentException` if data is empty

---

#### ReedSolomonDecoder

Main class for reconstructing data from available shards.

```kotlin
class ReedSolomonDecoder {
    fun decode(shards: List<Shard>): ReconstructionResult
}
```

**Parameters:**
- `shards`: Available shards (must have at least `dataShards` count)

**Returns:** `ReconstructionResult` indicating success or failure

**Implementation:**
- Uses systematic matrix-based decoding
- Can reconstruct from ANY k valid shards out of n total

---

### Models Package: `cb.core.tools.erasure.models`

#### EncodingConfig

Configuration for Reed-Solomon encoding.

```kotlin
data class EncodingConfig(
    val dataShards: Int,        // Minimum shards needed for reconstruction
    val parityShards: Int,      // Redundancy shards
    val shardSize: Int = 8192   // Bytes per shard
) {
    val totalShards: Int = dataShards + parityShards
}
```

**Parameters:**
- `dataShards`: Number of data shards (k in k-of-n)
- `parityShards`: Number of parity/redundancy shards
- `shardSize`: Size of each shard in bytes

**Constraints:**
- `dataShards > 0`
- `parityShards > 0`
- `totalShards ≤ 256`

---

#### Shard

Represents a single encoded data or parity shard.

```kotlin
data class Shard(
    val index: Int,
    val data: ByteArray,
    val metadata: ShardMetadata
) {
    val isDataShard: Boolean
    val isParityShard: Boolean
}
```

---

#### ShardMetadata

Metadata associated with each shard.

```kotlin
data class ShardMetadata(
    val originalSize: Long,
    val config: EncodingConfig,
    val checksum: String,
    val timestamp: Long = System.currentTimeMillis(),
    val chunkIndex: Int? = null
)
```

---

#### ReconstructionResult

Sealed class representing decoding outcomes.

```kotlin
sealed class ReconstructionResult {
    data class Success(val data: ByteArray) : ReconstructionResult()
    data class Failure(
        val error: ReconstructionError,
        val message: String? = null
    ) : ReconstructionResult()
    data class Partial(
        val recoveredBytes: Long,
        val totalBytes: Long
    ) : ReconstructionResult()
}
```

---

#### ReconstructionError

Enumeration of possible reconstruction failures.

```kotlin
enum class ReconstructionError {
    INSUFFICIENT_SHARDS,      // Not enough shards for reconstruction
    CORRUPTED_SHARDS,        // Checksum verification failed
    INVALID_CONFIGURATION,   // Inconsistent shard metadata
    MATH_ERROR              // Mathematical computation error
}
```

---

### Streaming Package: `cb.core.tools.erasure.stream`

#### StreamingEncoder

Encoder for processing large data streams.

```kotlin
class StreamingEncoder {
    suspend fun encodeStream(
        input: InputStream,
        config: EncodingConfig,
        bufferSize: Int = DEFAULT_BUFFER_SIZE
    ): Flow<Shard>
    
    suspend fun encodeChunked(
        input: InputStream,
        chunkSize: Int,
        config: EncodingConfig
    ): Flow<List<Shard>>
}
```

**Features:**
- Memory-efficient chunk processing
- Kotlin Flow integration
- Configurable buffer sizes

---

#### StreamingDecoder

Decoder for reconstructing data from streaming shards.

```kotlin
class StreamingDecoder {
    suspend fun decodeStream(
        shardFlow: Flow<List<Shard>>
    ): Flow<ByteArray>
}
```

---

### Performance Package: `cb.core.tools.erasure.performance`

#### OptimizedReedSolomonEncoder

High-performance encoder with parallel processing.

```kotlin
class OptimizedReedSolomonEncoder {
    fun encode(data: ByteArray, config: EncodingConfig): List<Shard>
    fun shutdown()  // Clean up thread pool
}
```

**Features:**
- Parallel chunk processing
- Optimized for common configurations
- Thread pool management
- Matrix caching for performance
- SIMD-style optimizations where applicable

---

#### RobustReedSolomonDecoder

Enhanced decoder with comprehensive validation.

```kotlin
class RobustReedSolomonDecoder {
    fun decode(shards: List<Shard>): ReconstructionResult
    fun canReconstruct(
        shards: List<Shard>,
        originalConfig: EncodingConfig
    ): Boolean
}
```

**Features:**
- Pre-validation of shard consistency
- Detailed error reporting
- Fallback recovery strategies
- Multiple matrix inversion strategies
- Alternative shard combination attempts on failure
- Enhanced diagnostics for troubleshooting

---

### Mathematical Package: `cb.core.tools.erasure.math`

#### GaloisField

Low-level Galois Field GF(256) operations.

```kotlin
object GaloisField {
    fun add(a: Int, b: Int): Int
    fun subtract(a: Int, b: Int): Int
    fun multiply(a: Int, b: Int): Int
    fun divide(a: Int, b: Int): Int
    fun power(a: Int, n: Int): Int
    fun inverse(a: Int): Int
}
```

**Note:** These are low-level operations used internally by the encoder/decoder.

---

### Matrix Package: `cb.core.tools.erasure.matrix`

#### MatrixUtils

Matrix operations for systematic Reed-Solomon encoding/decoding.

```kotlin
object MatrixUtils {
    fun generateVandermondeMatrix(k: Int, n: Int): Array<IntArray>
    fun generateCauchyMatrix(k: Int, n: Int): Array<IntArray>
    fun invertMatrix(matrix: Array<IntArray>): Array<IntArray>?
    fun multiplyMatrixVector(matrix: Array<IntArray>, vector: IntArray): IntArray
    fun extractSubmatrix(matrix: Array<IntArray>, rowIndices: List<Int>): Array<IntArray>
}
```

**Features:**
- Vandermonde and Cauchy matrix generation
- Matrix inversion using Gaussian elimination in GF(256)
- Optimized matrix-vector multiplication
- Matrix caching for common configurations

---

## Usage Patterns

### Basic Encoding/Decoding

```kotlin
// Setup
val config = EncodingConfig(dataShards = 4, parityShards = 2)
val encoder = ReedSolomonEncoder()
val decoder = ReedSolomonDecoder()

// Encode
val shards = encoder.encode(data, config)

// Decode - works with ANY 4 shards out of 6 total
when (val result = decoder.decode(availableShards)) {
    is ReconstructionResult.Success -> handleData(result.data)
    is ReconstructionResult.Failure -> handleError(result.error)
}
```

### Streaming Large Files

```kotlin
runBlocking {
    val encoder = StreamingEncoder()
    inputStream.use { input ->
        encoder.encodeStream(input, config)
            .collect { shard -> saveShard(shard) }
    }
}
```

### Performance-Critical Applications

```kotlin
val encoder = OptimizedReedSolomonEncoder()
try {
    val shards = encoder.encode(largeData, config)
    distributeShards(shards)
} finally {
    encoder.shutdown()
}
```

---

## Error Handling

All encoding operations may throw:
- `IllegalArgumentException` for invalid parameters
- `OutOfMemoryError` for excessive data sizes

Decoding operations return `ReconstructionResult.Failure` with:
- Specific error enum value
- Optional detailed error message

---

## Thread Safety

- `ReedSolomonEncoder`: Thread-safe (stateless)
- `ReedSolomonDecoder`: Thread-safe (stateless)
- `OptimizedReedSolomonEncoder`: Thread-safe with internal thread pool
- `StreamingEncoder/Decoder`: Use within appropriate coroutine scope

---

## Performance Characteristics

| Operation | Complexity | Typical Throughput |
|-----------|------------|-------------------|
| Encoding | O(n × p) | 25-175 MB/s |
| Decoding (no erasures) | O(n) | 100-400 MB/s |
| Decoding (with erasures) | O(n × k²) | 50-150 MB/s |

Where:
- n = data size
- p = parity shard count
- k = data shard count

**Note:** The systematic algorithm provides excellent parallelization opportunities and guarantees reconstruction from ANY k valid shards.