<!-- SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Feature 07 — Accessibility and Feedback

## Objective

Make the magnifier usable with TalkBack and large system settings without adding unnecessary UI complexity.

## Dependencies

- Features 02–06
- [`../docs/architecture.md`](../docs/architecture.md), Section 8
- [`../docs/testing.md`](../docs/testing.md)

## Scope

- Add content and state descriptions for live and frozen modes.
- Add semantic custom actions for freeze and resume.
- Add accessible retry action for errors.
- Add readable zoom indicator and frozen banner.
- Respect system font scale, display scaling, and touch exploration.

## Requirements

1. The main action is available without pointer gestures.
2. TalkBack can identify live versus frozen state.
3. TalkBack can invoke freeze/resume.
4. Errors expose an understandable message and retry action.
5. Essential state is not conveyed by color alone.
6. Large text does not clip or hide essential overlays.
7. Accessibility announcements are useful without repeating on every frame or zoom update.

## Acceptance Criteria

- TalkBack announces the current screen state and zoom level.
- The semantic action toggles freeze/resume correctly.
- Error and retry states are accessible.
- Large font and display scaling preserve essential controls and status.

## Tests and Device Validation

- Add Compose semantics tests for state descriptions and custom actions.
- Run TalkBack checks on the connected device.
- Test large system font, display size, and touch exploration.
- Test light and dark system themes if both are supported.

## Risks and Edge Cases

- Preview surface consuming or hiding accessibility semantics.
- Duplicate announcements caused by rapidly changing zoom state.
- Custom action label not matching current state.
- Overlay layout failure at extreme font scales.

## Completion Checklist

- [x] Live/frozen semantics implemented.
- [x] Freeze/resume custom action implemented.
- [x] Accessible error/retry behavior implemented.
- [x] TalkBack and large-display validation completed on the connected SM-A566B, as confirmed by the user.