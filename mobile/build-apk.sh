#!/bin/sh
# APK yig'ish: veb-bundle -> Capacitor sync -> Docker ichida Gradle (JDK 21 + Android SDK 35).
# Yangi fan qo'shish: JSON faylni backend/src/main/resources/seed/ ga qo'ying va shu skriptni ishga tushiring.
# Har yangi versiyada versionCode avtomatik oshiriladi, shunda APK eski ilova ustidan o'rnatiladi.
set -e
cd "$(dirname "$0")"

[ -f keystore/keystore.properties ] || { echo "keystore/keystore.properties topilmadi"; exit 1; }

GRADLE_FILE=android/app/build.gradle
CODE=$(grep -oE 'versionCode [0-9]+' $GRADLE_FILE | grep -oE '[0-9]+')
NEXT=$((CODE + 1))
sed -i "s/versionCode $CODE/versionCode $NEXT/; s/versionName \"[^\"]*\"/versionName \"1.$NEXT\"/" $GRADLE_FILE
echo "Versiya: 1.$NEXT (versionCode $NEXT)"

npm run build
npx cap sync android

docker image inspect test-tizimi-android-build > /dev/null 2>&1 || docker build -t test-tizimi-android-build android-build
mkdir -p "$HOME/.gradle-android"
docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -e GRADLE_USER_HOME=/gradle \
  -v "$HOME/.gradle-android:/gradle" -v "$PWD:/mobile" -w /mobile/android \
  test-tizimi-android-build ./gradlew --no-daemon -q assembleRelease

cp android/app/build/outputs/apk/release/app-release.apk "test-tizimi-1.$NEXT.apk"
echo "Tayyor: $(pwd)/test-tizimi-1.$NEXT.apk"
