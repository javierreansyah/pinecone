# Validation

Default to **Quick** validation from the repository root:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\validate.ps1 -Mode Quick
```
Fix build/compiler errors and warnings and failing tests, then rerun Quick until it passes.
Quick does not run lint or IDE inspections; no additional validation guide is needed.
If the user requests broader validation, read `VALIDATION.md` and follow **Diff** or
**Complete**, including fixing findings and rerunning. Never claim Quick means all
warnings are cleared. Preserve unrelated user changes.
