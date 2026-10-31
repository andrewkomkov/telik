# Releasing

1. Update `versionName` and `versionCode` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/*/changelogs/<versionCode>.txt`.
3. Push a tag: `git tag v1.0.0 && git push origin v1.0.0`.

`.github/workflows/release.yml` tests, signs and publishes the APK with its checksums. It reads the
`TELIK_KEYSTORE_BASE64` and `TELIK_KEYSTORE_PASSWORD` repository secrets.
