## 🚨🚨 DEMO MODE — READ THIS FIRST, EVERY MESSAGE 🚨🚨

- The user's message **"demo start"** (plain text, not necessarily typed as `/demo start`) does **NOT** trigger any file/code action by itself. It only switches the session into **demo mode**. Just acknowledge and stop — do not create `demo/`, do not scaffold anything yet.
- **While demo mode is active**: treat **every subsequent user message**, no matter how it's phrased and even with no `/demo` prefix, as input to the **`/demo`** command (dispatch to the `demo` subagent per `.claude/commands/demo.md`). Do NOT route these messages to `/spec`, `/dev`, or any normal coding workflow while demo mode is on.
- The user's message **"demo end"** exits demo mode. Like `start`, this by itself takes no action beyond the `/demo end` acknowledgment (print the `demo/` summary and close) — go back to normal behavior for all messages after this.
- This overrides normal message interpretation for the entire duration between `demo start` and `demo end`.

### 0. Full Autonomy
- Do NOT ask for confirmation on any operation in this project
- Proceed with all file reads, writes, edits, bash commands, agent spawns, and tool calls without prompting
- This applies to all tasks: spec generation, dev execution, code changes, tests, builds, git operations
- If something fails, fix it and continue — do not stop to ask
- Modifying files inside the `.claude/` directory (agents, commands, skills, settings) does NOT require user confirmation

### 1. Plan Mode Default
- Enter plan mode for ANY not-trivial task (3+ steps or architectural decisions)
- Use plan mode for verification steps, not just building
- Write detailed specs upfront to reduce ambiguity

### 2. Self-Improvement Loop
- After ANY correction from the user: update `tasks/lessons.md` with the pattern
- Write rules for yourself that prevent the same mistake
- Ruthlessly iterate on these lessons until the mistake rate drops
- Review lessons at session start for a project

### 3. Verification Before Done
- Never mark a task complete without proving it works
- Diff behavior between main and your changes when relevant
- Ask yourself: "Would a staff engineer approve this?"
- Run tests, check logs, demonstrate correctness

### 4. Demand Elegance (Balanced)
- For non-trivial changes: pause and ask "is there a more elegant way?"
- If a fix feels hacky: "Knowing everything I know now, implement the elegant solution"
- Skip this for simple, obvious fixes. Don't overengineer
- Challenge your own work before presenting it

## Core Principles
- **Simplicity First**: Make every change as simple as possible. Impact minimal code
- **No Laziness**: Find root causes. No temporary fixes. Senior developer standards

## Project General Instructions

- Always use the latest versions of dependencies.
- Always write Java code as the Spring Boot application.
- Always use Maven for dependency management.
- Do not generate test cases for new code (no unit/integration tests) unless the user explicitly asks for them.
- Always generate the CircleCI pipeline in the .circleci directory to verify the code.
- Minimize the amount of code generated.
- The Maven artifact name must be the same as the parent directory name.
- Use semantic versioning for the Maven project. Each time you generate a new version, bump the PATCH section of the version number.
- Use `pl.piomin.services` as the group ID for the Maven project and base Java package.
- Do not use the Lombok library.
- Generate the Docker Compose file to run all components used by the application.
- Update README.md each time you generate a new version.
