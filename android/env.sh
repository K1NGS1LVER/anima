#!/usr/bin/env bash
# Anima Android build environment.
#   source android/env.sh
#
# One-time setup on a fresh macOS machine:
#   brew install openjdk@17 gradle
#   brew install --cask android-commandlinetools android-platform-tools
#   yes | sdkmanager --licenses
#   sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0"
#
# AGP 8.5 requires JDK 17 exactly -- a newer JDK on PATH (macOS ships 21/24) is
# the most common cause of "Unsupported class file major version" on this repo.
export JAVA_HOME="${JAVA_HOME_OVERRIDE:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_HOME=$ANDROID_HOME"
java -version 2>&1 | head -1
