# EchoX Native

EchoX is a native Android application built with Kotlin and Jetpack Compose.

## Features
- **Authentication**: Secure login via X (Twitter).
- **Audio Recording**: High-quality audio recording with metering.
- **Video Generation**: On-device video generation using Jetpack Media3.
- **Social Sharing**: Share generated videos directly to X.

## Prerequisites
- Android Studio Koala or newer.
- JDK 17 or newer.
- Android SDK API 34.

## Getting Started

1.  **Clone the repository**:
    ```bash
    git clone <repository-url>
    cd EchoX
    ```

2.  **Open in Android Studio**:
    Open the project root directory in Android Studio.

3.  **Build the project**:
    ```bash
    ./gradlew assembleDebug
    ```

4.  **Run on device**:
    Connect your Android device and run the app from Android Studio or via:
    ```bash
    ./gradlew installDebug
    ```

## Architecture
- **UI**: Jetpack Compose
- **DI**: Hilt (if applicable, otherwise standard Android architecture)
- **Async**: Coroutines & Flow
- **Media**: Jetpack Media3

## License
[License Name]
