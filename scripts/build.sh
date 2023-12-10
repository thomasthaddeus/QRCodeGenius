#!/bin/bash

# Exit the script if any command fails
set -e

# Navigate to the project's root directory (if your build.sh is not already there)
cd ../

# Clean the build
echo "Cleaning the build..."
./gradlew clean

# Build the app bundle (Release version)
echo "Building the app bundle..."
./gradlew bundleRelease

# If you also want to generate an APK
echo "Building the APK..."
./gradlew assembleRelease

echo "Build complete."
