#!/bin/sh

cd "$(dirname "$0")/.."

resolve_version_name() {
    RESOLVED_VERSION_OUTPUT=$(./gradlew -q :proximityverification:printResolvedVersion) || return 1
    VERSION_NAME=$(printf '%s\n' "$RESOLVED_VERSION_OUTPUT" | tail -n 1 | tr -d '\r')
    if [ -z "$VERSION_NAME" ]; then
        return 1
    fi
    return 0
}

if ! resolve_version_name; then
    echo '#############################################'
    echo '#   Resolved version could not be detected  #'
    echo '#############################################'
    exit 1
fi

echo '#############################################'
printf '#   %-41s #\n' "PROXIMITY VERIFICATION BUILD START"
printf '#   %-41s #\n' "Version: $VERSION_NAME"
echo '#############################################'

echo "1. Clean and build"
./gradlew clean build -x :samples:sampleandroid:build -x test || exit 1

echo "2. Publish to Maven Local"
./gradlew publishToMavenLocal || exit 1

echo "3. Build Android artifacts to Products/Android/$VERSION_NAME"
sh build_scripts/build_android_elements.sh "$VERSION_NAME" || exit 1

echo "4. Build iOS Framework"
./gradlew :proximityverification:linkReleaseFrameworkIosArm64 || exit 1

echo "5. Build iOS artifacts to Products/iOS/$VERSION_NAME"
sh build_scripts/build_ios_elements.sh "$VERSION_NAME" || exit 1

echo '#############################################'
printf '#   %-41s #\n' "PROXIMITY VERIFICATION BUILD END"
printf '#   %-41s #\n' "Version: $VERSION_NAME"
echo '#############################################'
