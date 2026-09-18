# WolFox Test Modules — البنية الكاملة

## الطبقات

- `app/src/main/java/com/wolfox/gps/gate`: بوابة التفعيل المنطقية عبر `LicenseManager`.
- `manager`: الموقع، التحكم، الأيقونة العائمة، والتبديل المتعدد.
- `hook`: هوكات الموقع ومختبر الكاميرا وبوابة الحزم المسموحة.
- `ui`: لوحة WolFox والتفعيل والبحث والمفضلة والسجل والإعدادات.
- `model`: نماذج الموقع والمفضلة والسجل.
- `util`: التخزين والثيم والبحث وتحليل روابط الخرائط والسجل.
- `DirectBootstrap`: الجسر المقابل لـWFSniperGateBridge في مشروع iOS.
- `profiles`: تعريفات SAFE وCAMERA وMULTI.
- `backend`: API ولوحة الترخيص وقاعدة البيانات.
- `tests`: اختبارات التوافق وعدم الاعتماد على Root.

## الحماية

يعمل المشروع فقط داخل `com.wolfox.testapp` أو `com.wolfox.sandbox` وفروعهما.
لا توجد حزم حكومية أو تطبيقات خارجية في قائمة السماح.

## البناء على Linux

اضبط `ANDROID_SDK_ROOT` أو `ANDROID_PLATFORM_JAR` و`ANDROID_BUILD_TOOLS`، ثم:

```bash
chmod +x build-local.sh build-all-test-variants.sh
./build-all-test-variants.sh
```

تظهر الملفات والتجزئات في `dist/`.

## GitHub Actions

المسار `.github/workflows/build.yml` يشغّل الاختبارات ثم يبني SAFE وCAMERA وMULTI
ويرفعها في Artifact واحد. للتوقيع بمفتاح ثابت أضف أسرار المستودع:
`WOLFOX_KEYSTORE_BASE64` و`WOLFOX_KEYSTORE_PASS` و`WOLFOX_KEY_PASS`
و`WOLFOX_KEY_ALIAS`. عند غيابها ينشئ البناء كلمة مرور ومفتاح اختبار مؤقتين
داخل جلسة CI فقط، ولا يحفظهما في المستودع.
