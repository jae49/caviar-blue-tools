# Phase 1: Foundation - Matrix-Based Reed-Solomon Implementation

## Objective
Build and validate core matrix-based Reed-Solomon components as standalone implementations to prove the systematic approach works correctly.

## Background
The current polynomial division-based Reed-Solomon implementation cannot guarantee reconstruction from arbitrary k-out-of-n shard combinations. This phase implements the mathematical foundation for a complete systematic Reed-Solomon code using Vandermonde matrices.

## Deliverables
1. **MatrixUtils.kt** - GF(256) matrix operations (Vandermonde matrix generation, matrix inversion, multiplication)
2. **SystematicRSEncoder.kt** - Standalone systematic encoder using matrix multiplication
3. **SystematicRSDecoder.kt** - Standalone decoder capable of arbitrary k-out-of-n reconstruction
4. **Unit Tests** - Basic tests proving the mathematical approach works

## Success Criteria
- [ ] Basic round-trip encoding/decoding works for simple test cases
- [ ] The problematic case from erasure_notes.md (8 shards with missing [0,3,6]) works correctly
- [ ] Matrix operations are mathematically correct (A * A^(-1) = I)
- [ ] All Phase 1 unit tests pass

## Dependencies
- Existing GaloisField implementation for GF(256) arithmetic
- No dependencies on existing encoder/decoder infrastructure (standalone implementation)

## Timeline
This is the foundation phase that must be completed before any integration work. Estimated completion: 1-2 days of focused development.

## Testing Strategy
1. **Matrix Operations Tests**
   - Vandermonde matrix generation for small configurations
   - Matrix inversion with known examples
   - Matrix-vector multiplication verification
   - Mathematical property verification

2. **Round-Trip Tests**
   - Simple data encoding/decoding (e.g., "Hello World")
   - Small configurations (3,2 and 4,2)
   - Problematic case verification (8 shards, missing [0,3,6])

## Risk Mitigation
- Isolated development in new `matrix` package prevents disruption to existing functionality
- Focus on correctness over performance (optimization comes in Phase 3)
- Simple interfaces allow easy testing and validation
- No changes to existing code until approach is proven

## Implementation Notes
- All operations use existing GaloisField for arithmetic
- Byte-by-byte processing maintains compatibility with existing patterns
- Systematic property: data shards contain original data unchanged
- Focus on simplicity and clarity for Phase 1

## Next Steps
After successful completion of Phase 1:
- Phase 2: Integration with existing EncodingConfig and Shard infrastructure
- Phase 3: Performance optimization and caching
- Phase 4: Comprehensive testing and validation
- Phase 5: Documentation and migration guidance