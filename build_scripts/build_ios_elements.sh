#!/bin/sh

cd "$(dirname "$0")/.."

if [ "$#" -gt 0 ]; then
    VERSION_NAME=$1
else
    echo "Version Name MUST be defined!"
    echo "Usage: build_ios_elements.sh version_name"
    exit 1
fi

echo '#######################################'
echo '#   BUILD iOS ELEMENTS TO LOCAL       #'
echo '#######################################'

OUTPUT_DIR="Products/iOS/$VERSION_NAME"
TMP_BASE="$OUTPUT_DIR/temp_podspec"
mkdir -p "$TMP_BASE"
mkdir -p "$OUTPUT_DIR"

PODSPEC_SOURCE="build_scripts/proximityverification.podspec"
PODSPEC_FILE="proximityverification.podspec"
TAR_NAME="proximityverification.tar.gz"
FRAMEWORK_DIR="proximityverification/build/bin/iosArm64/podReleaseFramework/proximityverification.framework"

if [ ! -d "$FRAMEWORK_DIR" ]; then
    echo "Framework directory not found: $FRAMEWORK_DIR"
    rm -rf "$TMP_BASE"
    exit 1
fi

# Copy podspec into temp area and update version
if [ -f "$PODSPEC_SOURCE" ]; then
    cp "$PODSPEC_SOURCE" "$TMP_BASE/$PODSPEC_FILE"
    sed -i.bak "s/spec.version                  = '.*'/spec.version                  = '$VERSION_NAME'/" "$TMP_BASE/$PODSPEC_FILE"
    echo "Copied and updated podspec version to $VERSION_NAME"
else
    echo "Podspec file not found: $PODSPEC_SOURCE"
    rm -rf "$TMP_BASE"
    exit 1
fi

# Prepare output
cp "$TMP_BASE/$PODSPEC_FILE" "$OUTPUT_DIR/"
cp -R "$FRAMEWORK_DIR" "$OUTPUT_DIR/"

# Update Info.plist
/usr/libexec/PlistBuddy -c "Set :CFBundleIdentifier it.infocert.proximityverification" "$OUTPUT_DIR/proximityverification.framework/Info.plist"
/usr/libexec/PlistBuddy -c "Delete :MinimumOSVersion" "$OUTPUT_DIR/proximityverification.framework/Info.plist" 2>/dev/null
/usr/libexec/PlistBuddy -c "Add :MinimumOSVersion string 16.0" "$OUTPUT_DIR/proximityverification.framework/Info.plist"

# Archive
cd "$OUTPUT_DIR"
gtar czf "$TAR_NAME" "$PODSPEC_FILE" "proximityverification.framework"
cd - > /dev/null

# Remove temp directory
rm -rf "$TMP_BASE"

# Update symlink "latest"
LATEST=$(ls -d $PWD/Products/iOS/*/ | grep -v latest | sort -V | tail -n1)
ln -sfn "$LATEST" "$PWD/Products/iOS/latest"
echo "Symlink Updated: Products/iOS/latest -> $LATEST"

echo '#######################################'
echo '#         iOS BUILD COMPLETE          #'
echo '#######################################'