# CLAUDE.md

## Project Architecture

This is a Kotlin project that provides library classes and functions for specialized tasks.

## Core Architecture Patterns
- Prefer JSON for data interchange
- When working with database features, prefer generic sql that is usable in postgresql, sqlite and mssql

## Development Workflow

### Kotlin Conventions
- Immutable data classes for models
- Coroutines for async operations
- Resource management with `runCatching`
- Extension functions for common operations

### Package structure
- Root package name cb.core.tools
- Feature based with package names by category and then specific features.  Model packages are contained within categories

## Reed-Solomon Erasure Coding Implementation

### Mathematical Foundation (Phase 1 - COMPLETED)
The project includes a comprehensive Reed-Solomon erasure coding implementation in `cb.core.tools.erasure.math`:

#### GaloisField Operations
- **Location**: `cb.core.tools.erasure.math.GaloisField`
- **Purpose**: Implements GF(256) finite field arithmetic for Reed-Solomon encoding
- **Key Features**:
  - Pre-computed exponential and logarithmic lookup tables
  - Basic field operations: add, subtract, multiply, divide, power, inverse
  - Polynomial operations: multiplication, evaluation, division
  - Optimized for performance with >1M operations/second

#### Polynomial Mathematics
- **Location**: `cb.core.tools.erasure.math.PolynomialMath`
- **Purpose**: High-level polynomial operations for Reed-Solomon encoding/decoding
- **Key Features**:
  - Generator polynomial creation for configurable redundancy
  - Matrix operations for error correction
  - Polynomial interpolation for data reconstruction
  - Support for up to 255 total shards (GF(256) limitation)

### Testing Infrastructure
- **Unit Tests**: Comprehensive test coverage with 30+ test methods
- **Performance Benchmarks**: Throughput and scalability measurement tools
- **Test Framework**: JUnit 5 with proper assertion libraries

### Development Commands
```bash
# Run all tests
gradle test

# Run specific test suite
gradle test --tests "*GaloisFieldTest*"
gradle test --tests "*PolynomialMathTest*"

# Run performance benchmarks
gradle test --tests "*MathBenchmark*"
```

### Implementation Status
- ✅ **Phase 1**: Mathematical foundation (COMPLETE)
- 🔄 **Phase 2**: Core encoding/decoding classes (NEXT)
- ⏳ **Phase 3**: Streaming support with coroutines
- ⏳ **Phase 4**: Performance optimization and documentation 
