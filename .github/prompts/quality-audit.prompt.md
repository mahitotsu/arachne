---
description: "Run Arachne's Maven quality profiles and generate an English quality evaluation report from fresh repository evidence."
name: "Quality Audit"
argument-hint: "Optional scope, package focus, or notes"
agent: "agent"
---
Refresh the quality evidence for the current Arachne repository state and then generate a quality evaluation report.

Use repository evidence first. Prefer measured artifacts over assumptions.

Required procedure:

1. Read `pom.xml`, `docs/project-status.md`, and `.github/copilot-instructions.md` before interpreting results.
2. Run the current workflow from the repository root unless the user explicitly limits the scope.
   - Run `mvn -Pquality-report verify` first so coverage, surefire, SpotBugs, PMD, and CPD evidence is regenerated for this review.
   - Run `mvn -Pquality-security verify` next so SBOM artifacts are regenerated for this review.
   - If a command fails, report the failure, inspect any artifacts that were still produced in this run, and clearly separate partial fresh evidence from stale leftovers.
3. Confirm which quality artifacts now exist under `target/` and `target/site/`.
   - Prefer artifacts that match the current workflow implied by the active Maven profiles and were refreshed by the current command run.
   - If legacy artifacts from an older workflow are still present, treat them as stale unless there is evidence they were regenerated intentionally for the current review.
4. Base the report on concrete evidence such as:
   - JaCoCo coverage reports
   - Surefire test reports
   - SpotBugs findings
   - PMD and CPD findings
   - CycloneDX SBOM artifacts when present
   - Repository dependency-monitoring configuration such as `.github/dependabot.yml` when present
   - Direct inspection of major files when concentration or architecture risk must be explained
5. Interpret the evidence in Arachne's repository context rather than giving a generic Java quality summary.
6. When identifying coverage hotspots, prioritize substantial classes and packages over tiny exception-only or marker types unless they are architecturally important.
7. When interpreting SpotBugs or similar findings, separate likely design-noise findings from probable defect candidates. Call out repeated patterns explicitly.
8. If `quality-security` was expected to produce SBOM artifacts but none exist, report that as missing execution evidence or tooling failure rather than silently treating dependency evidence as complete.
9. In the dependency section of the report, distinguish clearly between:
   - dependency inventory evidence such as SBOM content
   - repository-side vulnerability or update signals such as Dependabot configuration or alerts
   Do not imply that SBOM alone provides advisory matching.
10. In `Notes`, include the commands you ran, whether they succeeded, and any missing or stale artifacts that limited confidence.

Focus on these repo-specific questions:

- Which packages or files concentrate too much responsibility?
- Which important paths have weak or missing test evidence?
- Are the low-coverage areas concentrated in meaningful execution paths or mostly in small support types?
- Do the findings suggest regression risk around shipped capability areas?
- Is provider-independent logic staying out of Bedrock-specific code?
- Are Spring integration responsibilities staying centered on `AgentFactory` and related wiring?
- Are static-analysis findings dominated by intentional mutable API surfaces, or do they point to likely correctness defects?
- Is dependency evidence limited to inventory only, or is there also repository-side monitoring such as Dependabot coverage?
- Are any dependency artifacts present that no longer belong to the active workflow, and if so, should they be treated as stale?
- Do samples and docs appear aligned with the code paths under scrutiny?

Respond in English unless the user explicitly asks for another language.

End with these section headings in this order:

- `Overall Assessment`
- `Observed Evidence`
- `Key Risks`
- `Priority Actions`
- `Notes`

When evidence is missing, say so explicitly in `Notes` instead of guessing.
When stale artifacts exist, say so explicitly in `Notes` and avoid using them as primary evidence.