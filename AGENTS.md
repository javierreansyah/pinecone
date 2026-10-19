# Validation

Default to **Quick** validation from the repository root (Windows PowerShell):
```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --warning-mode all
```
Fix build/compiler errors and warnings and failing tests, then rerun Quick until it passes.
Quick does not run lint or IDE inspections; no additional validation guide is needed.
If the user requests broader validation, read `VALIDATION.md` and follow **Complete**.
IDE recommendations are handled manually by the user in JetBrains tools, not by agents.
Never claim Quick means all warnings are cleared. Preserve unrelated user changes.
