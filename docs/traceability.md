# Requirement-to-code traceability

Use this path when refining or implementing work:

1. Identify the requirement in the [PRD](prd/requirements.md) by its stable ID
   (for example, `FR-001`).
2. Link the requirement ID from the relevant [epic](epics/README.md) and
   [story](stories/README.md). For repository or process work that has no
   product FR, trace from the story's acceptance criterion and do not invent an
   FR mapping.
3. Link the applicable design in the [architecture index](architecture/index.md)
   or a related design document.
4. Identify the owning source module under `modules/`.
5. Identify the corresponding test under that module's `src/test/`, or explain
   why verification is manual.

## Example: Story 0.2

| Stage | Reference |
|---|---|
| Requirement | Story 0.2 Definition of Done: identify the path from requirement through test |
| Epic | [Epic 0: Project Foundation & Engineering Setup](epics/epic-00-setup.md) |
| Story | [Story 0.2: Establish BMAD Project Structure](stories/epic-00-stories/Story-0.2.md) |
| Technical design | [Repository structure](architecture/source-tree.md), [coding standards](architecture/coding-standards.md) |
| Code | `modules/contracts/src/` is the initial source path required by Story 0.2; feature code belongs in the owning module listed in the [repository structure](architecture/source-tree.md) |
| Test | The existing contracts test is under `modules/contracts/src/test/`; see the [test organization index](../tests/README.md) for the module-local convention |

The test path is a location reference, not a claim that the existing
application test verifies Story 0.2's documentation criteria. Verify those
criteria by following the links and checking the documented directories.
Product FR-to-epic mappings should only be added when a story explicitly
implements that FR.
