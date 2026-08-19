#!/bin/sh

cd "$(dirname "$0")/.."

if [ "$#" -gt 0 ]; then
    VERSION_NAME=$1
else
    echo "Version Name MUST be defined!"
    echo "Usage: build_android_elements.sh version_name"
    exit 1
fi

echo '#######################################'
echo '#   BUILD ANDROID ELEMENTS TO LOCAL   #'
echo '#######################################'

OUTPUT_DIR="Products/Android/$VERSION_NAME"
mkdir -p "$OUTPUT_DIR"

MODULES=("proximityverification" "proximityverification-android" "proximityverification-iosarm64")

for MODULE in "${MODULES[@]}"; do
    M2_PATH="$HOME/.m2/repository/it/infocert/proximityverification/$MODULE/$VERSION_NAME"
    DEST="$OUTPUT_DIR/$MODULE"
    if [ -d "$M2_PATH" ]; then
        mkdir -p "$DEST"
        cp -R "$M2_PATH/"* "$DEST/"
        echo "Copied $M2_PATH → $DEST"
    else
        echo "Directory not found: $M2_PATH"
        exit 1
    fi
done

echo '#######################################'
echo '#        ANDROID BUILD COMPLETE       #'
echo '#######################################'