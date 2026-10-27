# 🧠 Smart Quiz — Android App

A full-featured Android quiz platform built with **Kotlin**, **XML**, and **Firebase**. Smart Quiz enables users to participate in quizzes, create and manage their own quizzes, compete through leaderboards, and provides administrative tools for platform governance.

The application uses **Cloud Firestore**, **Firebase Storage**, **Firebase Cloud Messaging**, and **Google Sign-In**, with support for multimedia questions, scenario-based questions, quiz analytics, smart descriptive answer matching, and anti-cheating mechanisms.

---

## ⚠️ Pre-Release Status

Smart Quiz is currently **pre-release**. The application works end-to-end, but several security and stability issues must be resolved before public deployment. See [Known Issues](#-known-issues) and [Security Notes](#-security-notes) below.

The three highest-priority items are:

1. **A Firebase service account key was previously shipped inside the APK** and must be revoked.
2. **Firestore security rules contain vulnerabilities** that allow users to modify privileged fields.
3. **Google Sign-In requires a valid OAuth Web Client ID** before it will function.

Details for each are provided in the setup and security sections.

---

## 📱 Features

### 👤 For All Users

**Authentication**
- Email/Password sign-up and sign-in
- Google Sign-In
- Password reset via email
- Persistent login (session survives app restart and reboot)
- Role-aware startup routing (users → Home, admins → Admin Panel)
- Banned-user guard on sign-in

**Dashboard (Home)**
- Personal greeting, avatar, and daily streak
- Real-time "Quizzes Joined" count via Firestore snapshot
- Global standings placeholder
- Quick actions: Create Quiz, Join Quiz, Leaderboard, My Quizzes, Profile, Settings, Logout
- Public quizzes section with live lifecycle status badges
- Admin card visible only to admin accounts

**Public Quiz Listing**
- Quiz title, description, creator, question count, timer mode
- Start date/time and due date/time
- Participant count with atomic increment
- Lifecycle status: UPCOMING, LIVE, COMPLETED, EXPIRED, DELETED
- Dynamic join button: Join / Start, Continue, View Result, Starts Soon, Expired

**Join Quiz**
- 6-digit quiz code
- QR code scanning
- Lifecycle validation (only LIVE quizzes joinable)
- Idempotent participant count increment (Firestore transaction)

**Quiz Attempt**
- Flexible timing: No Timer, Whole Quiz, Per Question
- Negative marking support
- Image, audio, and video questions
- Scenario questions with context banner above each sub-question
- Bookmark questions
- Mark for review
- Question navigation grid
- Auto-save of answers, states, timer, and current index to Firestore
- Resume attempts after app kill or crash
- Smart descriptive answer matching

**Results**
- Score, percentage, time taken
- Submission reason: Normal, Timer Expired, Auto-Submitted
- Score visibility respects creator's `showScoreAfterSubmission` setting

**Leaderboards**
- Global leaderboard
- Per-quiz leaderboard
- Current user highlighting
- Respects score visibility

**Profile**
- Upload profile avatar
- Change display name
- View earned badges

**Settings**
- Comprehensive in-app tutorial (42 sections)
- Change password
- Set password for Google accounts
- Logout with confirmation dialog

**Feedback**
- Rate quizzes
- Submit comments

**Push Notifications**
- FCM announcements from administrators
- Automatic token refresh

---

### 👨‍💻 For Quiz Creators

> There is no separate "Creator" role. Every authenticated `role = "user"` account can create, edit, publish, and manage their own quizzes.

**Create Quizzes**
- Title, description
- Required total question count (validated against actual questions)
- Public / Private visibility
- Timer type, whole-quiz timer, per-question timer
- Start time and deadline
- Negative marking
- Randomization: Fixed order, Random questions, Random questions + options
- Show score after submission toggle

**Question Types**
- Radio (Single Choice)
- Checkbox (Multiple Choice)
- Descriptive (Multi-line text)
- Scenario (Case study with sub-questions)

Questions support image, audio, and video URLs plus custom point values.

**Scenario Questions**
- Scenario is a container, not counted as a separate question
- Sub-question types: Radio, Checkbox, Descriptive
- Multiple scenarios per quiz
- Scenarios and normal questions can be mixed in any order
- Context banner shown above each sub-question during attempts
- Sub-questions count toward the quiz total

**Smart Descriptive Answer Matching**

Answers are normalized before comparison, ignoring harmless formatting differences:

| Correct Answer | Student Answer | Result |
|----------------|----------------|--------|
| `123 456` | `123456` | ✅ Correct |
| `1000` | `1,000` | ✅ Correct |
| `10.50` | `10.5` | ✅ Correct |
| `ABC 123` | `ABC123` | ✅ Correct |
| `New Delhi` | `new delhi` | ✅ Correct |
| `Java` | `JavaScript` | ❌ Incorrect |
| `TCP` | `UDP` | ❌ Incorrect |
| `123` | `124` | ❌ Incorrect |

Normalization pipeline:
1. Trim leading/trailing whitespace
2. Unicode NFKC normalization
3. Collapse repeated whitespace
4. Case-insensitive comparison (`Locale.ROOT`)
5. Numeric equivalence (strips `,`, `_`, `'`, spaces)
6. Alphanumeric formatting equivalence (only when both sides contain a digit)

The student's original answer and the creator's original correct answer are never modified — normalization is used only for comparison.

**Draft System**
- Save quizzes as drafts
- Edit drafts later
- Publish with validation (configured count must equal actual count)

**Quiz Statistics**
- Participant list with sort / filter / search
- Scores and time spent
- Score distribution bar chart
- Completion rate, highest, lowest, average
- Most incorrect question
- Average duration

**Data Export**
- Participant results as CSV
- Participant results as PDF
- Question paper (with or without answers) as PDF
- Questions as bulk-import-compatible CSV or XLSX
- Per-participant answer sheet PDF

**QR Code Generation**
- Generate join QR codes
- Share QR images

**Creator Analytics**
- Total participants, completion rate
- Highest and lowest scores
- Most incorrect question
- Auto-submit count
- Custom leaderboard
- Date-range filter (Today, Last 7 Days, Last 30 Days, Custom)

---

### 🛡️ For Administrators

**Admin Panel**
- Identity card with name, email, and avatar
- Overview statistics: users, quizzes, public quizzes, announcements published
- Recent quizzes feed

**User Management**
- View all users
- Promote to admin
- Ban / unban
- Block / unblock with reason
- Role and status chips

**Quiz Management**
- View all quizzes
- Filter by visibility (public / private / published)
- Search by title or quiz code
- Delete quizzes
- View per-quiz statistics

**Cheat Logs**
- Per-quiz view
- Global collection-group view
- Device model, Android version, event type, timestamp, violation count
- Suspicious event flags

**Announcements**
- Compose and send platform-wide announcements
- FCM push to all non-banned users
- Past announcements history

**Platform Analytics**
- Total users
- Total quizzes (overall, public, published)
- Total attempts
- Average score
- Cheat alert count

---

## 🚫 Anti-Cheating

**Screen Security**
- `FLAG_SECURE` on quiz screens blocks screenshots and screen recording

**Cheat Detection**

Monitored events:
- `APP_BACKGROUND` — process lifecycle
- `HOME_OR_RECENTS` — user leaves via recents
- `FOCUS_LOST` — window focus lost
- `BACK_BUTTON` — back press

Events are debounced (500 ms) to avoid duplicate logging.

**Violation System**
- Warning 1/3 — first reminder
- Warning 2/3 — final warning
- Warning 3/3 — automatic submission

**Attempt Limits**
- Completed attempts blocked from re-attempting (unless creator enables multiple attempts)

**Timer Management**
- No timer
- Whole-quiz timer (auto-submit on expiry)
- Per-question timer (locks question on expiry, moves to next)

---

## 🌐 Public Quiz Lifecycle

```
UPCOMING → LIVE → EXPIRED → ARCHIVED
```

- **UPCOMING** — start time in future; join disabled
- **LIVE** — joinable window
- **EXPIRED** — deadline passed; join blocked
- **ARCHIVED** — auto-archived 24 hours after deadline

**Archiving behavior:**
- Client-side sweep runs on every app launch
- WorkManager periodic sweep as best-effort background job
- Quiz document is not deleted — marked `archived = true`
- Summary copied to `archived_public_quizzes` for analytics
- Historical results, joined-quiz records, and cheat logs preserved
- Archived quizzes hidden from public listing; visible to creator and admins

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI | XML + Material Design 3 |
| View Binding | Android ViewBinding |
| Backend | Firebase |
| Authentication | Firebase Auth + Google Sign-In |
| Database | Cloud Firestore (offline persistence enabled) |
| Storage | Firebase Storage |
| Notifications | Firebase Cloud Messaging |
| QR Codes | ZXing + JourneyApps barcode scanner |
| Charts | MPAndroidChart |
| Image Loading | Glide |
| Video / Audio | Android VideoView / MediaPlayer |
| PDF Export | Android PdfDocument |
| CSV Export | Manual CSV generation |
| Excel | Apache POI for Android |
| Background Work | WorkManager |
| Build System | Gradle Kotlin DSL |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 34 |
| JDK | 17 |

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK API 34
- JDK 17
- Firebase project (Spark plan is sufficient for development)

### 1. Clone the repository

```bash
git clone https://github.com/pandyaomsanjay/smartquiz
cd smartquiz
```

### 2. Open in Android Studio

1. File → Open
2. Select the project folder
3. Wait for Gradle sync
4. Install missing SDK components if prompted

### 3. Create a Firebase project

1. Open [Firebase Console](https://console.firebase.google.com/)
2. Create a new project
3. Add Android app with package name `com.smartquiz`
4. Download `google-services.json`

### 4. Add Firebase config

Place `google-services.json` at:

```
app/google-services.json
```

> **Important:** Never commit `google-services.json` to a public repository. Ensure it is listed in `.gitignore`.

### 5. Enable Firebase services

In Firebase Console:

- **Authentication → Sign-in method** — enable Email/Password and Google
- **Firestore Database** — create database in production mode
- **Storage** — configure bucket
- **Cloud Messaging** — enabled by default

### 6. Configure Google Sign-In SHA-1

```bash
./gradlew signingReport
```

Copy the `SHA1` value for the debug variant. Then:

**Firebase Console → Project Settings → Your Android App → Add fingerprint**

Download the updated `google-services.json` and replace the existing one.

### 7. Set the OAuth Web Client ID

Edit `res/values/strings.xml`:

```xml
<string name="default_web_client_id">YOUR_ACTUAL_WEB_CLIENT_ID</string>
```

Find the value in **Firebase Console → Authentication → Sign-in method → Google → Web SDK configuration**.

> Google Sign-In will fail with `DEVELOPER_ERROR` until this is set to a valid client ID.

### 8. Deploy Firestore rules

```bash
firebase deploy --only firestore:rules
```

> Review the [Security Notes](#-security-notes) section before deploying. The rules in this repository require hardening for production.

### 9. Create Firestore indexes

Required composite indexes (create in Firebase Console → Firestore → Indexes):

| Collection | Fields |
|------------|--------|
| `quizzes` | `visibility ASC, status ASC, archived ASC` |
| `quizzes` | `visibility ASC, archived ASC, deadline ASC` |
| `users/{uid}/joinedQuizzes` | `joinTime DESC` |
| `quizzes/{id}/attempts` | `submitTime DESC` |

Collection group index:

| Collection Group | Fields |
|------------------|--------|
| `cheat_logs` | `creatorId ASC, timestamp DESC` |

### 10. Configure server time (recommended)

Deploy a Cloud Function or scheduled script:

```javascript
exports.updateServerTime = functions.pubsub
  .schedule('every 1 minutes')
  .onRun(async () => {
    await admin.firestore().doc('system/serverTime').set({
      now: Date.now()
    });
  });
```

Without this, `QuizTimeUtils.getServerTimeMs()` falls back to device time.

### 11. Bootstrap the first admin

In Firebase Console → Firestore:

1. Create collection `admins`
2. Document ID: `<admin-firebase-uid>`
3. Fields: `{ email: "admin@example.com", createdAt: <timestamp> }`
4. Also set `users/{uid}.role = "admin"` for UI consistency

### 12. Build and run

```bash
./gradlew assembleDebug
```

Or in Android Studio: Run → Run 'app'

---

## 📖 Tutorial Page

The in-app tutorial is a comprehensive offline help center with 42 sections covering every feature of the application.

**Features:**
- Instant search across titles, descriptions, and bullet-point content
- Category filter chips: Getting Started, Taking Quizzes, Results, Creating Quizzes, Troubleshooting
- Expandable accordion cards with smooth chevron rotation
- Persistent expansion state across filtering and scrolling
- Empty-state handling with a one-tap "Clear Search" button
- Fully offline — content is stored in Kotlin source, no network dependency

**Access:** Settings → Tutorial

---

## 🗄️ Firestore Schema

| Collection / Document | Purpose |
|-----------------------|---------|
| `users/{uid}` | Profile: name, email, role, avatar, ban/block status, fcmToken, streak |
| `users/{uid}/joinedQuizzes/{quizId}` | User's quiz history |
| `admins/{uid}` | Admin allowlist (source of truth for admin checks in rules) |
| `quizzes/{quizId}` | Quiz metadata: title, timer, visibility, lifecycle, participantCount, archived |
| `quizzes/{quizId}/questions/{qId}` | Public question data (no answers) |
| `quizzes/{quizId}/questions_private/{qId}` | Correct answers (creator + admin only) |
| `quizzes/{quizId}/attempts/{uid}` | User attempt: answers, states, timer, score |
| `quizzes/{quizId}/cheat_logs/{logId}` | Anti-cheat events |
| `results/{id}` | Global results for analytics |
| `bookmarks/{id}` | User question bookmarks |
| `feedback/{id}` | Quiz ratings |
| `announcements/{id}` | Admin announcements |
| `archived_public_quizzes/{quizId}` | Archive summaries |
| `system/serverTime` | Server timestamp (must be written by Cloud Function) |

---

## 📁 Project Structure

All classes currently live in the root `com.smartquiz` package.

```
app/
├── src/main/
│   ├── java/com/smartquiz/
│   │   ├── QuizApplication.kt
│   │   ├── MainActivity.kt
│   │   │
│   │   ├── LoginActivity.kt
│   │   ├── SignupActivity.kt
│   │   ├── ProfileSetupActivity.kt
│   │   ├── SetPasswordActivity.kt
│   │   ├── ChangePasswordActivity.kt
│   │   │
│   │   ├── HomeDashboardActivity.kt
│   │   ├── UserProfileActivity.kt
│   │   ├── SettingsActivity.kt
│   │   ├── TutorialActivity.kt
│   │   │
│   │   ├── QuestionMethodSelectionActivity.kt
│   │   ├── QuizCreationActivity.kt
│   │   ├── BulkImportActivity.kt
│   │   ├── DraftQuizzesActivity.kt
│   │   ├── CreatorDashboardActivity.kt
│   │   ├── CreatorAnalyticsActivity.kt
│   │   │
│   │   ├── JoinQuizActivity.kt
│   │   ├── QuizDetailsActivity.kt
│   │   ├── QuizInstructionsActivity.kt
│   │   ├── QuizAttemptActivity.kt
│   │   ├── ResultActivity.kt
│   │   ├── FeedbackActivity.kt
│   │   │
│   │   ├── LeaderboardActivity.kt
│   │   ├── QuizLeaderboardActivity.kt
│   │   ├── QuizStatsActivity.kt
│   │   │
│   │   ├── AdminPanelActivity.kt
│   │   ├── AdminUsersActivity.kt
│   │   ├── AdminQuizzesActivity.kt
│   │   ├── AdminCheatLogsActivity.kt
│   │   ├── AdminAnnouncementsActivity.kt
│   │   ├── AdminAnalyticsActivity.kt
│   │   ├── PastAnnouncementsActivity.kt
│   │   │
│   │   ├── MyFirebaseMessagingService.kt
│   │   │
│   │   ├── CheatLogger.kt
│   │   ├── TimerManager.kt
│   │   ├── DescriptiveAnswerMatcher.kt
│   │   ├── QuestionFileParser.kt
│   │   ├── QuizTimeUtils.kt
│   │   ├── ExpiredQuizArchiver.kt
│   │   ├── ArchiveSweepWorker.kt
│   │   ├── AuthHelper.kt
│   │   │
│   │   ├── Quiz.kt
│   │   ├── Question.kt
│   │   ├── User.kt
│   │   ├── CheatLog.kt
│   │   ├── QuestionState.kt
│   │   ├── QuizAttempt.kt
│   │   ├── QuizResult.kt
│   │   ├── LeaderboardEntry.kt
│   │   ├── LeaderboardQuizItem.kt
│   │   ├── JoinedQuiz.kt
│   │   ├── TutorialSection.kt
│   │   ├── QuizLifecycleStatus.kt
│   │   │
│   │   └── (adapters: QuizAdapter, PublicQuizAdapter, QuestionGridAdapter,
│   │       ParticipantStatsAdapter, CheatLogAdapter, TutorialSectionAdapter,
│   │       LeaderboardAdapter, LeaderboardQuizAdapter, DraftQuizAdapter,
│   │       JoinedQuizAdapter, QuestionPreviewAdapter, ImportPreviewAdapter,
│   │       AnnouncementHistoryAdapter)
│   │
│   ├── res/
│   │   ├── layout/      (60+ activity, item, and dialog layouts)
│   │   ├── drawable/    (icons, backgrounds, gradients)
│   │   ├── anim/        (slide animations)
│   │   ├── values/      (colors, strings, dimens, themes, arrays)
│   │   └── xml/         (file_paths, backup rules)
│   │
│   └── AndroidManifest.xml
│
├── build.gradle.kts
└── google-services.json    (git-ignored)
```

---

## 🔐 Security Notes

### Service Account Key — Must Be Revoked

An earlier version of this repository included `app/src/main/assets/service-account.json` — a Firebase service account with full administrative access to the project. The file has been removed, but the key itself must be revoked if it was ever exposed:

1. Open [Google Cloud Console → IAM → Service Accounts](https://console.cloud.google.com/iam-admin/serviceaccounts)
2. Locate `firebase-adminsdk-fbsvc@smartquiz-51b9d.iam.gserviceaccount.com`
3. Delete any active keys
4. Audit Firestore and Auth access logs
5. Rotate the Firebase project if misuse is suspected

**Push notifications must be sent from a Cloud Function or backend service, never from the client app.**

### Firestore Rules Require Hardening

The rules in this repository are functional but not production-ready. Known issues:

- Users can modify their own `role`, `isBanned`, `isBlocked` fields
- Any authenticated user can rewrite expired quiz metadata
- Any authenticated user can create announcements
- Any authenticated user can read all cheat logs and feedback
- Client-side score writes to `attempts` are trusted without validation
- `participantCount` can be set to arbitrary values (not just +1)
- `results` and `bookmarks` writes fail silently on first write

Production hardening should:

1. Add a field whitelist to `users/{uid}` writes
2. Restrict the archive update rule to `hasOnly(['archived', 'archivedAt', 'status'])`
3. Require `isAdmin()` for announcement creation
4. Scope cheat log and feedback reads to `creatorId == auth.uid || isAdmin()`
5. Use `request.resource.data.userId` (not `resource.data.userId`) on create rules
6. Restrict `participantCount` increments to `+1`
7. Move final score writes to a Cloud Function

### Other Recommendations

- Never use Firestore test mode in production
- Configure Firebase Storage security rules
- Do not commit `google-services.json` or any credential file
- Verify that correct answers (`questions_private`) are never exposed to non-creators
- Protect the `system/serverTime` document with write access restricted to Cloud Functions
- Test anti-cheating functionality across Android versions
- Review admin permission boundaries

---

## 🧪 Testing

### Automated Tests

Unit and instrumentation test directories exist but are currently empty. Adding coverage for `QuestionFileParser`, `DescriptiveAnswerMatcher`, `TimerManager`, and Firestore rules is a high-priority follow-up.

### Manual Test Checklist

**Authentication**
- Login → close app → reopen → Home
- Login → swipe away → reopen → Home
- Login → reboot → open app → Home
- Admin login → Admin Panel
- Explicit logout → confirmation → Login screen
- After logout, back button does not return to Home

**Public Quizzes**
- UPCOMING quiz → "Starts Soon" button, disabled
- LIVE quiz → "Join / Start" button, enabled
- EXPIRED quiz → "Expired" button, disabled
- Join quiz → participant count +1
- Re-join same quiz → count unchanged
- Complete quiz → "View Result" button

**Scenario Questions**
- Create scenario with 3 sub-questions → total +3
- Multiple scenarios sum correctly
- Mixed scenario and normal question order preserved
- Scenario banner shown above each sub-question
- Progress counter counts sub-questions
- PDF numbers sub-questions sequentially
- Score computed correctly

**Descriptive Answer Matching**
- All cases in the comparison table above

**Attempt & Anti-Cheat**
- Timer auto-submits on expiry
- Per-question timer locks question
- Autosave preserves answers on app kill
- Resume restores answers, states, timer, current index
- All cheat events detected
- 3 warnings → auto-submit
- FLAG_SECURE blocks screenshots

**Tutorial**
- Search finds relevant sections
- Category chips filter correctly
- Accordion expands/collapses without duplicating content
- Expansion persists across scroll and filter changes
- Works offline

**Drafts & Publishing**
- Save as draft → scenario preserved
- Edit draft → scenario editable
- Publish validates count
- Under/over count shows error message

---

## ⚠️ Known Issues

### Security (Blocking Release)
1. Service account key was previously in the APK — must be revoked
2. Firestore rules allow users to unban/unblock themselves
3. Firestore rules allow any user to rewrite expired quizzes
4. Firestore rules allow any user to create announcements
5. Firestore rules allow any user to read all cheat logs
6. Client-side score writes to `attempts` are not validated
7. `participantCount` can be set to any value (not just +1)
8. `default_web_client_id` is a placeholder — Google Sign-In broken

### Functionality (Silent Data Loss)
9. `results` writes fail silently — `resource.data.userId` on create
10. `bookmarks` writes fail silently — same issue
11. `AdminAnalyticsActivity` shows 0 attempts as a consequence

### Stability
12. `Tasks.await()` on main thread in `QuizCreationActivity.isTitleDuplicate()` (ANR risk)
13. `restoreAttemptFromFirestore()` race — `startQuiz()` runs before restore completes
14. Timer restore exploit — missing saved state gives fresh full timer
15. `QuizStatsActivity` listener never re-attached after `onStop()`

### Data Integrity
16. Subcollections orphaned on quiz delete (no cascade)
17. Attempt `status` always "Completed" — creator can't distinguish auto-submits
18. `allowMultipleAttempts` field is saved but never read

### UI
19. RecyclerView `match_parent` height in vertical LinearLayouts (3 screens clipped)
20. Loading spinners rendered off-screen on 3 screens
21. Two conflicting progress counters in `QuizAttemptActivity`
22. `setBackgroundColor` destroys chip drawables in 4 places
23. Hardcoded rank `#1` on Home Dashboard
24. Dead code: `SubmissionSuccessActivity`, `dialog_import_preview.xml`, `item_quiz.xml`

### Theming
25. No `values-night/colors.xml` — dark mode appearance inconsistent
26. Hardcoded colors in `themes.xml` instead of `@color/` references
27. Splash screen not using `core-splashscreen` API (Android 12+)

---

## 📋 Roadmap

### Blocking Release
- Revoke leaked service account, ensure it never returns
- Move FCM sending to Cloud Function
- Harden Firestore rules (see Security Notes)
- Fix `results` and `bookmarks` write rules
- Set `default_web_client_id` to a real OAuth client ID
- Server-side score validation via Cloud Function
- Quiz code uniqueness enforcement

### High Priority
- Move `Tasks.await()` off main thread
- Fix `restoreAttemptFromFirestore()` race condition
- Fix timer restore exploit
- Fix `QuizStatsActivity` listener lifecycle
- Chain subcollection deletion before parent delete
- Show correct attempt status in `QuizStatsActivity`
- Add `values-night/colors.xml` for dark mode
- Replace `setBackgroundColor` with `backgroundTintList`
- Fix `match_parent` RecyclerView heights
- Move loading spinners to visible positions

### Medium Priority
- Add unit tests for `QuestionFileParser`, `DescriptiveAnswerMatcher`, `TimerManager`
- Add Firestore rules tests
- Paginate admin user and quiz lists
- CSV parser support for quoted multi-line fields
- Add content descriptions for TalkBack
- Migrate to Media3 for audio/video
- Migrate to SplashScreen API (Android 12+)
- Replace `onBackPressed()` with `onBackPressedDispatcher`
- Remove dead code files
- Unify color system (remove duplicate semantic names)

### Nice to Have
- Localization
- Landscape layouts
- Tablet and foldable optimization
- Jetpack Compose migration (long-term)
- Offline queue for attempts
- Accessibility audit

---

## 📝 Changelog

### v1.6.0 — Tutorial Redesign & Security Audit
- **Tutorial Page redesign:**
  - Compact welcome card
  - Search across titles, descriptions, and bullet points
  - Category filter chips: All, Getting Started, Taking Quizzes, Results, Creating Quizzes, Troubleshooting
  - Expandable accordion cards with chevron rotation
  - Persistent expansion state across filtering and scrolling
  - Empty-state handling with one-tap Clear Search
  - `ListAdapter` + `DiffUtil` for efficient updates
- **Security audit:**
  - Documented leaked service account key
  - Identified 11 Firestore rules vulnerabilities
  - Flagged `results` and `bookmarks` silent write failures
  - Added SECURITY.md with hardened rule recommendations
- **Stability:**
  - Flagged `Tasks.await()` ANR in `QuizCreationActivity`
  - Flagged `restoreAttemptFromFirestore()` race condition
  - Flagged timer restore exploit
  - Flagged `QuizStatsActivity` listener lifecycle bug

### v1.5.0 — Descriptive Answer Improvements
- Centralized `DescriptiveAnswerMatcher`
- Multi-line descriptive input with improved spacing
- Numeric formatting equivalence (`1,000` ≡ `1000`)
- Decimal equivalence (`10.50` ≡ `10.5`)
- Alphanumeric formatting equivalence (`ABC 123` ≡ `ABC123`)
- Original answers preserved (no trim on save)

### v1.4.0 — Scenario-Based Questions
- New Scenario question type with sub-questions
- Scenario editor with add/edit/remove
- Flat navigation with scenario context banner
- Scenario-aware scoring, PDFs, analytics, and stats

### v1.3.0 — Persistent Login & Session Fix
- Removed Creator role — only USER and ADMIN
- Role-aware routing on startup
- Explicit logout with confirmation and `FLAG_ACTIVITY_CLEAR_TASK`

### v1.2.0 — Real-Time Quizzes Joined Count
- Firestore snapshot listener for joined quizzes
- Unique joined quiz counting (doc ID = quizId)
- Historical count preserved across quiz expiry

### v1.1.0 — Public Quiz Management & Auto-Expiry
- Public quiz listing with full metadata
- Lifecycle statuses: UPCOMING, LIVE, COMPLETED, EXPIRED, DELETED
- 24-hour auto-archive via client sweep and WorkManager
- Atomic participant count via Firestore transaction
- Dynamic join button states

### v1.0.0 — Initial Release
- Email/Password and Google authentication
- Quiz creation with Radio, Checkbox, Descriptive
- Public and private quizzes with 6-digit codes
- QR code scanning
- Timers, anti-cheat, bookmarks, mark for review
- Leaderboards, analytics, PDF/CSV export
- Admin panel with user and quiz management

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
   ```bash
   git checkout -b feature/amazing-feature
   ```
3. Make your changes
4. Add tests where applicable
5. Commit with a descriptive message
   ```bash
   git commit -m "feat: add amazing feature"
   ```
6. Push and open a Pull Request

**Important:** Do not introduce new client-side admin checks. Never commit credentials, `google-services.json`, or service account files. Read [Security Notes](#-security-notes) before contributing.

---

## 📄 License

This project is licensed under the **MIT License**. See the [LICENSE](./LICENSE) file for details.

---

## 🙏 Acknowledgements

Smart Quiz is built on the following open-source technologies:

- **Firebase** — Authentication, Firestore, Storage, Cloud Messaging
- **ZXing** — QR code generation and scanning
- **MPAndroidChart** — Analytics charts
- **Glide** — Image loading
- **AndroidX WorkManager** — Background archiving
- **Material Design 3** — UI system
- **Apache POI** — Excel import/export

---

<div align="center">

**Built with ❤️ by the Smart Quiz Team**

[⬆ Back to Top](#-smart-quiz--android-app)

</div>
