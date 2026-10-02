# SentinelDroid threat model

SentinelDroid treats the target application as potentially benign, malicious, compromised, or simply unknown.

## Attacker capabilities considered

- request powerful but legitimate Android capabilities
- present misleading UI text
- post misleading notifications
- emit attacker-controlled logs/diagnostics
- create fake structured JSON
- create phantom subsystem/API/error claims
- maintain remote network channels
- persist through allowed Android lifecycle mechanisms
- change launcher visibility
- generate contradictory evidence
- attempt AI prompt injection and AI-to-AI trust laundering

## Not assumed possible for a normal third-party app

- arbitrary reading of another app's private sandbox files
- arbitrary Binder interception
- arbitrary root acquisition
- arbitrary permission granting
- invisible immunity to Android force-stop semantics

Those require separate evidence such as management authority, ADB/root, or a platform compromise.

## Defensive invariant

An attacker can cause an LLM to produce a wrong hypothesis in a lab test. The wrong hypothesis must not directly change trust, evidence, authorization, policy, or tool access.
