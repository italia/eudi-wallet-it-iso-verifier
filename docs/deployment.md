<!-- Last scan: 2026-04-14 -->
# Deployment

The library is distributed as an Android AAR (Maven) and an iOS static framework (CocoaPods). Build and publish are fully scripted via `build_scripts/`.

## Build

```bash
# Full local build — generates Products/ without publishing
export $(cat .env | xargs)
bash build_scripts/update_build_publish_all.sh --no-publish

# Full release build + publish to GitLab registries
export $(cat .env | xargs)
bash build_scripts/update_build_publish_all.sh
```

> `update_build_publish_all.sh` must be run with **`bash`**, not `sh`.

The canonical pipeline script (`update_build_publish_all.sh`) calls the lower-level scripts in order:
1. Resolves branch-aware version via `./gradlew :proximityverification:printResolvedVersion`
2. `./gradlew clean build` (excluding samples and tests)
3. `./gradlew publishToMavenLocal`
4. `build_scripts/build_android_elements.sh <version>` — packages AAR + POM to `Products/Android/<version>/`
5. `./gradlew :proximityverification:linkReleaseFrameworkIosArm64`
6. `build_scripts/build_ios_elements.sh <version>` — packages `.framework`, `.podspec`, `.tar.gz` to `Products/iOS/<version>/`
7. Publishes Android artifact to GitLab Maven registry via `mvn deploy`
8. Publishes iOS artifact to GitLab Generic Package registry via `curl`

## Artifacts

| Platform | Format | Output path |
|----------|--------|-------------|
| Android | AAR + POM | `Products/Android/<version>/proximityverification-android/` |
| iOS | Static framework + podspec + tar.gz | `Products/iOS/<version>/` |

> `Products/` is gitignored — generated locally or by CI.

## Environment

Required for publish flow (load from `.env` or export directly):

| Variable | Description |
|----------|-------------|
| `GITLAB_PRIVATE_TOKEN` | GitLab personal access token with package write scope |
| `GITLAB_HOST` | GitLab host (e.g. `gitlab.example.com`) |
| `PROJECT_ID` | GitLab project numeric ID |

## Versioning

- Version source of truth: `proximityverification/build.gradle.kts` (`version = "..."`)
- Branch-aware version resolved at build time by Gradle task `:proximityverification:printResolvedVersion`
- Semantic versioning: MAJOR for breaking changes, MINOR for additive, PATCH for fixes
- Podspec versions aligned at packaging time by `build_scripts/build_ios_elements.sh`

## Integration by Consumers

### Android (Maven)

Add to `build.gradle.kts`:
```kotlin
// Repository configuration — see docs/INTEGRATION_GUIDE.md
implementation("it.infocert.proximityverification:proximityverification-android:<version>")
```

### iOS (CocoaPods)

```ruby
pod 'proximityverification', :http => 'https://<registry>/proximityverification-<version>.tar.gz'
```

See [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) for full setup including auth, repository config, and Swift usage.

## Run Samples Locally

```bash
# Android sample
./gradlew :samples:sampleandroid:installDebug

# iOS sample
cd samples/ProximityDemoApp && pod install
# Then open ProximityDemoApp.xcworkspace in Xcode
```

## CI/CD

No CI configuration is present in the repository. Builds are triggered manually via the build scripts.
