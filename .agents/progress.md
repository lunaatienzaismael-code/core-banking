# Progress log

Append-only. One line per event: `YYYY-MM-DD · <task|phase> · <event> · <detail>`.

2026-10-12 · phase 0 · done · spec, roadmap, ADRs 0001–0007
2026-10-12 · phase 1 · done · walking skeleton merged (PR #2)
2026-10-12 · phase 2 · started · agent infrastructure (issue #3)
2026-10-12 · phase 2 · done · agent infrastructure merged (PR #4)
2026-10-12 · phase 3 · planned · UC1 → T-001..T-005
2026-10-12 · T-001 · started · build/T-001-openapi-generator (issue #5)
2026-10-12 · T-001 · reviewed · round 1 approve (0 blockers, 0 majors, 1 minor deferred to T-003)
2026-10-12 · T-001 · in_review · PR #10
2026-10-12 · T-001 · done · PR #10 merged
2026-10-12 · T-002 · started · feat/T-002-account-domain-model (issue #6)
2026-10-12 · T-002 · reviewed · round 1 approve (0 blockers, 0 majors, 2 minors fixed)
2026-10-12 · T-002 · in_review · PR #11
2026-10-12 · T-002 · done · PR #11 merged
2026-10-12 · T-003 · started · feat/T-003-open-account-endpoint (issue #7)
2026-10-12 · T-003 · reviewed · round 1 approve (0 blockers, 0 majors, 2 minors fixed, 1 nit → T-004)
2026-10-12 · T-003 · in_review · PR #12
2026-10-12 · T-003 · done · PR #12 merged
2026-10-12 · T-004 · started · feat/T-004-account-balance-endpoint
2026-10-12 · T-004 · reviewed · round 1 approve (0 blockers, 0 majors, 1 minor already covered by AccountTest, 1 nit deferred)
2026-10-12 · T-004 · in_review · PR #13
2026-10-12 · T-004 · done · PR #13 merged
2026-10-12 · T-005 · started · feat/T-005-account-movements-endpoint
2026-10-12 · T-005 · reviewed · round 1 approve (0 blockers, 0 majors, 0 minors)
2026-10-12 · T-005 · in_review · PR #14
2026-10-12 · T-005 · done · PR #14 merged
2026-10-12 · phase 3 · done · milestone closed (UC1: T-001..T-005)
2026-10-12 · phase 3 · approved by user
2026-10-12 · phase 4 · planned · conventions → T-006..T-009
2026-10-13 · T-006 · started · chore/T-006-skills-reference-examples (issue #15)
2026-10-13 · T-006 · reviewed · round 1 approve (0 blockers, 0 majors, 0 minors)
2026-10-13 · T-006 · in_review · PR #19
2026-10-13 · T-006 · done · PR #19 merged
2026-10-13 · T-007 · started · test/T-007-archunit-conventions (issue #16)
2026-10-13 · T-007 · reviewed · round 1 approve (0 blockers, 0 majors, 0 minors)
2026-10-13 · T-007 · in_review · PR #20
2026-10-13 · T-007 · done · PR #20 merged
2026-10-13 · T-008 · started · chore/T-008-persistence-skill (issue #17)
2026-10-13 · T-008 · reviewed · round 1 approve (0 blockers, 0 majors, 0 minors)
2026-10-13 · T-008 · in_review · PR #21
2026-10-13 · T-008 · done · PR #21 merged
2026-10-13 · T-009 · started · build/T-009-jacoco-thresholds (issue #18)
2026-10-13 · T-009 · reviewed · round 1 approve (0 blockers, 0 majors, 0 minors)
2026-10-13 · T-009 · in_review · PR #22
2026-10-13 · T-009 · done · PR #22 merged
2026-10-13 · phase 4 · done · T-006..T-009 merged (PRs #19–#22)
2026-10-13 · phase 4 · approved by user · close milestone #4, tag v0.1.0
2026-10-13 · phase 4 · closed · milestone #4 closed, tag v0.1.0 on a1ded52
2026-10-13 · phase 5 · planned · UC2 → T-010..T-015 (balance: materialised counters + atomic conditional update)
2026-10-13 · phase 5 · planned · T-016 PIT gate added (baseline 36/41, 88 %; 1 real survivor Money.isPositive); T-011 now depends on T-016
2026-10-13 · methodology · review findings gain defect_type; 13 historic findings classified; alerts: indirect-test, unmapped-error, unbacked-doc-claim
2026-10-13 · methodology · growth rule applied: indirect-test, unmapped-error, unbacked-doc-claim → java-testing rule 12, ArchUnit fallback rule, evaluator doc-claims check
2026-10-13 · methodology · briefs: T-014 B4 NOT_ALLOWED fixture accounts, T-015 uses them, T-012 updates the evaluator's derived-balance check
2026-10-18 · T-010 · started · branch docs/T-010-adr-materialised-balance
2026-10-18 · T-016 · started · branch build/T-016-pit-mutation-gate
2026-10-18 · T-010 · in_review · PR #32
2026-10-18 · T-016 · in_review · PR #33
2026-10-18 · T-016 · done · PR #33 merged
2026-10-18 · T-010 · done · PR #32 merged
2026-10-18 · methodology · user grants autonomous mode (commit, PR, merge myself) until Phase 10; afterwards the user merges
2026-10-18 · T-011 · started · branch feat/T-011-transaction-domain-model
2026-10-18 · T-011 · in_review · PR #34
2026-10-18 · T-011 · done · PR #34 merged
2026-10-18 · T-012 · started · branch feat/T-012-materialise-balance
2026-10-18 · methodology · user reverts merge rights: I implement and open PRs non-stop, the user merges every PR
2026-10-18 · T-012 · in_review · PR #35
2026-10-18 · T-013 · started · branch feat/T-013-record-transaction (stacked on T-012 until PR #35 merges)
2026-10-18 · phase 5 · planned · T-017 GET /transactions/{id} (#36), from the T-013 review contract-gap; growth rules: weak-assertion → java-testing rule 13, contract-gap → evaluator check 'Contract completeness'
2026-10-18 · T-012 · done · PR #35 merged
2026-10-19 · T-017 · started · branch feat/T-017-get-transaction (from main, independent of T-014/T-015)
2026-10-19 · T-017 · in_review · PR #40; growth: test-data-gap in T-004 and T-017 → java-testing rule 14
2026-10-18 · T-013 · in_review · PR #37
2026-10-18 · T-014 · started · branch feat/T-014-reject-overdrafts (stacked on T-013 until PR #37 merges)
2026-10-19 · T-013 · done · PR #37 merged
2026-10-19 · T-014 · in_review · PR #38
2026-10-18 · T-015 · started · branch test/T-015-concurrent-transactions (stacked on T-014)
2026-10-19 · T-015 · in_review · PR #39 (stacked on #38); growth: scope-creep in T-012 and T-015, no rule (unrelated causes)
