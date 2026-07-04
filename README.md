# 📱 Smart Attendance System

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Java](https://img.shields.io/badge/Language-Java-orange?style=for-the-badge&logo=openjdk&logoColor=white)
![Firebase](https://img.shields.io/badge/Backend-Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)
![Cloud Firestore](https://img.shields.io/badge/Database-Cloud%20Firestore-039BE5?style=for-the-badge&logo=firebase)
![Google ML Kit](https://img.shields.io/badge/Google%20ML%20Kit-Face%20Recognition-red?style=for-the-badge)
![Status](https://img.shields.io/badge/Project-Completed-success?style=for-the-badge)

---

# 📖 Project Overview

**Smart Attendance System** is an Android-based Final Year Capstone Project developed to automate attendance management using **Google ML Kit Face Recognition**, **Firebase Authentication**, and **Cloud Firestore**.

The application captures a student's face, generates face embeddings using Google ML Kit, securely stores them in Firebase, and compares them during attendance marking for accurate student identification. The system also verifies the student's GPS location, provides attendance analytics, and supports exporting attendance reports in PDF and Excel formats.

This solution minimizes manual work, reduces proxy attendance, and provides a secure, efficient, and user-friendly attendance management system.

---

# ✨ Features

## 👨‍🏫 Teacher Module

- 🔐 Secure Teacher Registration & Login
- 📚 Create and Manage Classes
- 👨‍🎓 Register Student Profiles
- 📷 Register Student Face Embeddings
- 🤖 Face Recognition-based Attendance
- 📍 GPS-based Attendance Verification
- 📅 View Attendance Records
- 📊 Attendance Analytics Dashboard
- 📄 Export Attendance Reports (PDF)
- 📊 Export Attendance Reports (Excel)
- 👥 Manage Student Information

---

## 👨‍🎓 Student Module

- 🔐 Secure Student Registration & Login
- 📚 Join Classes
- 👤 View Student Profile
- 📅 View Attendance History
- 📈 View Attendance Percentage
- 🔄 Receive Real-time Attendance Updates

---

# 🛠️ Technologies Used

| Technology | Purpose |
|------------|---------|
| Java | Android Application Development |
| Android Studio | Development Environment |
| Google ML Kit | Face Detection & Face Embedding Generation |
| CameraX | Camera Integration |
| Firebase Authentication | User Authentication |
| Cloud Firestore | Database |
| Firebase Storage | Store Face Images |
| Google Maps & Location Services | GPS Verification |
| MPAndroidChart | Attendance Analytics Dashboard |
| PDF & Excel Libraries | Attendance Report Export |
| Material Design Components | User Interface |

---

# 📂 Project Structure

```
Smart-Attendance-System/
│
├── app/
├── gradle/
├── build.gradle
├── settings.gradle
├── gradlew
├── gradlew.bat
└── README.md
```

---

# 🚀 Installation

### 1. Clone the Repository

```bash
git clone https://github.com/sanjanakatkar5552-cyber/Android-Smart-Attendance-System.git
```

### 2. Open the Project

Open the project in **Android Studio**.

### 3. Configure Firebase

- Create a Firebase Project.
- Download the `google-services.json` file.
- Place it inside the `app/` directory.

### 4. Sync Gradle

Allow Android Studio to download all required dependencies.

### 5. Run the Application

Connect an Android device or launch an emulator and run the application.

---

# 🔄 System Workflow

```
Teacher Login
      │
      ▼
Create Class
      │
      ▼
Register Student Face
      │
      ▼
Generate Face Embedding
      │
      ▼
Store Face Embedding in Firebase
      │
      ▼
Student Face Scan
      │
      ▼
Generate Face Embedding
      │
      ▼
Compare with Stored Embeddings
      │
      ▼
Verify GPS Location
      │
      ▼
Attendance Marked Successfully
      │
      ▼
Generate Analytics & Export Reports
```

---

# 🗄️ Firebase Collections

The application uses **Cloud Firestore** to manage data.

Main collections include:

- users
- classes
- students
- attendance
- face_embeddings

---

# 📸 Screenshots

Project screenshots will be added soon.

---

# 🎯 Future Enhancements

- 🌐 Multi-device synchronization
- 📢 Push Notifications
- 🌙 Dark Mode
- 🤖 AI-based Attendance Insights
- 🌐 Web Admin Portal
- ☁️ Automated Cloud Backup

---

# 👩‍💻 Developer

**Sanjana Katkar**


📧 Email: sanjanakatkar5552@gmail.com

🔗 GitHub: https://github.com/sanjanakatkar5552-cyber

---

# 📄 License

This project is developed for educational and academic purposes.

---

## ⭐ Support

If you found this project useful, please consider giving it a ⭐ on GitHub.

Thank you for visiting this repository!
