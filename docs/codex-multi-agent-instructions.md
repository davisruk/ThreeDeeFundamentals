# Codex Multi-Agent Instructions
This document only applies when the user has explicitly asked for the multi-agent approach for implementing a feature plan's step.

### Multi-agent step ownership and delegation
Multi-agent orchestration is opt-in. Do not spawn an implementation subagent or use multi-agent orchestration unless the user explicitly requests it for the current task or plan step.

When the user explicitly requests multi-agent orchestration, the current higher-capability agent becomes the step execution owner and the rules in this section apply. When delegating bounded implementation work, use the registered implementer subagent when available. Do not use a generic/default implementation subagent when the registered implementer is available.

For a user-requested multi-agent step, reuse an existing compatible implementer subagent from the current parent session when the runtime supports safe continuation and the child's previous task is complete. Otherwise spawn one new implementer. Do not repeatedly spawn replacement implementers for the same logical step.

When the current step is complete and no further corrective delegation is required, close/release the implementation subagent if the runtime provides that capability.

Once implementation has been delegated, allow the subagent to work to natural completion. Do not request progress reports, interim summaries, or instruct a healthy running subagent to stop merely because execution is taking longer than expected.

Use waiting/polling sparingly. A wait timeout or longer-than-expected execution time is not by itself evidence that the subagent has stalled. Do not repeatedly poll, re-prompt, or send status requests to a running subagent solely because a wait operation returned without completion.

Interrupt a subagent only when there is concrete evidence of failure or a genuine need to change the delegated task.

The step execution owner must remain the parent agent and retain step scope, architectural decisions, acceptance review, and final completion responsibility.

Before delegation, the parent must:

- read the complete current plan step and its named prerequisites
- inspect enough current repository code to confirm that the plan still matches reality
- retain every requirement, non-goal, compatibility constraint, implementation-verification requirement, and acceptance criterion from the original step
- derive an explicit internal acceptance checklist when the step has several independent obligations
- decompose the step only when useful; delegation must not silently narrow or redefine scope
- resolve implementation-significant decisions left open by the plan when repository inspection is sufficient; if a new architectural decision is required, stop and report it

Delegated tasks must state the bounded ownership, relevant files/types or implementation analogue, important constraints, authorized verification, and unchanged boundaries. Subagents must not broaden scope, redesign APIs, introduce alternative abstractions, or silently omit delegated work.

Prefer one implementation subagent at a time. Use parallel implementation subagents only for genuinely disjoint slices with non-overlapping write scopes or another clear isolation boundary.

The parent and subagents share one worktree. Only one agent may write at a time unless the parent establishes genuinely non-overlapping write scopes. Every delegated task must identify pre-existing modified or untracked files that the subagent must preserve. Subagents must not revert, overwrite, stage, or absorb changes outside delegated ownership. Neither parent nor subagent may commit, amend, merge, rebase, pull, push, or change branches unless the user explicitly requests that Git operation.

A subagent report must include:

- files changed
- behavior implemented
- focused compile/tests run and their results
- any delegated work not completed
- assumptions, ambiguities, unexpected repository state, and concerns relevant to parent review

After a subagent returns, the parent must inspect the actual diff and review it against the complete active plan step and acceptance checklist, not merely the subagent summary. Check for omitted behavior, narrowed scope, architectural-boundary violations, compatibility regressions, and tests that prove only the implemented subset. If additional non-architectural execution detail is needed, update the active plan step and delegate the bounded corrective work before acceptance.

The parent may request at most two corrective delegations after the initial implementation attempt for one plan step. This limit is cumulative across replacement subagents and cannot be reset by starting a new task. If the same gap remains, implementation is still incomplete after the second corrective delegation, or correction requires an architectural decision, stop and report the precise issue to the user. The parent owns architectural judgement and plan refinement; it must not silently switch into the implementation role to bypass the corrective limit.

A step is not ready for user verification merely because delegated code compiles or newly added tests pass. The parent must confirm that every active-plan and acceptance-checklist item is satisfied. The step becomes complete only after required user verification succeeds. Completion does not authorize starting the next step.

Implementation subagents may run only plan-authorized focused verification. Broader regression, complete-suite, visual, or user-reserved verification must be requested from the user with the exact command or check.

The parent normally accepts the subagent's reported focused verification result and does not duplicate a green run. It may rerun the same plan-authorized focused command only when code changed after the reported run, the result is missing or ambiguous, or review identifies a concrete concern requiring confirmation. This exception does not authorize broader verification.