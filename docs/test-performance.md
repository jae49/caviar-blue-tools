# Test Performance Guidelines

## Test Organization

The test suite is organized into two categories:

### Fast Tests (Default)
Run with: `gradle test`

These tests complete within 30 seconds and include:
- Unit tests for core functionality
- Small integration tests
- Basic round-trip encoding/decoding tests

### Slow Tests
Run with: `gradle slowTests`

These tests are tagged with `@Tag("slow")` and include:
- Performance benchmarks
- Large data size tests
- Extensive integration tests
- Tests with many iterations

## Test Categories

### Always Fast (<1 second each)
- `EncodingConfigTest` - Model validation tests
- `ReedSolomonEncoderTest` - Basic encoding tests
- `ReedSolomonDecoderTest` - Basic decoding tests
- `PolynomialMathTest` - Mathematical operation tests
- `StreamingEncoderTest` - Streaming encoding tests
- `StreamingDecoderTest` - Streaming decoding tests

### Potentially Slow (>1 second each)
- `GaloisFieldTest` - Field operation tests (~2 seconds)
- `FastIntegrationTest` - Integration tests (~27 seconds)

### Slow Tests (Tagged)
- `MathBenchmark` - Performance benchmarks (1M+ iterations)
- `PerformanceBenchmark` - Throughput measurements
- `ExtensiveDataSizeTest` - Large data size tests
- `IntegrationTest` - Complex integration scenarios
- `RequestedIntegrationTest` - Specific 8+6 configuration test

## Performance Considerations

### Why Some Tests Are Slow

1. **Large Data Sizes**: Tests processing MB of data take time
2. **Multiple Iterations**: Benchmarks need many iterations for accuracy
3. **Complex Configurations**: Larger shard counts (8+6) require more computation
4. **Erasure Recovery**: Reconstructing with missing shards is computationally intensive

### Optimization Strategies Applied

1. **Test Tagging**: Slow tests marked with `@Tag("slow")`
2. **Reduced Iterations**: Benchmarks use 3 iterations instead of 10
3. **Smaller Data Sizes**: Fast tests use <1KB data
4. **Configuration Limits**: Default tests use smaller configurations (4+2)

## Running Tests

```bash
# Fast tests only (default, <30 seconds)
gradle test

# Slow tests only
gradle slowTests

# All tests
gradle test slowTests

# Specific test class
gradle test --tests "*FastIntegrationTest*"

# With detailed output
gradle test --info
```

## Known Issues

1. **8+6 Configuration**: Decoding with many erasures can be slow due to polynomial operations
2. **Memory Usage**: Large data tests may require increased heap size
3. **Timeout Risk**: Some slow tests may timeout on slower machines

## Recommendations

1. Run fast tests during development
2. Run slow tests before commits
3. Use smaller configurations (4+2) for quick testing
4. Consider parallelizing test execution for CI/CD