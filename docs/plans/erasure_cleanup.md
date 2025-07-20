# Erasure Coding Cleanup - Remove Polynomial Algorithm

Date: 2025-07-20

## Context

The polynomial algorithm has significant limitations:
- Cannot guarantee reconstruction from any k-out-of-n shards
- Fails with certain shard combinations (especially when missing the first shard)
- Has known issues documented in test notes
- The systematic algorithm is superior with 96% success rate

Since the library is new and no data has been encoded using the polynomial algorithm, it should be removed entirely.

## Completed Tasks

1. ✅ Removed RSAlgorithm enum from EncodingConfig.kt
2. ✅ Updated EncodingConfig to remove algorithm parameter

## Remaining Tasks

### 1. Update ReedSolomonEncoder
- Remove the algorithm check in encode() method (lines 30-32)
- Remove all polynomial-specific logic (lines 34-154)
- Simply delegate all encoding to systematicEncoder
- Remove imports that are no longer needed (PolynomialMath)

### 2. Update ReedSolomonDecoder
- Remove the algorithm check (line 38-40)
- Remove polynomial-specific reconstruction logic
- Remove reconstructWithParity() and reconstructWithParityFallback() methods
- Remove PolynomialMath imports and usage
- Simply delegate all decoding to systematicDecoder

### 3. Update SystematicRSEncoder
- Remove any references to RSAlgorithm in encode() method
- Update metadata creation to not include algorithm field

### 4. Update SystematicRSDecoder
- Remove algorithm validation (lines 31-36)

### 5. Update Tests
Tests that need to be updated or removed:
- SystematicRSTest: Remove "test cross-validation systematic vs polynomial results"
- SystematicRSTest: Remove "test algorithm mismatch detection"
- SystematicRSTest: Update other tests to not reference RSAlgorithm
- RequestedIntegrationTest: Remove RSAlgorithm.SYSTEMATIC from config
- Phase1ValidationTest: Remove "side by side comparison of algorithms"
- ShardCombinationTest: Remove algorithm comparison tests
- Any test that creates EncodingConfig with algorithm parameter

### 6. Update Documentation
- Remove references to polynomial algorithm from CLAUDE.md
- Update any other documentation that mentions algorithm selection
- Update usage examples to not show algorithm parameter

## Code Changes Needed

### ReedSolomonEncoder.kt
```kotlin
class ReedSolomonEncoder {
    private val systematicEncoder = SystematicRSEncoder()
    
    fun encode(data: ByteArray, config: EncodingConfig): List<Shard> {
        return systematicEncoder.encode(data, config)
    }
}
```

### ReedSolomonDecoder.kt
```kotlin
class ReedSolomonDecoder {
    private val systematicDecoder = SystematicRSDecoder()
    
    fun decode(shards: List<Shard>): ReconstructionResult {
        return systematicDecoder.decode(shards)
    }
    
    fun canReconstruct(shards: List<Shard>, config: EncodingConfig): Boolean {
        return shards.size >= config.dataShards
    }
}
```

## Benefits After Cleanup

1. Simpler codebase with only one algorithm
2. Better reliability - systematic algorithm works for any k-out-of-n
3. No confusion about which algorithm to use
4. Smaller binary size
5. Easier to maintain and debug