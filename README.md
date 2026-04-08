# ThreeStrip

ThreeStrip is a fully local Android-native chat console for Galaxy-S25-class phones.

- Kotlin + Jetpack Compose
- On-device LLM inference via MediaPipe `tasks-genai`
- Local Android `TextToSpeech`
- Room for transcript history
- DataStore for local settings and model selection
- No internet permissions

## Build

```bash
cd android-app
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
./gradlew connectedDebugAndroidTest
```

## Privacy

- No `INTERNET`
- No `ACCESS_NETWORK_STATE`
- No backend
- No analytics
- No cloud speech or model calls

## Model import

Import a local `.task` or `.litertlm` model from device storage. The app copies it into app-private storage and persists the selected path.
