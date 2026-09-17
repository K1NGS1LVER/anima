.PHONY: test e2e test-all demo benchmark clean install apk apk-install android-test android-clean

# ---------------------------------------------------------------------------
# Python runtime (zero dependencies, pure stdlib)
# ---------------------------------------------------------------------------

test:
	python3 -m unittest test_anima.py

e2e:
	python3 -m unittest test_e2e.py

test-all:
	python3 -m unittest discover -p "test_*.py"

demo:
	python3 anima.py --demo

benchmark:
	python3 anima.py --benchmark

clean:
	find . -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true
	find . -type f -name "*.pyc" -delete 2>/dev/null || true
	rm -f *.db 2>/dev/null || true

install:
	pip install -e .

# ---------------------------------------------------------------------------
# Android daemon APK
# Requires a one-time toolchain setup -- see the header of android/env.sh.
# Each target re-sources env.sh because make runs every recipe in a fresh shell.
# ---------------------------------------------------------------------------

apk:
	cd android && . ./env.sh && ./gradlew :app:assembleDebug
	@echo "APK -> android/app/build/outputs/apk/debug/app-debug.apk"

apk-install:
	cd android && . ./env.sh && ./gradlew :app:installDebug

android-test:
	cd android && . ./env.sh && ./gradlew :app:testDebugUnitTest

android-clean:
	cd android && . ./env.sh && ./gradlew clean
