# port-lint Proposed Changes

**Generated:** 2026-08-28
**Source:** tmp/crossbeam-channel/src
**Target:** src/commonMain/kotlin/io/github/kotlinmania/crossbeamchannel

These are review proposals only. They are emitted when a Rust -> Kotlin pair matches only after fallback normalization, so the existing `port-lint` header is not an exact provenance match.

| Target file | Current header | Proposed header | Source path | Reason |
|-------------|----------------|-----------------|-------------|--------|
| `src/commonMain/kotlin/io/github/kotlinmania/crossbeamchannel/counter/Counter.kt` | `// port-lint: source src/counter.rs` | `// port-lint: source counter.rs` | `counter.rs` | `port-lint provenance header matched only after fallback normalization: 'src/counter.rs' vs expected 'counter.rs'` |
| `src/commonMain/kotlin/io/github/kotlinmania/crossbeamchannel/Err.kt` | `// port-lint: source src/err.rs` | `// port-lint: source err.rs` | `err.rs` | `port-lint provenance header matched only after fallback normalization: 'src/err.rs' vs expected 'err.rs'` |
| `src/commonMain/kotlin/io/github/kotlinmania/crossbeamchannel/flavors/Mod.kt` | `// port-lint: source src/flavors/mod.rs` | `// port-lint: source flavors/mod.rs` | `flavors/mod.rs` | `port-lint provenance header matched only after fallback normalization: 'src/flavors/mod.rs' vs expected 'flavors/mod.rs'` |
