# A Blind Examiner Application

A mobile application for accessible examination, designed for visually impaired students and educators.

## Features

- **Accessibility-First Design**: The application is built with accessibility as the core principle, providing voice guidance and compatibility with screen readers.
- **User Role Management**: Supports three user types:
  - **Admin**: Can create and manage student and instructor accounts
  - **Instructor**: Can create and manage exams
  - **Student**: Can take exams with accessibility features

## Authentication Flow

- **Admin Access**: 
  - Email: natiabay1017@gmail.com
  - Password: 12345678
  - The admin can create accounts for students and instructors

- **Student & Instructor Access**:
  - Accounts must be created by the admin
  - All credentials are stored securely in Firebase Authentication
  - User profile data is stored in Firebase Realtime Database

## Account Creation Fields

### Student Accounts
- Email
- Password
- Full Name
- Year
- ID
- Section
- Department

### Instructor Accounts
- Email
- Password
- Full Name
- Department
- Course Assigned

## Architecture

- **Main Activity**: Serves as the entry point with minimal code (< 40 lines)
- **Navigation**: Centralized navigation in separate component
- **Screens**: Each screen is contained in its own composable function
- **Firebase Integration**: Authentication and database operations

## Setup & Development

1. Clone the repository
2. Ensure you have the latest version of Android Studio
3. The application uses Gradle for dependency management
4. Firebase configuration is already set up in the google-services.json file

## Technical Details

- Built with Kotlin and Jetpack Compose
- Follows Material3 design principles
- Uses Firebase for authentication and data storage
- Implements accessibility best practices

## Project Overview

Blind Examiner is an Android application that enables blind students to take examinations using a specialized interface with accessible features. The app includes different roles for students, teachers, and administrators, and leverages text-to-speech, braille input simulation, and other accessibility features to create an inclusive examination experience.

## Key Features

### For Blind Students:
- Text-to-speech narration of exam questions and options
- Braille input support for answering questions
- Voice guidance throughout the examination process
- Timer announcements to track remaining time
- Secure exam environment that prevents exiting during an exam

### For Teachers:
- Exam creation and management
- Publishing/unpublishing control
- Answer key setup for automatic grading

### For Administrators:
- User management system
- System-wide settings controls
- Accessibility options configuration

## Accessibility Features

- Screen reader integration
- High contrast UI with accessible color schemes
- Large, readable text
- Voice announcements for important events
- Haptic feedback
- Semantic properties for all UI elements

## Getting Started

1. Clone this repository
2. Open the project in Android Studio
3. Connect to your Firebase project (add your own google-services.json)
4. Build and run on your Android device or emulator

## User Roles

### Student
- Login with student credentials
- View available exams
- Take exams with accessibility support
- Review results

### Teacher
- Login with teacher credentials
- Create and manage exams
- Set up questions and answers
- View student results

### Administrator
- Login with admin credentials
- Manage users and roles
- Configure system settings
- Monitor system usage

## License

This project is part of a thesis research on accessible education technology.

## Machine Learning Features

### Braille Recognition
- Integration of BrailleNet.tflite model for braille character recognition
- Real-time braille input processing
- Support for standard braille patterns

### Automated Grading
- ASAG (Automated Short Answer Grading) model integration
- Intelligent answer evaluation system
- Support for both objective and subjective questions

## Security Implementation

### Exam Lockdown Mode
- Kiosk mode implementation for secure exam environment
- Device administration features
- Screen lock and battery optimization controls
- Prevention of unauthorized app switching

### Data Protection
- Secure file handling through FileProvider
- Protected storage access
- Encrypted user credentials

## System Requirements

### Hardware Requirements
- Android device with camera (for ML features)
- Minimum 2GB RAM recommended
- Sufficient storage for ML models

### Software Requirements
- Android 6.0 (API level 23) or higher
- Google Play Services
- Screen reader support (TalkBack/VoiceOver)

## Development Environment

### Required Tools
- Android Studio Arctic Fox or newer
- Gradle 7.0 or higher
- TensorFlow Lite dependencies
- Firebase SDK

### Configuration Steps
1. Set up Firebase project and add google-services.json
2. Configure ML models in assets folder
3. Set up device admin policies
4. Configure FileProvider paths

## Testing

### Unit Tests
- Located in src/test directory
- Covers core functionality
- ML model integration tests

### Integration Tests
- Located in src/androidTest directory
- UI testing with accessibility features
- Exam flow testing

## Common Issues

### ML Model Issues
- Ensure sufficient device memory
- Check camera permissions
- Verify model file integrity

### Accessibility Features
- Screen reader compatibility
- Voice guidance settings
- Haptic feedback configuration

