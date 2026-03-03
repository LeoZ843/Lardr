# Lardr

Android grocery shopping list app with recipe management.

## Setup

### Prerequisites
- Android Studio (latest version)
- JDK 11 or higher
- Firebase account (free tier is fine)

### Installation

1. **Clone the repository**
```bash
   git clone https://github.com/LeoZ843/Lardr.git
   cd lardr
```

2. **Set up Firebase**
   - Create a Firebase project at https://console.firebase.google.com/
   - Add an Android app with package name: `com.zanoni.lardr`
   - Download `google-services.json` and place it in `app/` directory
   - Enable Authentication (Email/Password, Google Sign-In)
   - Enable Firestore Database
   
3. **Configure secrets**
```bash
   cp secrets.properties.example secrets.properties
```
   Edit `secrets.properties` with your Firebase credentials

4. **Build and run**
```bash
   ./gradlew assembleDebug
```
   Or open in Android Studio and click Run
