# 🧠 Smart Quiz – Android App

**Smart Quiz** is a full-featured Android quiz platform that allows users to participate in quizzes, create and manage their own quizzes, compete through leaderboards, and provides administrative tools for managing the platform.

The application is built using **Kotlin**, **XML**, and **Firebase**, with support for authentication, Cloud Firestore, Firebase Storage, push notifications, multimedia questions, quiz analytics, and anti-cheating mechanisms.

---

## 📱 Features

### 👤 For All Users

* **Authentication**

  * Email/Password authentication
  * Google Sign-In
  * Password reset
  * Session persistence

* **Dashboard**

  * View personal statistics
  * Quizzes joined
  * Streak
  * Rank
  * Quick actions for creating and joining quizzes

* **Join Quiz**

  * Join using a 6-digit quiz code
  * Scan a QR code to join

* **Quiz Attempt**

  * Timed quizzes
  * Whole-quiz and per-question timers
  * Negative marking
  * Image, audio, and video questions
  * Bookmark questions
  * Mark questions for review
  * Question navigation grid

* **Results**

  * Score
  * Percentage
  * Time taken
  * Submission reason
  * Timer-expiry status
  * Cheat-warning status

* **Leaderboards**

  * Global leaderboard
  * Per-quiz leaderboard
  * Highlight current user's ranking

* **Profile**

  * Upload profile avatar
  * Change display name
  * View earned badges

* **Settings**

  * App tutorial
  * Change password
  * Delete account
  * Logout

* **Feedback**

  * Rate quizzes
  * Submit comments and feedback

* **Push Notifications**

  * Receive announcements through Firebase Cloud Messaging (FCM)

---

## 👨‍💻 Features for Quiz Creators

### Create Quizzes

Creators can configure:

* Quiz title
* Description
* Public/private visibility
* Quiz timer mode
* Whole-quiz timer
* Per-question timer
* Deadline
* Negative marking
* Score visibility

### Add Questions

Supported question types include:

* Multiple-choice questions
* Single-correct-answer questions
* Multiple-correct-answer questions
* Descriptive questions

Questions can also contain:

* Images
* Audio
* Video
* Custom point values

### Draft System

* Save quizzes as drafts
* Edit drafts later
* Publish quizzes when ready

### Quiz Statistics

Creators can view:

* Participant list
* Participant scores
* Time spent
* Score distribution
* Completion rate

### Data Export

* Export results as CSV
* Export results as PDF

### Question Paper

Download question papers:

* With answers
* Without answers

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

---

## 🛡️ Administrator Features

### Admin Panel

Administrators can:

* View registered users
* Assign creator roles
* Block users
* Ban users
* Provide reasons for user restrictions

### Quiz Management

* View/manage quizzes
* Delete quizzes when required

### Cheat Logs

Monitor suspicious activity including:

* App minimization
* Back button activity
* Focus loss
* App switching
* Home/Recents activity

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
* Home/Recents navigation
* Window focus loss
* App minimization

### ⚠️ Violation System

Users receive warnings for suspicious activity.

After **3 violations**, the quiz can be automatically submitted.

### 🔢 Attempt Limits

Multiple quiz completions can be restricted unless the quiz creator enables multiple attempts.

### ⏱️ Timer Management

The application supports:

* No timer
* Whole-quiz timer
* Per-question timer
* Automatic submission when the timer expires

---

# 🛠️ Tech Stack

| Component             | Technology                     |
| --------------------- | ------------------------------ |
| Language              | Kotlin                         |
| UI                    | XML + Material Design 3        |
| View Binding          | Android ViewBinding            |
| Backend               | Firebase                       |
| Authentication        | Firebase Authentication        |
| Google Authentication | Google Sign-In                 |
| Database              | Cloud Firestore                |
| Storage               | Firebase Storage               |
| Notifications         | Firebase Cloud Messaging (FCM) |
| QR Code               | ZXing                          |
| Charts                | MPAndroidChart                 |
| Image Loading         | Glide                          |
| Video/Audio           | AndroidX Media3 / ExoPlayer    |
| PDF Export            | Android PdfDocument            |
| CSV Export            | Manual CSV Generation          |
| Build System          | Gradle Kotlin DSL              |

---

# 🚀 Getting Started

## Prerequisites

Before setting up Smart Quiz, make sure you have:

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
cd smart-quiz
```

> Replace `yourusername` with your GitHub username and update the repository URL accordingly.

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

Open the:

**Firebase Console**

https://console.firebase.google.com/

Then:

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

Download:

```text
google-services.json
```

Place the file inside:

```text
app/google-services.json
```

### Important

Do **not** commit your real `google-services.json` to a public repository if your project configuration or repository policy requires keeping it private.

If a placeholder file already exists, replace it with your Firebase configuration file locally.

---

# 🔐 Firebase Authentication

Go to:

**Firebase Console → Authentication → Sign-in method**

Enable:

### Email/Password

Enable the **Email/Password** provider.

### Google Sign-In

Enable the **Google** provider.

Select the appropriate support email and save the configuration.

---

# 🔑 Google Sign-In SHA-1

For Google Sign-In, configure the SHA-1 fingerprint for your Android application.

From the Android project terminal, run:

### Windows

```powershell
.\gradlew signingReport
```

Find the debug variant:

```text
Variant: debug
SHA1: 09:21:97:a6:d8:f5:e0:a7:b7:13:1e:07:93:37:73:15:b7:48:e6:c3
```

Copy the SHA-1 value.

Then go to:

**Firebase Console → Project Settings → Your Android App**

Add the SHA-1 fingerprint.

After making changes, download the updated:

```text
google-services.json
```

and replace:

```text
app/google-services.json
```

---

# 🗄️ Cloud Firestore

Go to:

**Firebase Console → Firestore Database**

Create a Firestore database.

For initial development, you may use test mode, but configure proper security rules before deploying the application.

Smart Quiz stores application data such as:

* Users
* Quizzes
* Questions
* Attempts
* Results
* Bookmarks
* Feedback
* Cheat logs
* Announcements

---

# 📦 Firebase Storage

Go to:

**Firebase Console → Storage**

Create/configure Firebase Storage.

Storage is used for application media such as:

* Profile avatars
* Quiz images
* Audio
* Video

Configure appropriate Storage security rules before production deployment.

---

# 🔔 Firebase Cloud Messaging

Firebase Cloud Messaging (FCM) is used to send push notifications and announcements.

Go to:

**Firebase Console → Project Settings → Cloud Messaging**

Make sure Firebase Cloud Messaging is enabled for your project.

---

# 🔐 Firestore Security Rules

The following rules provide the application's recommended Firestore access structure.

Go to:

**Firebase Console → Firestore Database → Rules**

Then add:

```javascript
rules_version = '2';

service cloud.firestore {

  function isAdmin() {
    return request.auth != null &&
      get(/databases/$(database)/documents/users/$(request.auth.uid))
        .data.role == "admin";
  }

  match /databases/{database}/documents {

    // Users
    match /users/{userId} {

      allow read: if request.auth != null;

      allow write: if request.auth != null &&
        (
          request.auth.uid == userId ||
          isAdmin()
        );

      match /joinedQuizzes/{quizId} {
        allow read, write: if request.auth != null &&
          request.auth.uid == userId;
      }
    }

    // Quizzes
    match /quizzes/{quizId} {

      allow read: if request.auth != null &&
        (
          resource.data.visibility == "public" ||
          resource.data.quizCode != "" ||
          resource.data.creatorId == request.auth.uid ||
          isAdmin()
        );

      allow create: if request.auth != null;

      allow update: if request.auth != null &&
        (
          request.auth.uid == resource.data.creatorId ||
          isAdmin()
        ) &&
        request.resource.data.keys().hasAll([
          'creatorId',
          'status'
        ]) &&
        request.resource.data.creatorId == resource.data.creatorId;

      allow delete: if request.auth != null &&
        (
          request.auth.uid == resource.data.creatorId ||
          isAdmin()
        );

      // Public Questions
      match /questions/{questionId} {

        allow read: if request.auth != null;

        allow write: if request.auth != null &&
          (
            request.auth.uid ==
              get(
                /databases/$(database)/documents/quizzes/$(quizId)
              ).data.creatorId ||
            isAdmin()
          );
      }

      // Private Questions
      match /questions_private/{questionId} {

        allow read: if request.auth != null &&
          (
            request.auth.uid ==
              get(
                /databases/$(database)/documents/quizzes/$(quizId)
              ).data.creatorId ||
            isAdmin()
          );

        allow write: if request.auth != null &&
          (
            request.auth.uid ==
              get(
                /databases/$(database)/documents/quizzes/$(quizId)
              ).data.creatorId ||
            isAdmin()
          );
      }

      // Attempts
      match /attempts/{userId} {

        allow read: if request.auth != null &&
          (
            request.auth.uid == userId ||

            request.auth.uid ==
              get(
                /databases/$(database)/documents/quizzes/$(quizId)
              ).data.creatorId ||

            isAdmin() ||

            get(
              /databases/$(database)/documents/quizzes/$(quizId)
            ).data.status == "PUBLISHED"
          );

        allow create: if request.auth != null &&
          request.auth.uid == userId;

        allow update: if request.auth != null &&
          request.auth.uid == userId &&
          (
            resource.data.status != "Completed" ||

            get(
              /databases/$(database)/documents/quizzes/$(quizId)
            ).data.allowMultipleAttempts == true
          );

        allow delete: if false;
      }
    }

    // Global Results
    match /results/{resultId} {

      allow read: if request.auth != null;

      allow write: if request.auth != null &&
        request.auth.uid == resource.data.userId;
    }

    // Legacy Quiz Attempts
    match /quiz_attempts/{attemptId} {

      allow read: if request.auth != null &&
        (
          request.auth.uid == resource.data.userId ||
          isAdmin()
        );

      allow write: if request.auth != null &&
        (
          request.auth.uid == resource.data.userId ||
          isAdmin()
        );
    }

    // Active Attempts
    match /activeAttempts/{attemptId} {

      allow read, write: if request.auth != null;
    }

    // Bookmarks
    match /bookmarks/{bookmarkId} {

      allow read, write: if request.auth != null &&
        (
          request.auth.uid == resource.data.userId ||
          isAdmin()
        );
    }

    // Feedback
    match /feedback/{feedbackId} {

      allow read: if request.auth != null;

      allow write: if request.auth != null;
    }

    // Cheat Logs
    match /cheat_logs/{logId} {

      allow write: if request.auth != null;

      allow read: if request.auth != null &&
        (
          isAdmin() ||

          (
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

    // Announcements
    match /announcements/{announceId} {

      allow read: if request.auth != null;

      allow write: if request.auth != null &&
        isAdmin();
    }
  }
}
```

> **Security Note:** Review and test these rules against the exact Firestore queries used by the application before production deployment.

---

# 📦 Key Dependencies

The project uses the following major dependencies:

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
```

> The complete dependency configuration is available in `app/build.gradle.kts`.

---

# ▶️ Build and Run

After configuring Firebase:

1. Open the project in Android Studio.
2. Wait for Gradle Sync.
3. Connect an Android device or start an emulator.
4. Make sure USB debugging is enabled if using a physical device.
5. Click **Run ▶**.

Alternatively, use:

```bash
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew assembleDebug
```

To install the debug APK on a connected device:

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
│   │   │   │           ├── adapters/
│   │   │   │           ├── models/
│   │   │   │           ├── services/
│   │   │   │           └── utils/
│   │   │   │
│   │   │   ├── res/
│   │   │   │   ├── drawable/
│   │   │   │   ├── layout/
│   │   │   │   ├── values/
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
│   ├── home.png
│   ├── attempt.png
│   └── leaderboard.png
│
├── .gitignore
├── LICENSE
└── README.md
```

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

The Firebase Emulator Suite can be used for local testing of Firebase services such as:

* Authentication
* Cloud Firestore

---

# 🔒 Security Recommendations

Before publishing the application:

* Do not use Firestore test mode in production.
* Configure proper Firestore security rules.
* Configure Firebase Storage security rules.
* Protect sensitive configuration files.
* Review authentication settings.
* Test admin permissions carefully.
* Verify quiz access permissions.
* Verify that correct answers are not exposed to regular users.
* Test anti-cheating functionality on supported Android versions.

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

---

## ❤️ Smart Quiz

**Built with ❤️ by the Smart Quiz Team.**
