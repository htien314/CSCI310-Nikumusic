# Riya Feature 1 Testing Report

Account registration login and profile editing
Author Riya  |  Member 1  |  Verified November 16 2026

This contribution contains five executable black-box tests and five executable white-box tests for the first feature. All 10 passed with no failures or skips. Each method below is one test case; multiple assertions and input variants within a method strengthen that case rather than increasing the case count.

## Test environment and setup

The device run used the Pixel 4 Android emulator on Android 13 API 33, Espresso and the real application screens. Authentication and profile storage used local Firebase Auth and Firestore emulators under demo-trojan-account-profile. Each test starts signed out and uses a unique synthetic email and USC ID; tests do not depend on execution order.

Local white-box tests use JUnit 4, Mockito 5.23.0 and synchronous Task fixtures. The Firebase SDK and repository are mocked for these tests. JaCoCo 0.8.14 measures coverage. The repository requests JDK 25 for Gradle; install the Android SDK and sync Gradle before running.

## Execution commands

**B Black-box suite** Start an Android virtual device, then run the following from the project root. Set ANDROID_SERIAL if it is not emulator-5554. The Firebase CLI must be installed and on PATH, or set FIREBASE_CLI to its executable. Ports 8080 and 9099 must be free.

```sh
node scripts/test-account-profile.cjs
```

The script creates a disposable build with a separate application ID, enables emulator connections, starts a fresh demo backend, installs the APKs and runs all five BB methods. It succeeds only when all five pass. The test class refuses to mutate a non-demo build. Output is saved in .firebase/account-profile/espresso.txt.

**W White-box suite and coverage** Run only this author’s five methods when generating the coverage figures below.

```sh
./gradlew :app:testDebugUnitTest --tests '*AccountProfileWhiteBoxTest' :app:accountProfileCoverage --rerun-tasks
```

To execute one WB method, replace the --tests value with its fully qualified class and method: com.example.trojanrideshare.service.AccountProfileWhiteBoxTest.METHOD. HTML and XML coverage reports are generated under app/build/reports/jacoco/accountProfileCoverage/.

## Verification summary

Five Espresso cases passed in 48.504 seconds. Five assigned JUnit cases passed. The complete local JVM regression suite also passed all 245 tests, with zero failures, errors or skips. Android lint passed with existing warnings. These results establish the tested local behavior; they do not measure coverage of the entire application or validate a live deployment.

# 4 Black box Testing

**Author** Riya for BB01 through BB05.

**Folder and file** app/src/androidTest/java/com/example/trojanrideshare/AccountProfileBlackBoxTest.java

Execution for every entry: run command B on the preceding page. The named method identifies the executable test in that file. Assertions below use observable UI outcomes.

## BB01 Valid registration

**Method** bb01_validRegistrationShowsTheSignedInStudentProfile

**Description** Enter Student One, nickname Traveler, a unique @usc.edu email, a unique 10-digit USC ID, next year as graduation year and LocalTest123! as the password. Submit the real registration form. The profile shows the expected name, email, ID, graduation year and nickname.

**Rationale** Valid equivalence class and use-case testing establish the normal end-to-end account creation flow.

**Result and bugs** PASS. No product bug was uncovered by this case.

## BB02 Reject a non USC email

**Method** bb02_nonUscEmailShowsValidationAndKeepsRegistrationOpen

**Description** Submit an otherwise valid form with student@gmail.com. Check the USC email validation message, unchanged email field and enabled submit button. Replace it with a unique @usc.edu address and verify registration succeeds.

**Rationale** Invalid and valid email partitions check USC-only validation and recovery after correcting an error.

**Result and bugs** PASS. No product bug was uncovered by this case.

## BB03 USC ID length boundary

**Method** bb03_uscIdRequiresTenDigitsAndBlocksAnEleventhDigit

**Description** Submit a nine-digit ID and verify the exact 10-digit validation message. Enter an 11-digit value; verify the field blocks the last digit and retains the first 10. Submit and verify the saved 10-digit ID appears on the profile.

**Rationale** Boundary analysis uses 9, 10 and 11 digits. The UI enforces its upper boundary by limiting input; it does not display an 11-digit rejection message.

**Result and bugs** PASS. No product bug was uncovered by this case.

## BB04 Incorrect login password

**Method** bb04_wrongPasswordShowsAnErrorWithoutSigningIn

**Description** Create an account through the UI, log out and try its email with WrongPassword123!. Verify the login failure message and re-enabled button. Use the visible back button and verify the profile remains signed out. Log in with the correct password and verify the profile loads.

**Rationale** Invalid and valid credential partitions check failed authentication, signed-out state and a successful retry using an account known to exist.

**Result and bugs** PASS. No product bug was uncovered by this case.

## BB05 Edit and persist a nickname

**Method** bb05_nicknameRejectsBlankAndPersistsAfterLoggingInAgain

**Description** Register through the UI, submit a whitespace-only nickname and verify Nickname cannot be empty. Save Traveler One and verify success. Log out and log in again; the profile nickname remains Traveler One.

**Rationale** Invalid and valid nickname partitions plus a persistence use case check validation and a real save followed by an authenticated reload.

**Result and bugs** PASS. No product bug was uncovered by this case.

# 5 White box Testing

**Author** Riya for WB01 through WB05.

**Folder and file** app/src/test/java/com/example/trojanrideshare/service/AccountProfileWhiteBoxTest.java

Execution for every entry: run command W, or select the named method using the fully qualified class and method filter described on page 1.

## WB01 Valid registration path

**Method** wb01_validRegistrationNormalizesFieldsAndReturnsSavedProfile

**Description** Inject mocked FirebaseAuth and UserRepository. Complete account creation and profile saving successfully. Register once with a supplied nickname and once with a blank nickname. Verify both saved User objects and the success callbacks, including UID, trimmed name, normalized email, ID and graduation year.

**Coverage rationale** Exercises successful validation, nickname selection versus full-name fallback, the non-null Firebase user path and profile-save success. Checks the current-year and current-year-plus-six valid endpoints.

**Result and bugs** PASS. No product bug was uncovered by this case.

## WB02 Email rejection branches

**Method** wb02_invalidEmailStopsBeforeAuthenticationOrProfileWrites

**Description** Try null, empty, non-USC, misleading USC-suffix and oversized email values while other fields are valid. Verify five IllegalArgumentException callbacks with the exact email validation message. Verify no authentication calls, profile writes or success callbacks occur. A valid email passes the validation control.

**Coverage rationale** Exercises the null-email, maximum-length and email-pattern rejection conditions, plus valid continuation.

**Result and bugs** PASS. No product bug was uncovered by this case.

## WB03 USC ID rejection branches

**Method** wb03_uscIdGuardsRejectNineElevenAndNondigitInputs

**Description** Try null, nine-digit, 11-digit and 10-character nonnumeric IDs. Verify the exact USC ID error for each input and no authentication calls, profile writes or success callbacks. A 10-digit ID passes the validation control.

**Coverage rationale** Exercises the null-ID and numeric-format guards with both length boundaries. Unlike the UI, the validator receives the full 11-character value.

**Result and bugs** PASS. No product bug was uncovered by this case.

## WB04 Login callback and guard paths

**Method** wb04_loginPropagatesFailureAndSuccessAndRejectsMissingCredentials

**Description** Use synchronous mocked Tasks to deliver one incorrect-password failure and one successful AuthResult. Verify the correct listener receives each original result and email whitespace is trimmed. Try null/blank email and null/empty password; verify validation errors and no additional sign-in calls.

**Coverage rationale** Exercises login credential guards, authentication failure and success callbacks, and the absence of profile-repository side effects. Firebase server authentication itself is checked by BB04.

**Result and bugs** PASS. No product bug was uncovered by this case.

## WB05 Nickname validation branches

**Method** wb05_nicknameValidationCoversEmptyAndLengthBranchesBeforeSaving

**Description** Validate null, whitespace-only, ordinary padded, 100-character and 101-character nicknames. Verify the exact rejection messages or null for valid values. Submit the 101-character nickname to registration; verify the error callback and no authentication calls, profile writes or success callbacks.

**Coverage rationale** Exercises empty-name and length decision outcomes, including the 100/101 boundary, and AuthService’s pre-authentication nickname guard. The actual profile update and persistence are checked by BB05.

**Result and bugs** PASS. No product bug was uncovered by this case.

# White box coverage and changes

## Coverage criteria

Branch coverage is the primary structural criterion: covered decision outcomes divided by total decision outcomes in the measured classes. The tests deliberately select valid and invalid inputs, successful and failed callbacks, and early returns that prevent authentication or persistence side effects. JaCoCo executable-line coverage is a secondary metric; line coverage is not an exact statement-coverage measure.

The scope is the complete AuthService and RegistrationValidator classes, including their uncovered methods. Measurements come only from WB01–WB05. Espresso execution and the other 240 JVM tests do not contribute to these figures. The selected suite was rerun with --rerun-tasks before generating the report.

| Measured scope | Branches covered | Lines covered |
|---|---|---|
| AuthService | 16 / 26  (61.5%) | 36 / 61  (59.0%) |
| RegistrationValidator | 23 / 30  (76.7%) | 13 / 18  (72.2%) |
| Combined | 39 / 56  (69.6%) | 49 / 79  (62.0%) |

The combined percentages use the summed covered and total counts, not an average of the class percentages. The measured suite does not achieve full branch or path coverage. Uncovered paths include registration cleanup after a profile-save failure, account-collision handling, a missing Firebase user result, session helper methods and some name, password and graduation-year validation failures. Those paths require additional tests beyond this assigned set.

## Bugs and implementation changes

No application defect was reproduced by these 10 cases, so no product bug fix is claimed. AuthService received a package-private constructor accepting FirebaseAuth and UserRepository so JUnit can inject mocks. The public no-argument constructor still uses the same real Firebase dependencies. This is a testability change, not a change to registration or login behavior.

The test branch also adds the Mockito test dependency, a subclass mock-maker setting that avoids a dynamically attached agent, the JaCoCo report task and the isolated device runner. Production Firebase configuration, security rules and UI behavior were not changed. BB03 documents the existing 10-character UI limit accurately rather than expecting an error for a character the field cannot retain.

## Evidence and report handoff

Source-controlled evidence: docs/testing/riya-feature-1-results.json records the execution summary and coverage counts; docs/testing/riya-feature-1-coverage.xml contains the selected-suite JaCoCo report. Fresh test runs regenerate detailed reports in app/build/ and the local Espresso log in .firebase/.

Copy Section 4 entries into the team’s Black-box Testing section and Section 5 entries plus this coverage explanation into White-box Testing. This is Riya’s contribution of 5 black-box and 5 white-box cases toward the team’s required 25 of each. Keep the author label when combining the report.
