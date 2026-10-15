# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 16/16 (100.0%)
- **Function parity:** 257/327 matched (target 323) — 78.6%
- **Class/type parity:** 31/58 matched (target 76) — 53.4%
- **Combined symbol parity:** 288/385 matched (target 399) — 74.8%
- **Average inline-code cosine:** 0.21 (function body across 15 matched files)
- **Average documentation cosine:** 0.60 (doc text across 15 matched files)
- **Cheat-zeroed Files:** 2
- **Critical Issues:** 15 files with <0.60 function similarity
- **Needs Review:** 0 files with 0.60-0.84 function similarity
- **Excellent:** 1 files with >=0.85 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. context

- **Target:** `crossbeamchannel.Context`
- **Similarity:** 0.14
- **Dependents:** 9
- **Priority Score:** 9011209.0
- **Functions:** 10/10 matched (target 15)
- **Missing functions:** _none_
- **Types:** 1/2 matched (target 8)
- **Missing types:** `Inner`

### 2. utils

- **Target:** `crossbeamchannel.Utils`
- **Similarity:** 0.14
- **Dependents:** 3
- **Priority Score:** 3000208.8
- **Functions:** 2/2 matched (target 10)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_

### 3. waker

- **Target:** `crossbeamchannel.Waker`
- **Similarity:** 0.19
- **Dependents:** 1
- **Priority Score:** 1072308.0
- **Functions:** 13/20 matched (target 16)
- **Missing functions:** `new`, `notify`, `drop`, `new`, `notify`, `drop`, `current_thread_id`
- **Types:** 3/3 matched (target 4)
- **Missing types:** _none_

### 4. counter

- **Target:** `counter.Counter`
- **Similarity:** 0.09
- **Dependents:** 1
- **Priority Score:** 1071509.1
- **Functions:** 5/11 matched (target 10)
- **Missing functions:** `counter`, `deref`, `eq`, `counter`, `deref`, `eq`
- **Types:** 3/4 matched (target 3)
- **Missing types:** `Target`

### 5. select

- **Target:** `crossbeamchannel.Select`
- **Similarity:** 0.05
- **Dependents:** 0
- **Priority Score:** 224509.5
- **Functions:** 18/38 matched (target 18)
- **Missing functions:** `hook`, `from`, `into`, `deadline`, `register`, `unregister`, `accept`, `is_ready`, `watch`, `unwatch`, `try_select`, `try_select`, `select`, `select_timeout`, `select_deadline`, `clone`, `default`, `fmt`, `fmt`, `drop`
- **Types:** 5/7 matched (target 9)
- **Missing types:** `Operation`, `Selected`

### 6. err

- **Target:** `crossbeamchannel.Err`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 153610.0
- **Functions:** 11/26 matched (target 44)
- **Missing functions:** `fmt`, `fmt`, `fmt`, `fmt`, `from`, `fmt`, `fmt`, `from`, `fmt`, `fmt`, `from`, `fmt`, `from`, `fmt`, `fmt`
- **Types:** 10/10 matched (target 19)
- **Missing types:** _none_

### 7. flavors.list

- **Target:** `flavors.List`
- **Similarity:** 0.22
- **Dependents:** 0
- **Priority Score:** 134807.8
- **Functions:** 34/41 matched (target 35)
- **Missing functions:** `destroy`, `default`, `new`, `disconnect_senders`, `disconnect_receivers`, `discard_all_messages`, `drop`
- **Types:** 1/7 matched (target 6)
- **Missing types:** `Slot`, `Block`, `ListToken`, `Channel`, `Receiver`, `Sender`
- **Lint issues:** 3

### 8. flavors.zero

- **Target:** `flavors.Zero`
- **Similarity:** 0.19
- **Dependents:** 0
- **Priority Score:** 104408.1
- **Functions:** 33/38 matched (target 33)
- **Missing functions:** `default`, `fmt`, `empty_on_stack`, `empty_on_heap`, `message_on_stack`
- **Types:** 1/6 matched (target 5)
- **Missing types:** `ZeroToken`, `Inner`, `Channel`, `Receiver`, `Sender`

### 9. channel

- **Target:** `crossbeamchannel.Channel`
- **Similarity:** 0.08
- **Dependents:** 0
- **Priority Score:** 96609.2
- **Functions:** 50/58 matched (target 56)
- **Missing functions:** `drop`, `fmt`, `drop`, `fmt`, `into_iter`, `fmt`, `fmt`, `fmt`
- **Types:** 7/8 matched (target 13)
- **Missing types:** `Item`

### 10. flavors.array

- **Target:** `flavors.Array`
- **Similarity:** 0.25
- **Dependents:** 0
- **Priority Score:** 74007.5
- **Functions:** 33/35 matched (target 33)
- **Missing functions:** `default`, `drop`
- **Types:** 0/5 matched (target 4)
- **Missing types:** `Slot`, `ArrayToken`, `Channel`, `Receiver`, `Sender`

### 11. flavors.never

- **Target:** `flavors.Never`
- **Similarity:** 0.22
- **Dependents:** 0
- **Priority Score:** 21807.8
- **Functions:** 16/16 matched
- **Missing functions:** _none_
- **Types:** 0/2 matched (target 1)
- **Missing types:** `NeverToken`, `Channel`
- **Lint issues:** 2

### 12. flavors.tick

- **Target:** `flavors.Tick`
- **Similarity:** 0.29
- **Dependents:** 0
- **Priority Score:** 21807.1
- **Functions:** 16/16 matched
- **Missing functions:** _none_
- **Types:** 0/2 matched (target 1)
- **Missing types:** `TickToken`, `Channel`
- **Lint issues:** 2

### 13. flavors.at

- **Target:** `flavors.At`
- **Similarity:** 0.29
- **Dependents:** 0
- **Priority Score:** 21807.1
- **Functions:** 16/16 matched
- **Missing functions:** _none_
- **Types:** 0/2 matched (target 1)
- **Missing types:** `AtToken`, `Channel`
- **Lint issues:** 2

### 14. select_macro

- **Target:** `crossbeamchannel.SelectMacro [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10.0
- **Functions:** 0/0 matched (target 5)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_

### 15. flavors.mod

- **Target:** `flavors.Mod [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10.0
- **Functions:** 0/0 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 16. lib

- **Target:** `crossbeamchannel.Lib`
- **Similarity:** 1.00
- **Dependents:** 0
- **Priority Score:** 0.0
- **Functions:** 0/0 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

