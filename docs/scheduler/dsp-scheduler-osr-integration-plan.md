# DSP Scheduler OSR Integration Plan

Status: to be drafted next.

This document will contain the detailed step-by-step plan for `feature/dsp-scheduler-osr-integration`.

Known branch purpose:

- Add scheduler-driven OSR/AV02 release sources.
- Add simulation-thread application of `ReleaseOrderCommand`.
- Replace or wrap debug tote injection so scheduler decisions control release.
- Create tote renderables only when orders are released.
- Keep scheduler evaluation synchronous; do not add the scheduler thread in this branch.
