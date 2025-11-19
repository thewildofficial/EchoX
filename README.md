# EchoX

EchoX is a standalone Android application built with **Expo (React Native)** that brings the "Audio Note" feature to Android users. It allows you to record voice notes, visualizes them with a dynamic waveform, generates a video with your profile picture, and shares it directly to X (formerly Twitter).

## Features

- **X Authentication**: Secure login via OAuth 2.0.
- **Audio Recording**: High-quality audio recording with real-time metering.
- **Dynamic Waveform**: Visualizes your voice as you speak.
- **Video Generation**: Creates an MP4 video on-device by combining your profile picture and audio.
- **Auto-Splitting**: Automatically splits recordings longer than 140 seconds into multiple video segments for non-premium users, posting them as a thread.
- **Direct Sharing**: Uploads and posts tweets directly from the app.

## Tech Stack

- **Framework**: [Expo](https://expo.dev/) (React Native)
- **Language**: TypeScript
- **Styling**: [NativeWind](https://www.nativewind.dev/) (Tailwind CSS)
- **Video Processing**: [FFmpeg Kit](https://github.com/arthenica/ffmpeg-kit)
- **Audio**: `expo-av`
- **Animations**: `react-native-reanimated` & `react-native-svg`

## Prerequisites

- **Node.js** (LTS recommended)
- **Android Studio** (for local development builds) or an **Expo Account** (for EAS Build)
- **X Developer Account**: You need a Client ID from the [X Developer Portal](https://developer.twitter.com/en/portal/dashboard).
  - Create a Project & App.
  - Enable **User Authentication Settings**.
  - App Type: **Native App**.
  - Callback URI / Redirect URL: `echox://auth` (This is crucial).
  - Website URL: Any valid URL (e.g., `https://example.com`).

## Setup

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/yourusername/echox.git
    cd echox
    ```

2.  **Install dependencies**:
    ```bash
    npm install
    ```

3.  **Configure Environment Variables**:
    - Copy `.env.example` to `.env`:
        ```bash
        cp .env.example .env
        ```
    - Open `.env` and paste your X Client ID:
        ```properties
        EXPO_PUBLIC_X_CLIENT_ID=your_actual_client_id_here
        ```

## Running the App

**Important**: Because this app uses `ffmpeg-kit-react-native` (native code), it **cannot** run in the standard "Expo Go" app from the Play Store. You must use a **Development Build**.

### Option A: Local Build (Requires Android Studio)

1.  Ensure an Android Emulator is running or a device is connected.
2.  Run the prebuild and build command:
    ```bash
    npx expo run:android
    ```

### Option B: EAS Build (Cloud Build)

1.  Install EAS CLI: `npm install -g eas-cli`
2.  Login: `eas login`
3.  Configure the project: `eas build:configure`
4.  Build for development:
    ```bash
    eas build -p android --profile development
    ```
5.  Download the APK and install it on your device.
6.  Start the development server:
    ```bash
    npx expo start --dev-client
    ```

## Troubleshooting

- **FFmpeg Errors**: If you see errors related to FFmpeg, ensure you are NOT using Expo Go. You must use the custom development build.
- **Login Issues**: Double-check your Callback URI in the X Developer Portal matches `echox://auth`.
