# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 3/16 (18.8%)
- **Function parity:** 8/303 matched (target 54) — 2.6%
- **Class/type parity:** 13/58 matched (target 17) — 22.4%
- **Combined symbol parity:** 21/361 matched (target 71) — 5.8%
- **Average inline-code cosine:** 0.29 (function body across 2 matched files)
- **Average documentation cosine:** 0.87 (doc text across 2 matched files)
- **Cheat-zeroed Files:** 1
- **Critical Issues:** 3 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. counter

- **Target:** `counter.Counter [PROVENANCE-FALLBACK]`
- **Similarity:** 0.35
- **Dependents:** 1
- **Priority Score:** 1041006.5
- **Functions:** 3/6 matched (target 10)
- **Missing functions:** `counter`, `deref`, `eq`
- **Types:** 3/4 matched (target 3)
- **Missing types:** `Target`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `src/counter.rs` vs expected `counter.rs`
- **Proposed provenance header:** `// port-lint: source counter.rs` (current: `// port-lint: source src/counter.rs`)
- **Lint issues:** 1

### 2. err

- **Target:** `crossbeamchannel.Err [PROVENANCE-FALLBACK]`
- **Similarity:** 0.23
- **Dependents:** 0
- **Priority Score:** 21707.7
- **Functions:** 5/7 matched (target 44)
- **Missing functions:** `fmt`, `from`
- **Types:** 10/10 matched (target 14)
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `src/err.rs` vs expected `err.rs`
- **Proposed provenance header:** `// port-lint: source err.rs` (current: `// port-lint: source src/err.rs`)
- **Lint issues:** 1

### 3. flavors.mod

- **Target:** `flavors.Mod [STUB] [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10.0
- **Functions:** 0/0 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `src/flavors/mod.rs` vs expected `flavors/mod.rs`
- **Proposed provenance header:** `// port-lint: source flavors/mod.rs` (current: `// port-lint: source src/flavors/mod.rs`)
- **Lint issues:** 1

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

## Reexport / Wiring Modules

These files match `reexport_modules` patterns in `.ast_distance_config.json`. They are filtered out of
normal priority and missing-file ladders because they are wiring
modules, not direct logic ports. Consult them for call-site routing;
do not treat them as the next implementation target by default.

### Missing

| Source | Expected target | Deps | Source path | Expected path |
|--------|-----------------|------|-------------|---------------|
| `lib` | `Lib` | 0 | `lib.rs` | `Lib.kt` |

