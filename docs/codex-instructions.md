# Codex Instructions

## Purpose

This document is a session handoff note for the next piece of tipper / tote-to-bag architecture work.

Read these documents before starting:

1. `docs/codex-context.md`
2. `docs/tote-to-bag-requirements.txt`
3. `docs/tipper-route-mounted-machine-architecture.md`

## Working Rule

Do not make any code or document changes unless the user explicitly agrees in the current session.

## Current Focus For The Next Session

The current code has been cleaned up materially, but the main remaining architectural issue is that the reusable shape is still too close to a fixed "tipper plus sorter" section.

The next piece of work should focus on:

- keeping `TipperModule` independently reusable
- keeping `SortingModule` independently reusable
- treating the current paired section as a composition helper rather than as the primary machine abstraction
- improving the scene/build composition boundary so downstream machines can be swapped

## Specific Direction To Continue From

Continue from the position described in `docs/tipper-route-mounted-machine-architecture.md`:

- machine-to-machine seams should stay explicit
- the tipper should be able to discharge into something other than a sorter
- the sorter should not be treated as part of the tipper’s identity
- future machines are expected to include additional route-mounted or conveyor-adjacent stations, so a lightweight independent-machine composition style is preferred over a monolithic paired module
