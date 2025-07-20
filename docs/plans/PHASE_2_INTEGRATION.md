# Phase 2: Integration

## Objective
Integrate systematic Reed-Solomon algorithm with existing encoder/decoder infrastructure, enabling side-by-side operation with the polynomial algorithm while maintaining full backward compatibility.

## Deliverables
1. **EncodingConfig Enhancement**: Add RSAlgorithm enum and algorithm selection parameter
2. **Encoder Integration**: Update ReedSolomonEncoder to support algorithm selection
3. **Decoder Integration**: Update ReedSolomonDecoder with automatic algorithm detection
4. **API Compatibility**: SystematicRSEncoder/Decoder using full Shard/EncodingConfig API
5. **Test Updates**: Integration tests verifying both algorithms work through the same API

## Success Criteria
- ✅ All existing tests pass without modification
- ✅ New systematic algorithm works through existing public API
- ✅ Backward compatibility maintained (polynomial remains default)
- ✅ Systematic and polynomial encoded shards can coexist
- ✅ Automatic algorithm detection in decoder
- ✅ No breaking changes to public interfaces

## Dependencies
- **Phase 1 Completion**: Core matrix utilities and basic systematic encoder/decoder
- **Validation**: Phase 1 components tested and working correctly
- **GaloisField**: Existing arithmetic operations available

## Timeline
- Start: After Phase 1 mathematical foundation is proven
- Duration: 1-2 days for implementation and testing
- Milestone: Both algorithms available through unified API

## Testing Strategy
1. **Backward Compatibility Tests**:
   - Verify existing polynomial tests continue to pass
   - Ensure default behavior unchanged
   - Test polynomial-encoded shards decode correctly

2. **Integration Tests**:
   - Test systematic algorithm through EncodingConfig
   - Verify Shard metadata is correct
   - Test mixed scenarios (polynomial and systematic shards)

3. **Algorithm Selection Tests**:
   - Test explicit algorithm selection
   - Test automatic detection in decoder
   - Test error handling for invalid configurations

## Risk Mitigation
1. **Default to Polynomial**: Maintain existing behavior by default
2. **Phased Rollout**: Keep systematic as opt-in initially
3. **Clear Documentation**: Document differences and migration path
4. **Extensive Testing**: Ensure no regression in existing functionality
5. **Logging**: Add clear logging for algorithm selection

## Implementation Order
1. EncodingConfig.kt - Add RSAlgorithm enum
2. SystematicRSEncoder.kt - Update to use EncodingConfig/Shard API
3. SystematicRSDecoder.kt - Update to use Shard API and ReconstructionResult
4. ReedSolomonEncoder.kt - Add algorithm delegation
5. ReedSolomonDecoder.kt - Add algorithm detection and delegation
6. SystematicRSTest.kt - Update tests for full integration
7. Validation - Run all tests and fix issues

## Technical Details

### RSAlgorithm Enum
```kotlin
enum class RSAlgorithm {
    POLYNOMIAL,  // Existing polynomial division approach
    SYSTEMATIC   // New matrix-based systematic approach
}
```

### EncodingConfig Changes
- Add `algorithm: RSAlgorithm = RSAlgorithm.POLYNOMIAL` parameter
- Maintain backward compatibility with default value
- Update validation if needed

### Shard Metadata
- Consider adding algorithm indicator to metadata for easier detection
- Ensure compatibility between algorithm-generated shards

### Error Handling
- Maintain existing error codes and messages
- Add new error types only if absolutely necessary
- Ensure clear error messages for algorithm-specific issues