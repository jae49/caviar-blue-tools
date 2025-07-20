# Plan to Fix Erasure Coding Edge Cases

This plan synthesizes information from `erasure_notes_2.md` and `erasure_alg_improvements.md` to address failing edge cases and complete the systematic Reed-Solomon algorithm implementation.

## 1. Complete Systematic Algorithm Integration

The systematic algorithm is not fully integrated with the main `ReedSolomonEncoder` and `ReedSolomonDecoder` APIs. This is the highest priority.

- **Action:** Complete the integration of the systematic algorithm with the main `ReedSolomonEncoder`/`Decoder` APIs.
- **File to modify:** `src/main/kotlin/cb/core/tools/erasure/ReedSolomonEncoder.kt`
- **File to modify:** `src/main/kotlin/cb/core/tools/erasure/ReedSolomonDecoder.kt`
- **Acceptance Criteria:**
    - `RSAlgorithm.SYSTEMATIC` in `EncodingConfig` properly routes to the systematic implementation.
    - The three disabled tests in `SystematicRSTest` (`test full API integration with EncodingConfig`, `test problematic case with full API`, `test property - Reed-Solomon MDS property`) are enabled and passing.

## 2. Address Systematic Algorithm Edge Cases

The systematic algorithm fails for specific `(k,n)` combinations like (5,11) and (6,12).

- **Action:** Investigate and fix the matrix properties that cause failures in these edge cases.
- **File to modify:** `src/main/kotlin/cb/core/tools/erasure/matrix/MatrixUtils.kt`
- **Possible solutions:**
    - Investigate alternative evaluation points for Vandermonde matrix generation.
    - Research and potentially implement Cauchy matrices as an alternative to Vandermonde matrices, as they can offer better numerical stability.
- **Acceptance Criteria:**
    - The skipped tests for (5,11) and (6,12) combinations in `SystematicRSTest` are enabled and passing.
    - The disabled test in `ShardCombinationTest` expecting 100% success for the systematic algorithm is enabled and passing.

## 3. Improve Error Handling and Reporting

Failures, especially for known limitations, should be communicated clearly to the user.

- **Action:** Enhance error messages to be more informative.
- **File to modify:** `src/main/kotlin/cb/core/tools/erasure/matrix/SystematicRSDecoder.kt`
- **File to modify:** `src/main/kotlin/cb/core/tools/erasure/ReedSolomonDecoder.kt`
- **Acceptance Criteria:**
    - When reconstruction fails due to a known limitation, the error message indicates this.
    - Error messages suggest alternative shard combinations where applicable.
    - Failures in matrix inversion provide clear diagnostic information.

## 4. Enhance and Finalize Documentation

With the algorithm fixes in place, the documentation needs to be updated to reflect the complete and robust implementation.

- **Action:** Update all user-facing documentation.
- **Files to modify:**
    - `docs/erasure-coding-api.md`
    - `docs/erasure-coding-usage.md`
    - `README.md`
- **Acceptance Criteria:**
    - Documentation clearly explains the benefits of the systematic algorithm.
    - All mentions of known limitations (that are now fixed) are removed.
    - Code examples are updated to reflect the final, fully integrated API.
    - Consider making the systematic algorithm the default in the documentation and future releases.

## 5. Final Validation and Performance Testing

Once all fixes are in, a final round of comprehensive testing is required.

- **Action:** Add final validation and performance benchmark tests.
- **Files to create/modify:**
    - `src/test/kotlin/cb/core/tools/erasure/performance/PerformanceBenchmark.kt` (or similar)
    - `src/test/kotlin/cb/core/tools/erasure/StressTest.kt`
- **Acceptance Criteria:**
    - A performance benchmark comparing the polynomial and systematic algorithms for various configurations is created.
    - Stress tests for maximum supported configurations (e.g., 255 shards) are implemented and passing.
    - Integration tests for large data sizes (e.g., 16KB with 8+6 configuration) are added and passing.
