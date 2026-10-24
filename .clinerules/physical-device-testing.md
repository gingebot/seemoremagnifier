# Physical-device testing

- Before starting any test command that will use a connected physical Android device, ask the user to confirm that the phone is unlocked, awake, and interactive.
- Do not infer that the phone is ready from ADB connectivity alone. A locked or dozing phone can cause instrumented Compose tests to fail with missing Compose hierarchies.
- If the user has not confirmed the phone is interactive, do not run connected-device tests. Defer them and continue with device-independent checks, such as unit tests, lint, and debug assembly.
- Never try to wake, unlock, or inject input into the phone. Ask the user to make it interactive and confirm before proceeding.
- Emulator tests do not require physical-phone confirmation.