# Codex Prompt Templates

## Multi-agent concurrency policy (Codex + Claude + humans)
- Parallelism allowed: Codex and Claude may both implement tickets, including code changes, in either repo.
- Single-writer per PR/branch: Exactly one agent may push commits to a given PR branch. No tag-team pushing.
- Merge authority: Only Codex may merge PRs (or perform the final “merge step” for the session).
- Serialized merges: Only one merge operation may happen at a time across BOTH repos (Mimeo + Mimeo-Android).
- No history rewrites: No rebases or force-pushes by agents. Additive commits only.
- Contract-change flag: Any PR that changes backend/API semantics must be labeled "CONTRACT CHANGE" and coordinated; dependent client work must not assume it until merged.
- Local safety: If agents share a machine, do not share a working directory; avoid stash-based workflows; tracked local modifications => STOP.
- Precedence: CODEX_PROMPTS.md is authoritative for Codex behavior; CLAUDE.md is authoritative for Claude behavior.
