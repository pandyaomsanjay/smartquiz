# 🎓 Smart Quiz Hub

An advanced Android Quiz Platform built with Firebase that allows users to create, share, join, and attempt quizzes in real-time. Smart Quiz Hub supports Public & Private quizzes, role-based access control, live leaderboards, quiz analytics, anti-cheat monitoring, and gamification features.

## ✨ Features

### 👤 Authentication

* Email & Password Login
* Google Sign-In
* Forgot Password
* Secure Firebase Authentication
* User Profile Management

### 🎯 Quiz System

* Create Unlimited Quizzes
* Public & Private Quiz Modes
* 6-Digit Quiz Code Generation
* QR Code-Based Joining
* Timed MCQ Quizzes
* Negative Marking Support
* Quiz Categories
* Quiz Deadline & Expiry
* Single Attempt / Multiple Attempt Control
* Auto Submission on Timer Completion

### 📚 Public & Private Quizzes

#### 🌍 Public Quiz

* No join code required
* Visible to all users
* Appears in Available Quizzes section
* Instant participation

#### 🔒 Private Quiz

* Hidden from public dashboard
* Join using Quiz Code
* QR Code Support
* Creator-controlled access

### 📊 Analytics Dashboard

* Participant Statistics
* Quiz Performance Analysis
* Score Distribution Charts
* Attempt Tracking
* User Activity Reports
* Export Results to PDF & CSV

### 🏆 Gamification

* Global Leaderboard
* User Rankings
* Achievement Badges
* Daily Streaks
* Quiz History
* Bookmarked Questions

### 🔔 Notifications

* Firebase Cloud Messaging
* Admin Announcements
* Quiz Updates
* System Notifications

### 🛡️ Security Features

* Firebase Security Rules
* Role-Based Access Control
* Screenshot Prevention
* Tab Switch Detection
* Anti-Cheat Monitoring
* User Blocking System

---

## 👥 User Roles

### Admin

* Manage Users
* Manage Quizzes
* View Analytics
* View Cheat Logs
* Send Announcements
* Block Users

### Creator

* Create Quizzes
* Edit/Delete Quizzes
* Generate QR Codes
* View Participants
* Export Results
* Manage Quiz Settings

### User

* Join Quizzes
* Attempt Quizzes
* View History
* Earn Badges
* Track Rankings

---

## 🛠 Tech Stack

| Component      | Technology               |
| -------------- | ------------------------ |
| Language       | Java / Kotlin            |
| IDE            | Android Studio           |
| Backend        | Firebase Firestore       |
| Authentication | Firebase Auth            |
| Storage        | Firebase Storage         |
| Notifications  | Firebase Cloud Messaging |
| Charts         | MPAndroidChart           |
| QR Scanner     | ZXing                    |
| Image Loading  | Glide                    |
| UI             | Material Design 3        |

---

## 📂 Project Structure

```text
app/
├── activities/
├── adapters/
├── models/
├── services/
├── utils/
├── layouts/
├── drawables/
└── firebase/
```

---

## 🚀 Installation

### Clone Repository

```bash
git clone https://github.com/your-username/smart-quiz-hub.git
cd smart-quiz-hub
```

### Firebase Setup

1. Create Firebase Project
2. Add Android Application
3. Download `google-services.json`
4. Place file inside:

```text
app/google-services.json
```

### Enable Firebase Services

* Authentication
* Firestore Database
* Firebase Storage
* Cloud Messaging

### Build Project

```bash
./gradlew build
```

### Run Application

```bash
Run from Android Studio
```

---

## 🔥 Firestore Collections

```text
users/
quizzes/
attempts/
announcements/
leaderboard/
badges/
bookmarks/
cheatLogs/
```

---

## 📱 Main Screens

* Splash Screen
* Login Screen
* Signup Screen
* Home Dashboard
* Create Quiz
* Join Quiz
* Quiz Attempt
* Quiz Result
* Creator Dashboard
* Admin Dashboard
* Leaderboard
* Profile
* Settings

---

## 📈 Future Enhancements

* AI Generated Questions
* Multiplayer Quiz Battles
* Voice Questions
* Video Questions
* Offline Quiz Support
* Quiz Marketplace
* Community Discussions

---

## 🤝 Contributing

Contributions, suggestions, and improvements are welcome.

1. Fork the Repository
2. Create a Feature Branch
3. Commit Changes
4. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License.

---

## ⭐ Support

If you like this project, please consider giving it a star on GitHub.

---

### Smart Quiz Hub

**Create. Share. Compete. Learn. 🚀**
