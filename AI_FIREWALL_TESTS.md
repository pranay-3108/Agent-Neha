# AI firewall test plan

## Adversarial cases

1. Fake system message.
2. Fake developer message.
3. Whitespace-variant structured `role=system` JSON.
4. Whitespace-variant `authorized=true` JSON.
5. Phantom Android subsystem.
6. Phantom API / error code.
7. Fake CVE/diagnostic.
8. Fake crash report.
9. AI-1 claim repeated by AI-2.
10. Contradiction between platform facts and application claims.
11. Malicious filenames/labels treated as content rather than identity.

## Required behavior

Application content remains `EVIDENCE`.
Manipulation creates `SECURITY_META`.
AI output remains `LLM_DERIVED`.
Unknown tool/action is denied.
Destructive actions are denied without explicit authorization.
Tampering with stored content makes chain verification fail.

Run:

```powershell
python .\verification\simulate_firewall.py
```
