# Codex Instructions

## Purpose

This document is a session handoff note for follow-up work after the tipper / sorter separation cleanup.

Read these documents before starting:

1. `docs/codex-context.md`
2. `docs/tote-to-bag-requirements.txt`
3. `docs/tipper-route-mounted-machine-architecture.md`

## Working Rule

Do not make any code or document changes unless the user explicitly agrees in the current session.

## Current Position

The main tipper / sorter architectural cleanup described in earlier sessions has now been completed materially.

Current code position:

- `TipperModule` is independently installed via `TipperSectionInstaller` / `TipperInstallation`
- `SortingModule` is independently installed via `SortingSectionInstaller` / `SortingInstallation`
- the old paired `TipperEntryModule` / builder shape has been removed
- `TipperToSorterSection` is now the explicit composition helper for the paired path
- route ownership is external to the mounted tipper installation through `TipperTrackSection`
- tote load-plan ownership is externalised through `ToteLoadPlanProvider`
- the tipper-side flow controller now uses a small downstream-capacity boundary (`TipperDownstreamFlow`)
- the branch now proves both:
  - `tipper -> sorter`
  - `tipper -> alternate debug-only receive target`

## Specific Direction To Continue From

Continue from the position described in `docs/tipper-route-mounted-machine-architecture.md`:

- machine-to-machine seams should stay explicit
- the sorter is no longer treated as part of the tipper's identity
- the current installed tipper flow should be treated as the repeatable pattern for future route-mounted machines:
  - installer plus install result
  - explicit handoff points
  - external route ownership
  - external load-plan ownership
  - explicit composition helper between adjacent machines
  - small downstream-flow boundary for local controller release / occupancy decisions
- likely follow-up work is now about applying the same pattern to future mounted machines and higher-level orchestration rather than continuing tipper / sorter untangling for its own sake
