# 🧠 Smart Quiz – Android App

**Smart Quiz** is a full-featured Android quiz platform that allows users to participate in quizzes, create and manage their own quizzes, compete through leaderboards, and provides administrative tools for managing the platform.

The application is built using **Kotlin**, **XML**, and **Firebase**, with support for authentication, Cloud Firestore, Firebase Storage, push notifications, multimedia questions, scenario-based questions, quiz analytics, smart descriptive answer matching, and anti-cheating mechanisms.

---

## 📱 Features

### 👤 For All Users

* **Authentication**
  * Email/Password authentication
  * Google Sign-In
  * Password reset
  * **Persistent login** — user stays signed in until explicit logout
  * Role-aware startup routing: `USER → Home`, `ADMIN → Admin Panel`
  * No passwords stored locally (Firebase Auth SDK handles credentials)

* **Dashboard (Home)**
  * Personal greeting, avatar, and daily streak
  * **Real-time "Quizzes Joined"** count (public + private, deduplicated)
  * Global standings placeholder
  * Quick actions: Create Quiz, Join Quiz, Leaderboard, My Quizzes, Profile, Settings, Logout
  * **Public Quizzes** section with live lifecycle status badges
  * Admin card visible only to admin accounts

* **Public Quiz Listing**
  * Quiz title, description, creator name
  * Number of questions, total marks, timer mode
  * **Start date/time** and **due date/time**
  * **Participant count** (atomic increment)
  * Lifecycle status: **UPCOMING**, **LIVE**, **COMPLETED**, **EXPIRED**, **DELETED**
  * Dynamic join button: `Join / Start`, `Continue`, `View Result`, `Starts Soon`, or `Expired`

* **Join Quiz**
  * Join using a 6-digit quiz code
  * Scan a QR code to join
  * Auto-verified against quiz lifecycle (only LIVE quizzes joinable)
  * Participant count incremented only on first join (Firestore transaction, no double-counting)

* **Quiz Attempt**
  * Timed quizzes (No Timer, Whole Quiz, Per Question)
  * Negative marking support
  * Image, audio, and video questions
  * **Scenario questions** with context banner above each sub-question
  * Bookmark questions
  * Mark questions for review
  * Question navigation grid
  * **Auto-save** — answers, states, timer, and current index persisted to Firestore
  * **Resume** — attempt restored on app restart
  * **Smart descriptive answer matching** (see below)

* **Results**
  * Score, percentage, time taken
  * Submission reason (Normal, Timer Expired, Auto-Submitted)
  * Timer-expiry status
  * Cheat-warning status

* **Leaderboards**
  * Global leaderboard
  * Per-quiz leaderboard
  * Highlights current user's ranking
  * Respects `showScoreAfterSubmission` visibility

* **Profile**
  * Upload profile avatar
  * Change display name
  * View earned badges

* **Settings**
  * App tutorial (updated with all new features)
  * Change password
  * Delete account
  * Logout (with confirmation dialog)

* **Feedback**
  * Rate quizzes
  * Submit comments and feedback

* **Push Notifications**
  * Receive announcements through Firebase Cloud Messaging (FCM)

---

## 👨‍💻 Features for Quiz Creators

> **Note:** There is **no separate "Creator" role.** Every authenticated user (`role = "user"`) can create, edit, publish, and manage their own quizzes. Admins have additional moderation tools.

### Create Quizzes

Creators can configure:

* Quiz title
* Description
* **Total Questions (required)** — validated against actual question count
* Public / private visibility
* Quiz timer mode
* Whole-quiz timer
* Per-question timer
* Start date/time
* Deadline
* Negative marking
* Randomization: Fixed / Random Questions / Random Questions + Options
* Score visibility

### Add Questions

Supported question types:

* **Radio** (Single Choice)
* **Checkbox** (Multiple Choice)
* **Descriptive** (Multi-line text)
* **Scenario** (Case study with sub-questions)

Questions can also contain:

* Images
* Audio
* Video
* Custom point values

### Scenario Questions

* **Scenario** is a container/case study with multiple sub-questions.
* The scenario itself is **NOT** counted as a separate question.
* Sub-question types: Radio, Checkbox, Descriptive.
* Multiple scenarios can be added to a single quiz.
* Scenarios and normal questions can be **mixed in any order**.
* The scenario context is displayed above every related sub-question during the attempt.
* Scenario sub-questions count toward the quiz total.

### Smart Descriptive Answer Matching

Descriptive answers use a centralized `DescriptiveAnswerMatcher` that ignores harmless formatting differences:

| Correct Answer | Student Answer | Result |
|----------------|----------------|--------|
| `123 456` | `123456` | ✅ Correct |
| `123456` | `123 456` | ✅ Correct |
| `1000` | `1,000` | ✅ Correct |
| `10.50` | `10.5` | ✅ Correct |
| `ABC 123` | `ABC123` | ✅ Correct |
| `New Delhi` | `new delhi` | ✅ Correct |
| `Java` | `JavaScript` | ❌ Incorrect |
| `TCP` | `UDP` | ❌ Incorrect |
| `123` | `124` | ❌ Incorrect |

**Normalization pipeline:**
1. Trim leading/trailing whitespace
2. Unicode NFKC normalization
3. Collapse repeated whitespace (spaces, tabs, newlines)
4. Case-insensitive (`Locale.ROOT`)
5. Numeric equivalence (strips `,`, `_`, `'`, spaces)
6. Alphanumeric formatting equivalence (only when both sides contain a digit)

The student's original answer and the creator's original correct answer are **never modified** — normalization is used only for comparison.

### Draft System

* Save quizzes as drafts
* Edit drafts later
* Publish quizzes when ready
* Scenarios work in drafts exactly like published quizzes

### Quiz Statistics

Creators can view:

* Participant list with sort/filter/search
* Participant scores and time spent
* Score distribution bar chart
* Completion rate
* Highest / lowest score
* Most incorrect question
* Average duration

### Data Export

* Export results as CSV
* Export results as PDF
* Download question papers (with answers / without answers)
* Download answer sheet PDF for each participant

### QR Code

Generate a QR code to allow users to quickly join a quiz.

### Creator Analytics

Creators can view:

* Total participants
* Completion rate
* Highest score
* Lowest score
* Most incorrect question
* Auto-submit count
* Custom leaderboard
* Date range filter (Today, Last 7 Days, Last 30 Days, Custom)

---

## 🛡️ Administrator Features

### Admin Panel

Administrators can:

* View registered users
* Block users (with reason)
* Ban users
* View admin analytics

### Quiz Management

* View / manage all quizzes
* Delete quizzes when required

### Cheat Logs

Monitor suspicious activity including:

* App minimization (`APP_BACKGROUND`)
* Back button activity (`BACK_BUTTON`)
* Focus loss (`FOCUS_LOST`)
* App switching (`HOME_OR_RECENTS`)

Each log includes user details, quiz, device model, Android version, timestamp, and violation count.

### App Analytics

View:

* Total users
* Total quizzes
* Total attempts
* Average score

### Announcements

Administrators can send announcements to users through Firebase Cloud Messaging.

---

## 🚫 Anti-Cheating & Integrity

Smart Quiz includes several mechanisms to improve quiz integrity.

### 🔒 Screen Security

`FLAG_SECURE` is used to prevent screenshots and screen recording during protected quiz screens.

### 🚨 Cheat Detection

The application monitors events such as:

* App switching
* Back button usage
* Home / Recents navigation
* Window focus loss
* App minimization

Events are debounced (500 ms) to avoid duplicate logging.

### ⚠️ Violation System

Users receive progressive warnings for suspicious activity:

* Warning 1/3 — first reminder
* Warning 2/3 — final warning
* Warning 3/3 — automatic submission

### 🔢 Attempt Limits

Multiple quiz completions are restricted unless the quiz creator enables multiple attempts.

### ⏱️ Timer Management

The application supports:

* No timer
* Whole-quiz timer
* Per-question timer (locks question on expiry, moves to next)
* Automatic submission when the whole-quiz timer expires

Scenario sub-questions use the same timer configuration as normal questions.

---

## 🌐 Public Quiz Lifecycle & Auto-Expiry

Public quizzes have a full lifecycle managed by the app:

```
UPCOMING  →  LIVE  →  EXPIRED  →  ARCHIVED
```

* **UPCOMING** — start time is in the future; join button disabled
* **LIVE** — joinable window
* **EXPIRED** — deadline passed; join blocked
* **ARCHIVED** — automatically archived 24 hours after the due time

**Archiving behavior:**
* A client-side sweep runs on every app launch (`ExpiredQuizArchiver`).
* A `WorkManager` job runs periodically as a best-effort background sweep.
* The quiz document is **not deleted** — it is marked `archived = true` and a summary is copied to `archived_public_quizzes`.
* Historical results, analytics, joined-quiz records, and cheat logs remain intact.
* Archived quizzes are hidden from the public listing but visible to the creator and admins.

Firestore rules also block new attempts on expired quizzes as an authoritative server-side guard.

---

# 🛠️ Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI | XML + Material Design 3 |
| View Binding | Android ViewBinding |
| Backend | Firebase |
| Authentication | Firebase Authentication |
| Google Authentication | Google Sign-In |
| Database | Cloud Firestore |
| Storage | Firebase Storage |
| Notifications | Firebase Cloud Messaging (FCM) |
| QR Code | ZXing |
| Charts | MPAndroidChart |
| Image Loading | Glide |
| Video / Audio | AndroidX Media3 / ExoPlayer |
| PDF Export | Android PdfDocument |
| CSV Export | Manual CSV Generation |
| Background Work | AndroidX WorkManager |
| Build System | Gradle Kotlin DSL |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 34 |

---

# 🚀 Getting Started

## Prerequisites

* **Android Studio** Jellyfish (2023.3.1) or later
* **Android SDK API 34**
* Android SDK Platform-Tools
* Android Emulator or Android device
* A Firebase project

A Firebase Spark plan can be used for development.

---

## 1. Clone the Repository

```bash
git clone https://github.com/pandyaomsanjay/smartquiz
cd smartquiz
```

---

## 2. Open the Project in Android Studio

1. Open **Android Studio**.
2. Select **Open**.
3. Select the Smart Quiz project folder.
4. Wait for Gradle synchronization to complete.
5. If Android Studio asks to install missing SDK components, install them.

---

# 🔥 Firebase Configuration

Smart Quiz uses Firebase for authentication, database, storage, and notifications.

The application requires:

* Firebase Authentication
* Cloud Firestore
* Firebase Storage
* Firebase Cloud Messaging
* Google Sign-In

---

## 3. Create a Firebase Project

Open the **Firebase Console**:

https://console.firebase.google.com/

1. Create a new Firebase project.
2. Open the project.
3. Click **Add App**.
4. Select **Android**.

---

## 4. Register the Android Application

Use the following package name:

```text
com.smartquiz
```

Register the application.

Download `google-services.json` and place it inside:

```text
app/google-services.json
```

> Do **not** commit your real `google-services.json` to a public repository.

---

# 🔐 Firebase Authentication

Go to **Firebase Console → Authentication → Sign-in method**.

Enable:

### Email/Password

Enable the **Email/Password** provider.

### Google Sign-In

Enable the **Google** provider.

Select the appropriate support email and save the configuration.

---

# 🔑 Google Sign-In SHA-1

Configure the SHA-1 fingerprint for your Android application.

From the Android project terminal, run:

### Windows

```powershell
.\gradlew signingReport
```

Find the debug variant and copy the **SHA1** value.

Then go to **Firebase Console → Project Settings → Your Android App** and add the SHA-1 fingerprint.

Download the updated `google-services.json` and replace `app/google-services.json`.

---

# 🗄️ Cloud Firestore

Go to **Firebase Console → Firestore Database** and create a Firestore database.

For initial development, you may use test mode, but configure proper security rules before deploying the application.

### Recommended Composite Indexes

Create the following indexes when prompted by Logcat errors:

| Collection | Fields |
|------------|--------|
| `quizzes` | `visibility ASC, status ASC, archived ASC` |
| `quizzes/{id}/attempts` | `submitTime DESC` |
| `quizzes/{id}/cheat_logs` | `timestamp DESC` |
| `users/{id}/joinedQuizzes` | `joinTime DESC` |

### Optional: Server Time Document

For client-side lifecycle calculation, create a Firestore document:

```
system/serverTime  {  now: <number>  }
```

This is used to compare against quiz deadlines without relying on the device clock.

---

# 📦 Firebase Storage

Go to **Firebase Console → Storage** and configure Firebase Storage.

Storage is used for application media such as:

* Profile avatars
* Quiz images
* Audio
* Video

Configure appropriate Storage security rules before production deployment.

---

# 🔔 Firebase Cloud Messaging

Go to **Firebase Console → Project Settings → Cloud Messaging** and make sure Firebase Cloud Messaging is enabled.

---

# 🔐 Firestore Security Rules

Go to **Firebase Console → Firestore Database → Rules** and paste:

```javascript
rules_version = '2';

service cloud.firestore {

  function isAdmin() {
    return request.auth != null &&
      get(/databases/$(database)/documents/users/$(request.auth.uid))
        .data.role == "admin";
  }

  match /databases/{database}/documents {

    // ===============================================================
    // USERS
    // ===============================================================
    match /users/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null &&
        (request.auth.uid == userId || isAdmin());

      match /joinedQuizzes/{quizId} {
        allow read, write: if request.auth != null &&
          request.auth.uid == userId;
      }
    }

    // ===============================================================
    // QUIZZES
    // ===============================================================
    match /quizzes/{quizId} {

      // Any authenticated user can read quizzes that are:
      //   • public, or
      //   • have a non-empty quizCode, or
      //   • owned by the caller, or
      //   • admin
      // (Archived quizzes are hidden from public listing by the client.)
      allow read: if request.auth != null && (
        resource.data.visibility == "public" ||
        (resource.data.quizCode is string && resource.data.quizCode != "") ||
        resource.data.creatorId == request.auth.uid ||
        isAdmin()
      );

      allow create: if request.auth != null;

      allow update: if request.auth != null && (
        request.auth.uid == resource.data.creatorId ||
        isAdmin() ||
        (
          // Any authenticated user may archive an expired quiz
          request.resource.data.archived == true &&
          resource.data.archived == false &&
          resource.data.deadline + (24 * 60 * 60 * 1000) <= request.time.toMillis()
        )
      );

      allow delete: if request.auth != null && (
        request.auth.uid == resource.data.creatorId || isAdmin()
      );

      // ---------- PUBLIC QUESTIONS ----------
      match /questions/{questionId} {
        allow read: if request.auth != null;
        allow write: if request.auth != null && (
          request.auth.uid ==
            get(/databases/$(database)/documents/quizzes/$(quizId))
              .data.creatorId ||
          isAdmin()
        );
      }

      // ---------- PRIVATE QUESTIONS ----------
      match /questions_private/{questionId} {
        allow read, write: if request.auth != null && (
          request.auth.uid ==
            get(/databases/$(database)/documents/quizzes/$(quizId))
              .data.creatorId ||
          isAdmin()
        );
      }

      // ---------- ATTEMPTS ----------
      match /attempts/{userId} {

        allow read: if request.auth != null && (
          request.auth.uid == userId ||
          request.auth.uid ==
            get(/databases/$(database)/documents/quizzes/$(quizId))
              .data.creatorId ||
          isAdmin() ||
          get(/databases/$(database)/documents/quizzes/$(quizId))
            .data.status == "PUBLISHED"
        );

        allow create, update: if request.auth != null &&
          request.auth.uid == userId;

        allow delete: if false;
      }

      // ---------- CHEAT LOGS ----------
      match /cheat_logs/{logId} {
        allow write: if request.auth != null;
        allow read: if request.auth != null && (
          isAdmin() ||
          (
            resource.data.quizId is string &&
            resource.data.quizId != "" &&
            exists(
              /databases/$(database)/documents/quizzes/$(resource.data.quizId)
            ) &&
            get(
              /databases/$(database)/documents/quizzes/$(resource.data.quizId)
            ).data.creatorId == request.auth.uid
          )
        );
      }
    }

    // ===============================================================
    // ARCHIVED PUBLIC QUIZZES
    // ===============================================================
    match /archived_public_quizzes/{quizId} {
      allow read: if request.auth != null &&
        (isAdmin() || resource.data.creatorId == request.auth.uid);
      allow write: if request.auth != null;
    }

    // ===============================================================
    // SYSTEM (server time)
    // ===============================================================
    match /system/{docId} {
      allow read: if request.auth != null;
      allow write: if false;
    }

    // ===============================================================
    // GLOBAL RESULTS
    // ===============================================================
    match /results/{resultId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null &&
        request.auth.uid == resource.data.userId;
    }

    // ===============================================================
    // LEGACY
    // ===============================================================
    match /quiz_attempts/{attemptId} {
      allow read, write: if request.auth != null &&
        (request.auth.uid == resource.data.userId || isAdmin());
    }

    match /activeAttempts/{attemptId} {
      allow read, write: if request.auth != null;
    }

    // ===============================================================
    // BOOKMARKS
    // ===============================================================
    match /bookmarks/{bookmarkId} {
      allow read, write: if request.auth != null &&
        (request.auth.uid == resource.data.userId || isAdmin());
    }

    // ===============================================================
    // FEEDBACK
    // ===============================================================
    match /feedback/{feedbackId} {
      allow read, write: if request.auth != null;
    }

    // ===============================================================
    // ANNOUNCEMENTS
    // ===============================================================
    match /announcements/{announceId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && isAdmin();
    }
  }
}
```

> **Security Note:** Review and test these rules against the exact Firestore queries used by the application before production deployment.

---

# 📦 Key Dependencies

```gradle
// Firebase
implementation("com.google.firebase:firebase-firestore-ktx")
implementation("com.google.firebase:firebase-auth-ktx")
implementation("com.google.firebase:firebase-storage-ktx")
implementation("com.google.firebase:firebase-messaging-ktx")

// Google Sign-In
implementation("com.google.android.gms:play-services-auth:20.7.0")

// QR Code
implementation("com.journeyapps:zxing-android-embedded:4.3.0")
implementation("com.google.zxing:core:3.5.3")

// Charts
implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

// Image Loading
implementation("com.github.bumptech.glide:glide:4.16.0")
annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

// Media
implementation("androidx.media3:media3-exoplayer:1.2.0")

// Background Work
implementation("androidx.work:work-runtime-ktx:2.9.0")
```

> Complete dependency configuration is available in `app/build.gradle.kts`.

---

# ▶️ Build and Run

After configuring Firebase:

1. Open the project in Android Studio.
2. Wait for Gradle Sync.
3. Connect an Android device or start an emulator.
4. Enable USB debugging if using a physical device.
5. Click **Run ▶**.

Alternatively:

```bash
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew assembleDebug
```

Install the debug APK on a connected device:

```powershell
.\gradlew installDebug
```

---

# 📁 Project Structure

```text
SmartQuiz/
│
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/
│   │   │   │       └── smartquiz/
│   │   │   │           ├── activities/
│   │   │   │           │   ├── MainActivity.kt
│   │   │   │           │   ├── LoginActivity.kt
│   │   │   │           │   ├── SignupActivity.kt
│   │   │   │           │   ├── ProfileSetupActivity.kt
│   │   │   │           │   ├── HomeDashboardActivity.kt
│   │   │   │           │   ├── UserProfileActivity.kt
│   │   │   │           │   ├── SettingsActivity.kt
│   │   │   │           │   ├── TutorialActivity.kt
│   │   │   │           │   ├── QuizCreationActivity.kt
│   │   │   │           │   ├── DraftQuizzesActivity.kt
│   │   │   │           │   ├── JoinQuizActivity.kt
│   │   │   │           │   ├── QuizInstructionsActivity.kt
│   │   │   │           │   ├── QuizAttemptActivity.kt
│   │   │   │           │   ├── ResultActivity.kt
│   │   │   │           │   ├── QuizDetailsActivity.kt
│   │   │   │           │   ├── QuizStatsActivity.kt
│   │   │   │           │   ├── CreatorAnalyticsActivity.kt
│   │   │   │           │   ├── QuizLeaderboardActivity.kt
│   │   │   │           │   ├── LeaderboardActivity.kt
│   │   │   │           │   ├── FeedbackActivity.kt
│   │   │   │           │   ├── SubmissionSuccessActivity.kt
│   │   │   │           │   ├── AdminPanelActivity.kt
│   │   │   │           │   ├── AdminQuizzesActivity.kt
│   │   │   │           │   ├── AdminAnalyticsActivity.kt
│   │   │   │           │   ├── AdminAnnouncementsActivity.kt
│   │   │   │           │   └── AdminCheatLogsActivity.kt
│   │   │   │           ├── adapters/
│   │   │   │           │   ├── QuizAdapter.kt
│   │   │   │           │   ├── PublicQuizAdapter.kt
│   │   │   │           │   ├── QuestionPreviewAdapter.kt
│   │   │   │           │   ├── QuestionGridAdapter.kt
│   │   │   │           │   ├── DraftQuizAdapter.kt
│   │   │   │           │   ├── JoinedQuizAdapter.kt
│   │   │   │           │   ├── LeaderboardAdapter.kt
│   │   │   │           │   ├── LeaderboardQuizAdapter.kt
│   │   │   │           │   ├── ParticipantStatsAdapter.kt
│   │   │   │           │   ├── CheatLogAdapter.kt
│   │   │   │           │   ├── ScenarioSubQuestionAdapter.kt
│   │   │   │           │   └── TutorialSectionAdapter.kt
│   │   │   │           ├── models/
│   │   │   │           │   ├── Quiz.kt
│   │   │   │           │   ├── Question.kt
│   │   │   │           │   ├── User.kt
│   │   │   │           │   ├── CheatLog.kt
│   │   │   │           │   ├── QuestionState.kt
│   │   │   │           │   ├── QuizAttempt.kt
│   │   │   │           │   ├── QuizResult.kt
│   │   │   │           │   ├── LeaderboardEntry.kt
│   │   │   │           │   ├── LeaderboardQuizItem.kt
│   │   │   │           │   ├── JoinedQuiz.kt
│   │   │   │           │   ├── TutorialSection.kt
│   │   │   │           │   └── QuizLifecycleStatus.kt
│   │   │   │           ├── services/
│   │   │   │           │   └── MyFirebaseMessagingService.kt
│   │   │   │           └── utils/
│   │   │   │               ├── CheatLogger.kt
│   │   │   │               ├── TimerManager.kt
│   │   │   │               ├── DescriptiveAnswerMatcher.kt
│   │   │   │               ├── QuizTimeUtils.kt
│   │   │   │               ├── ExpiredQuizArchiver.kt
│   │   │   │               ├── ArchiveSweepWorker.kt
│   │   │   │               └── QuizApplication.kt
│   │   │   │
│   │   │   ├── res/
│   │   │   │   ├── drawable/
│   │   │   │   ├── layout/
│   │   │   │   ├── values/
│   │   │   │   ├── anim/
│   │   │   │   └── xml/
│   │   │   │
│   │   │   └── AndroidManifest.xml
│   │   │
│   │   ├── test/
│   │   └── androidTest/
│   │
│   ├── build.gradle.kts
│   └── google-services.json
│
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── screenshots/
│   ├── splash.png
│   ├── login.png
│   ├── home.png
│   ├── public.png
│   ├── create.png
│   ├── scenario.png
│   ├── attempt.png
│   ├── result.png
│   ├── analytics.png
│   ├── leaderboard.png
│   ├── cheat_logs.png
│   └── admin.png
│
├── .gitignore
├── LICENSE
└── README.md
```

---

# 🗄️ Firestore Schema

| Collection / Document | Purpose |
|-----------------------|---------|
| `users/{userId}` | User profile: name, email, role, avatar, ban status |
| `users/{userId}/joinedQuizzes/{quizId}` | User's joined-quiz history |
| `quizzes/{quizId}` | Quiz metadata: title, timer, visibility, lifecycle, participant count, `archived`, `startTime`, `configuredQuestionCount` |
| `quizzes/{quizId}/questions/{qId}` | Public questions (with nested `subQuestions` for scenarios) |
| `quizzes/{quizId}/questions_private/{qId}` | Correct answers (`subAnswers` map for scenarios) |
| `quizzes/{quizId}/attempts/{userId}` | User's attempt: answers, score, status, timer state, `questionOrder`, `optionOrder` |
| `quizzes/{quizId}/cheat_logs/{logId}` | Anti-cheat events per quiz |
| `results/{resultId}` | Global results for analytics |
| `feedback/{feedbackId}` | Quiz ratings and comments |
| `announcements/{id}` | Admin announcements |
| `bookmarks/{bookmarkId}` | User question bookmarks |
| `archived_public_quizzes/{quizId}` | Long-term summary of archived quizzes |
| `system/serverTime` | Cached server timestamp for client-side lifecycle |

---

# 🧪 Testing

The project supports:

### Unit Tests

```text
app/src/test/
```

### Instrumentation Tests

```text
app/src/androidTest/
```

### Firebase Emulator Suite

Useful for local testing of Firebase services:

* Authentication
* Cloud Firestore

### Manual Testing Checklist

#### Authentication
- [x] USER login → close app → reopen → Home directly
- [x] USER login → swipe from recent apps → reopen → Home directly
- [x] USER login → restart phone → open app → Home directly
- [x] ADMIN login → close app → reopen → Admin Panel directly
- [x] Explicit logout → confirmation → Login screen
- [x] After logout, pressing Back does not return to Home
- [x] Uninstall → reinstall → account + Firestore data intact

#### Public Quizzes
- [x] UPCOMING quiz → button "Starts Soon", disabled
- [x] LIVE quiz → button "Join / Start", enabled
- [x] EXPIRED quiz → button "Expired", disabled
- [x] Join quiz → participant count +1 on Home
- [x] Re-join same quiz → count unchanged
- [x] Complete quiz → button becomes "View Result"
- [x] After deadline, quiz hidden from public list
- [x] Historical Quizzes Joined count unaffected by expiry

#### Scenario Questions
- [x] Create quiz with scenario containing 3 sub-questions → total +3
- [x] Create multiple scenarios → total = sum of sub-questions
- [x] Mixed scenario + normal order preserved
- [x] Attempt shows scenario banner above each sub-question
- [x] Progress counter counts sub-questions, not containers
- [x] PDF numbers sub-questions sequentially
- [x] Result calculates score correctly

#### Descriptive Answer Matching
- [x] `123456` ≡ `123 456` → Correct
- [x] `123 456` ≡ `123456` → Correct
- [x] `hello    world` ≡ `hello world` → Correct
- [x] `Hello World` ≡ `hello world` → Correct
- [x] `  Hello World` ≡ `Hello World` → Correct
- [x] `123` vs `124` → Wrong
- [x] `Java` vs `JavaScript` → Wrong
- [x] `1000` ≡ `1,000` → Correct
- [x] `10.50` ≡ `10.5` → Correct
- [x] `ABC 123` ≡ `ABC123` → Correct
- [x] `TCP` vs `UDP` → Wrong
- [x] Empty answer → Wrong
- [x] Original answer preserved in UI and PDF

#### Attempt & Anti-Cheat
- [x] Timer runs, auto-submits on expiry
- [x] Per-question timer locks question on expiry
- [x] Autosave preserves answers on app kill
- [x] Resume restores answers, states, timer, current index
- [x] BACK_BUTTON / HOME_OR_RECENTS / APP_BACKGROUND / FOCUS_LOST detected
- [x] 3 warnings → auto-submit
- [x] FLAG_SECURE blocks screenshots

#### Drafts & Publishing
- [x] Save as draft → scenario data preserved
- [x] Edit draft → scenario editable
- [x] Publish validates configured count vs actual
- [x] Under/over count shows exact error message

---

# 🔒 Security Recommendations

Before publishing the application:

* Do not use Firestore test mode in production.
* Configure proper Firestore security rules.
* Configure Firebase Storage security rules.
* Protect sensitive configuration files (`google-services.json`).
* Review authentication settings.
* Test admin permissions carefully.
* Verify quiz access permissions.
* Verify that correct answers are not exposed to regular users.
* Test anti-cheating functionality on supported Android versions.
* Ensure the `system/serverTime` document is write-protected (client read-only).

---

# 📝 Changelog

### v1.5.0 — Descriptive Answer Improvements
- Added `DescriptiveAnswerMatcher` — centralized normalization + comparison
- Multi-line descriptive input with improved spacing/padding
- Numeric formatting equivalence (`1,000` ≡ `1000`)
- Decimal equivalence (`10.50` ≡ `10.5`)
- Alphanumeric formatting equivalence (`ABC 123` ≡ `ABC123`)
- Student's original answer preserved (no trimming on save)

### v1.4.0 — Scenario-Based Questions
- New "Scenario" question type as a container for sub-questions
- Scenario editor with add/edit/remove sub-questions
- Flat navigation — sub-questions shown individually with scenario banner
- Scenario-aware scoring, PDFs, analytics, and stats
- Firestore schema supports nested `subQuestions` and `subAnswers`

### v1.3.0 — Persistent Login & Session Fix
- Removed `Creator` role — only `USER` and `ADMIN`
- Role-aware routing on startup
- Explicit logout with confirmation dialog + `FLAG_ACTIVITY_CLEAR_TASK`
- No `signOut()` calls outside explicit user action

### v1.2.0 — Real-Time Quizzes Joined Count
- Replaced static count with Firestore snapshot listener
- Counts unique joined quizzes (doc ID = quizId)
- Includes public, private, 6-digit, and QR joins
- Historical count preserved across quiz expiry

### v1.1.0 — Public Quiz Management & Auto-Expiry
- Public quiz listing with full metadata
- Lifecycle status: UPCOMING / LIVE / COMPLETED / EXPIRED / DELETED
- 24-hour auto-archive via client sweep + WorkManager
- Participant count via atomic Firestore transaction
- Dynamic join button states

### v1.0.0 — Initial Release
- Email/Password + Google authentication
- Quiz creation with Radio, Checkbox, Descriptive
- Public and private quizzes with 6-digit join codes
- QR code scanning
- Timer, anti-cheat, bookmarks, mark for review
- Leaderboard, analytics, PDF/CSV export
- Admin panel with user and quiz management

---

# 🤝 Contributing

Contributions are welcome.

1. Fork the repository.
2. Create a feature branch:
   ```bash
   git checkout -b feature/amazing-feature
   ```
3. Make your changes.
4. Commit your changes:
   ```bash
   git commit -m "Add amazing feature"
   ```
5. Push the branch:
   ```bash
   git push origin feature/amazing-feature
   ```
6. Open a Pull Request.

---

# 📄 License

This project is licensed under the **MIT License**.

See the `LICENSE` file for more information.

---

# 🙏 Acknowledgements

This project uses and/or is inspired by the following technologies:

* **Firebase** – Backend infrastructure
* **ZXing** – QR code generation and scanning
* **MPAndroidChart** – Charts and analytics
* **Glide** – Image loading
* **AndroidX Media3 / ExoPlayer** – Audio and video playback
* **AndroidX WorkManager** – Background archiving sweep
* **Material Design 3** – UI system

---

## ❤️ Smart Quiz

**Built with ❤️ by the Smart Quiz Team.**
