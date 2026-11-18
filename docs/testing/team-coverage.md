# Team coverage across all five features

Measured November 18, 2026 from final-test application code at c23769c28f737f640b7b8bb342fe7c23a6b8d90f, with the teamCoverage report task added. No application logic or tests were changed for this measurement.

Run from the project root after applying the coverage task:

```sh
./gradlew :app:testDebugUnitTest :app:teamCoverage --rerun-tasks
```

All 267 JUnit tests passed; zero failures, errors or skipped tests.

The report includes all compiled classes in the service, model and util packages, plus the UI ViewModels and their nested classes. It includes account/profile validation and history, ride management, search/location logic, membership, cost splitting, payment logic and monthly summaries. Both covered and uncovered classes in those packages count toward the totals. It excludes Android Activity/Fragment screens and Firebase/Android repository and adapter implementations. Espresso coverage data is not merged into this JVM report.

| Metric | Covered | Total | Percentage |
| --- | ---: | ---: | ---: |
| Branches | 661 | 820 | 80.6% |
| Executable lines | 1195 | 1326 | 90.1% |

Use these aggregate counts; do not average per-feature percentages. The XML is saved alongside this document as team-coverage.xml. HTML is generated at app/build/reports/jacoco/teamCoverage/html/index.html.

## Text for the submission

We used JaCoCo to measure branch coverage and executable-line coverage across the business logic for all five feature areas: account/profile management, ride management, search/locations, requests/membership, and costs/payments. The report includes the service, model and utility packages, together with the feature ViewModels.

Branch coverage measures how many decision outcomes the tests exercised. Line coverage measures how many executable source lines ran. We ran the complete merged JUnit suite, and all 267 tests passed with no failures, errors or skipped tests.

The tests covered 661 of 820 branches (80.6%) and 1,195 of 1,326 executable lines (90.1%). These percentages come from the combined JaCoCo counts, rather than an average of individual members' percentages. Some decision paths remain uncovered. Android screens and Firebase adapter implementations are outside the scope of this JVM coverage report.
