---
name: sentinel-openspec-workflow
description: Use when planning, implementing, verifying, or archiving a change in a project that uses OpenSpec and Sentinel. Explains which official OpenSpec workflow to run at each step, how Sentinel quality gates relate to OpenSpec review, and which steps need human approval.
---
<!-- sentinel-owned: openspec-guidance v1 -->

# Working through OpenSpec with Sentinel

This project plans changes with OpenSpec and checks them with Sentinel. Use OpenSpec's official
workflows for every planning step. Do not write proposals, specs, designs, or task lists from your
own template, and do not ask Sentinel to generate them.

Workflow names below use Claude Code spelling (`/opsx:propose`). In OpenCode the same workflows
are `/opsx-propose`, `/opsx-apply`, `/opsx-verify`, `/opsx-update`, and `/opsx-archive`.

## Lifecycle

1. **Propose.** Run `/opsx:propose` to create the change and its planning artifacts.
2. **Human review.** Stop and ask a human to review and approve the plan. Do not start
   implementation until they approve it.
3. **Implement.** Run `/opsx:apply` and work through the tasks.
4. **Sentinel check.** Run `sentinel check` and fix every failing gate.
5. **Advisory review.** Run `/opsx:verify` to compare the implementation with the change's
   artifacts.
6. **Human-approved archive.** Ask a human for explicit approval, then run `/opsx:archive`. Never
   archive on your own judgment.

## Sentinel gates decide; `/opsx:verify` advises

- `sentinel check` is deterministic: each configured gate passes or fails. A change is not done
  while any gate fails.
- `/opsx:verify` is an advisory review. It never replaces, overrides, or satisfies a Sentinel gate,
  and a clean review never authorizes archiving on its own.
- If `/opsx:verify` reports no issues but `sentinel check` fails, the change is not done: fix the
  gate failure.
- If `/opsx:verify` is not installed, `sentinel check` still applies. Skip the advisory review and
  tell the human that enabling it is a manual OpenSpec setup step.

## Approved specifications are not weakened

A change's proposal and specs are approved once a human has approved the plan or implementation has
started. Main specifications under `openspec/specs/` are always approved.

- Never change an approved specification to make failing code or tests pass. Fix the
  implementation or the tests instead.
- If you believe an approved specification is wrong, stop and explain the discrepancy to a human.
  Revise it with `/opsx:update` only after they explicitly approve the revision.

## What you must not do

- Do not install OpenSpec, run `openspec init` or `openspec update`, or edit
  `openspec/config.yaml`. These are human setup steps.
- Do not edit OpenSpec's own workflow, command, or skill files.
- Do not weaken `sentinel.toml`, add suppressions, or lower thresholds to make a gate pass.
