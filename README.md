# 🎓 Smart Quiz Hub

An advanced Android Quiz Platform built with Firebase that allows users to create, share, join, and attempt quizzes in real-time. Smart Quiz Hub supports Public & Private quizzes, role-based access control, live leaderboards, quiz analytics, anti-cheat monitoring, and gamification features.

## ✨ Features

### 👤 Authentication
- Email & Password Login
- Google Sign-In
- Forgot Password
- Secure Firebase Authentication
- User Profile Management with Avatar Upload

### 🎯 Quiz System
- Create Unlimited Quizzes
- Public & Private Quiz Modes
- 6-Digit Quiz Code Generation
- QR Code-Based Joining
- Timed MCQ Quizzes (configurable timer)
- Negative Marking Support
- Quiz Categories
- Quiz Deadline & Expiry
- Single Attempt / Multiple Attempt Control
- Auto Submission on Timer Completion
- Media Support (images, audio, video in questions)

### 📚 Public & Private Quizzes
- **Public Quiz**: No join code required, visible to all users, instant participation.
- **Private Quiz**: Hidden from public dashboard, join using Quiz Code or QR Code, creator-controlled access.

### 📊 Analytics Dashboard
- Participant Statistics (per quiz)
- Quiz Performance Analysis
- Score Distribution Charts (MPAndroidChart)
- Attempt Tracking
- User Activity Reports
- Export Results to PDF & CSV
- Participant Answers Review

### 🏆 Gamification
- Global Leaderboard with Real-time Updates
- User Rankings
- Achievement Badges (displayed on profile)
- Daily Streaks
- Quiz History (joined quizzes with status)
- Bookmarked Questions

### 🔔 Notifications
- Firebase Cloud Messaging
- Admin Announcements
- Quiz Updates
- System Notifications

### 🛡️ Security Features
- Firebase Security Rules
- Role-Based Access Control
- Screenshot Prevention (`FLAG_SECURE`)
- Tab Switch Detection (logs cheat attempts)
- Anti-Cheat Monitoring (cheat logs stored in Firestore)
- User Blocking System (admin can ban with reason)

## 👥 User Roles

### 👑 Admin
- Manage Users (view, assign roles, block)
- Manage Quizzes (view all, delete)
- View Analytics (total users, quizzes, attempts, average score)
- View Cheat Logs
- Send Announcements (FCM)
- Full access to all data

### 🧑‍💻 Creator
- Create Quizzes
- Edit/Delete Own Quizzes
- Generate QR Codes for Private Quizzes
- View Participants
- Export Results (CSV/PDF)
- Manage Quiz Settings (visibility, timer, negative marking)

### 👤 User
- Join Quizzes (public/private)
- Attempt Quizzes
- View History (joined quizzes with status)
- Earn Badges
- Track Rankings
- Bookmark Questions
- View Profile and Stats

## 🛠 Tech Stack

| Component               | Technology                     |
|-------------------------|--------------------------------|
| Language                | Kotlin                         |
| IDE                     | Android Studio                 |
| Backend                 | Firebase Firestore             |
| Authentication          | Firebase Auth                  |
| Storage                 | Firebase Storage               |
| Notifications           | Firebase Cloud Messaging (FCM) |
| Charts                  | MPAndroidChart                 |
| QR Scanner              | ZXing (via journeyapps)        |
| Image Loading           | Glide                          |
| UI                      | Material Design 3 (Material You)|
| Architecture            | MVVM (simplified, Activity-based) |

## 📂 Project Structure

```
SmartQuiz/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/smartquiz/
│   │   │   │   ├── MainActivity.kt               # Splash
│   │   │   │   ├── LoginActivity.kt
│   │   │   │   ├── SignupActivity.kt
│   │   │   │   ├── HomeDashboardActivity.kt
│   │   │   │   ├── QuizCreationActivity.kt
│   │   │   │   ├── JoinQuizActivity.kt
│   │   │   │   ├── QuizAttemptActivity.kt
│   │   │   │   ├── ResultActivity.kt
│   │   │   │   ├── SubmissionSuccessActivity.kt
│   │   │   │   ├── LeaderboardActivity.kt
│   │   │   │   ├── CreatorDashboardActivity.kt
│   │   │   │   ├── UserProfileActivity.kt
│   │   │   │   ├── SettingsActivity.kt
│   │   │   │   ├── AdminPanelActivity.kt
│   │   │   │   ├── AdminQuizzesActivity.kt
│   │   │   │   ├── AdminAnalyticsActivity.kt
│   │   │   │   ├── AdminAnnouncementsActivity.kt
│   │   │   │   ├── AdminCheatLogsActivity.kt
│   │   │   │   ├── QuizStatsActivity.kt
│   │   │   │   ├── QuizAttemptActivity.kt
│   │   │   │   ├── QuizDetailsActivity.kt
│   │   │   │   ├── FeedbackActivity.kt
│   │   │   │   ├── models/                        # Data classes
│   │   │   │   ├── adapters/                      # RecyclerView adapters
│   │   │   │   ├── services/                      # FCM service
│   │   │   │   ├── utils/                         # Helpers
│   │   │   │   └── QuizApplication.kt             # App class for Firestore settings
│   │   │   ├── res/
│   │   │   │   ├── layout/                        # All activity & item XMLs
│   │   │   │   ├── drawable/                      # Icons, backgrounds
│   │   │   │   ├── values/                        # Colors, strings, themes, dimens
│   │   │   │   └── xml/                           # File provider paths
│   │   │   └── AndroidManifest.xml
│   │   └── res/ (additional resources)
│   └── build.gradle (Module: app)                 # Dependencies
├── google-services.json                           # Firebase config
└── build.gradle (Project)
```

## 🚀 Installation

### Clone Repository
```bash
git clone https://github.com/pandyaomsanjay/smartquiz
cd smartquiz
```

### Firebase Setup
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project (or select existing)
3. Add an Android app with package name `com.smartquiz`
4. Download `google-services.json`
5. Place the file in `app/` directory

### Enable Firebase Services
- **Authentication**: Enable Email/Password and Google Sign-In
- **Firestore Database**: Create database in test mode (or with security rules)
- **Firebase Storage**: Enable for user avatars and question media
- **Cloud Messaging**: For announcements (optional)

### Build & Run
```bash
./gradlew build
```
Then run from Android Studio on an emulator or physical device.

## 🔥 Firestore Collections Structure

| Collection      | Description                                  |
|-----------------|----------------------------------------------|
| `users`         | User profiles with role, badges, streak      |
| `quizzes`       | Quiz metadata (title, visibility, timer, etc.)|
| `questions`     | Public question data (text, options, media)  |
| `questions_private`| Correct answers (separate for security)   |
| `attempts`      | User attempts per quiz (subcollection under quiz)|
| `results`       | Aggregated scores for leaderboard            |
| `announcements` | Admin broadcast messages                     |
| `cheat_logs`    | Suspicious activity logs                     |
| `bookmarks`     | User bookmarked questions                    |
| `feedback`      | User ratings and comments on quizzes         |
| `joinedQuizzes` | User's joined quizzes (subcollection under user)|

## 📱 Main Screens (13+)

| Screen               | Description                                 |
|----------------------|---------------------------------------------|
| **Splash**           | App logo and auto-navigation                |
| **Login**            | Email/Password + Google Sign-In             |
| **Signup**           | New user registration                       |
| **Home Dashboard**   | Overview, stats, quick actions, quiz list   |
| **Create Quiz**      | Build quiz with questions, timer, visibility|
| **Join Quiz**        | Enter 6-digit code or scan QR               |
| **Quiz Attempt**     | Timer, question navigation, anti-cheat      |
| **Result**           | Score and performance summary               |
| **Leaderboard**      | Global rankings                             |
| **Creator Dashboard**| Manage own quizzes, view stats              |
| **Admin Panel**      | Manage users, quizzes, analytics, logs      |
| **User Profile**     | Edit profile, badges, avatar                |
| **Settings**         | Password reset, account deletion, logout    |

## 📈 Future Enhancements
- AI Generated Questions
- Multiplayer Quiz Battles
- Voice & Video Questions
- Offline Quiz Support
- Quiz Marketplace
- Community Discussions

## 🤝 Contributing
Contributions are welcome!  
1. Fork the repository  
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)  
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)  
4. Push to the branch (`git push origin feature/AmazingFeature`)  
5. Open a Pull Request

## 📄 License
This project is licensed under the MIT License – see the [LICENSE](LICENSE) file for details.

## ⭐ Support
If you find this project useful, please give it a star ⭐ on GitHub.

---

**Smart Quiz Hub**  
Create. Share. Compete. Learn. 🚀
