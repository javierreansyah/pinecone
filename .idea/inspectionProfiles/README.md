# Inspection profiles

`Style` is an opt-in Kotlin style/cleanup profile for **Code > Inspect Code**.
Choose `Style` in the inspection dialog; the everyday project profile remains `Fast`.
Use a file, directory, or changed-files scope for faster feedback, then inspect the
whole project when desired. Let Android Studio finish indexing first.

## Coverage

- Unused local variables (including unused destructuring entries), redundant
  initializers, unused imports, and unused expressions/results.
- Destructuring suggestions and incomplete destructuring.
- Naming conventions, modifier order, and trailing-comma conventions.
- Redundant syntax/types, simpler control flow/null handling, lambda cleanup,
  and idiomatic collection, range, operator, and string operations.

Function naming accepts both camelCase and PascalCase for Compose. The installed
Kotlin naming inspection has no annotation-specific exemption setting, so this
also permits PascalCase on non-Compose functions; other naming checks retain
their defaults.

Inspection IDs were checked against the installed Android Studio Kotlin plugin.
The profile is locked so unlisted inspections do not inherit IDE defaults.
Most recommendations use weak-warning severity to be visible in batch results;
severity does **not** reduce inspection cost. Semantic inspections still need
Kotlin type resolution, so this broader profile is not guaranteed to run as fast
as the original minimal selection. No timing benchmark has been performed.

## Deliberate limits

### Readability and conflicting fixes

- Keep `UnclearPrecedenceOfBinaryExpression`; disable
  `RemoveUnnecessaryParentheses`. Parentheses that clarify mixed operators are
  welcome even when not required by the language. The separate checks for empty
  parentheses on annotations and lambda calls remain enabled.
  Validation also found that removing parentheses from the callback in
  `OrganizeBottomSheet.kt` changed `else ({ ... })` (a lambda value) into
  `else { ... }` (a block returning `Unit`), breaking compilation. Do not apply
  that transformation; callback grouping can be required, not merely stylistic.
- Keep `NestedLambdaShadowedImplicitParameter`. Resolve ambiguous nested `it`
  parameters with meaningful explicit names rather than removing parameter
  declarations. Disable `RedundantLambdaArrow` so it does not suggest undoing
  explicit parameter lists introduced to resolve shadowing (including `_ ->`
  for an unused parameter). Simple, unambiguous lambdas can still use `it`.
- Constructor checks are distinct: `RedundantConstructorKeyword` removes an
  optional keyword, and `CanBePrimaryConstructorProperty` consolidates a
  property declaration. Neither is a blanket instruction to delete parameters.
  Retain parameters/properties required by method bodies or API contracts.
  No constructor inspection has been disabled without identifying a concrete
  conflicting pair.
- Unused parameter values do not imply removable API parameters. For example,
  the `consumed` parameters in `NestedScrollConnection.onPostScroll` and
  `onPostFling` must remain in overrides even when their values are unused.
- Review style quick-fixes rather than applying every recommendation blindly.
  Fix correctness and unused code first, readability next, optional
  simplifications last. If fixes repeatedly undo each other, prefer the
  readability policy and review the competing inspection.
- Disable `RedundantRequireNotNullCall` for the current toolchain. The working
  tree's replacement of `requireNotNull(Url(href))` with `Url(href)` in
  `PdfTextOverlay.kt` fails compilation in debug, release, and staging:
  Readium's URL factory returns `Url?`, while `Locator` requires `Url`.
  This is evidence against that transformation, not proof of which IDE action
  applied it. Preserve required null guards and reassess this inspection after
  Kotlin IDE/compiler or Readium upgrades. Disabling the inspection does not
  restore guards already removed from source.

### Coverage and performance exclusions

- No project-wide `UnusedSymbol`/dead-code searches, visibility usage searches,
  or cross-method/call-graph analysis. Unused public/private declarations and
  parameters are not promised by this local-cleanup profile.
- No Android Lint, spelling/grammar, or unrelated language inspections. Use
  `Complete` for broader IDE diagnostics, not as a speed-focused style pass.
- No blanket compiler-diagnostics pass: selected local diagnostic inspections
  are enabled explicitly instead.
- No mutually opposing style conversions (such as adding versus removing
  explicit `this`, or converting ranges into comparisons and back).
- Formatting is governed by IDE code-style settings and **Reformat Code**;
  inspections are not a complete whitespace/formatting checker.
- Editor intentions are not always batch inspections. A lightbulb action can
  exist without a corresponding inspection that a profile can enable.

This is broad Kotlin style coverage, not a guarantee of every stylistic issue
or every Android Lint warning. New plugin inspections require explicit review.
Gradle Quick validation does not execute these IDE profiles; run them manually
in Android Studio.
