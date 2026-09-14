# DOUPAD 3.1.0 — GitHub Actions build

The repository includes `.github/workflows/build-apk.yml`.

## Automatic build
Push the project to `main`, `master`, or any `release/**` branch. The workflow will:

1. Install Temurin JDK 17.
2. Install Android SDK platform 35 and Build Tools 35.0.0.
3. Run the two source-verification scripts.
4. Run `testDebugUnitTest`.
5. Run `assembleDebug`.
6. Upload `DOUPAD-3.1.0-realtime-debug.apk` plus its SHA-256 checksum as a GitHub Actions artifact.

## Manual build
Open the repository on GitHub → **Actions** → **Build DOUPAD APK** → **Run workflow**.

The artifact name is `DOUPAD-3.1.0-realtime-debug`.
