# HossamMo Cash — متابعة السيولة والمدخرات

تطبيق Android أصلي (Kotlin + Jetpack Compose) لمتابعة أماكن السيولة والمدخرات شهرًا بشهر، ومقارنة كل عنصر بالشهر السابق، مع قسم ديون منفصل تمامًا عن السيولة.

- **اسم الحزمة:** `com.hossammo.cash`
- **الإصدار:** 1.0.0 (versionCode 1)
- **أقل إصدار Android:** 8.0 (API 26) — **الهدف:** API 35
- **اللغة:** عربي بالكامل واتجاه RTL، والعملة الأساسية الافتراضية الجنيه المصري EGP.
- **الخصوصية:** يعمل دون إنترنت بالكامل. البيانات محلية ومشفرة (SQLCipher) ومفتاحها محمي في Android Keystore. الإنترنت يُستخدم فقط لجلب جدول أسعار العملات العام عبر HTTPS دون إرسال أي بيانات.

## ما الذي يفعله التطبيق

| القسم | المحتوى |
|---|---|
| الرئيسية | إجمالي الشهر المختار، التغير عن الشهر السابق ونسبته، التغير منذ أول شهر، الأعلى والأقل والمتوسط، أفضل وأسوأ شهر، أكبر زيادة وانخفاض، أكبر نسبة، السيولة الفورية، الأصول التي تحتاج تصفية، تواريخ آخر تحديث، وقائمة مختصرة لكل عنصر. |
| الرسومات | 11 رسمًا تفاعليًا (خطي، دائري، أعمدة، أشرطة) مع فلاتر 3/6 أشهر وسنة وسنتين وكامل المدة وفترة مخصصة، والضغط على أي شهر يعرض تفاصيله. |
| السيولة والمدخرات | عرض بطاقات/جدول/تجميع حسب الفئة أو الموقع أو العملة، إضافة وتعديل ونسخ وأرشفة وحذف واستعادة، بحث وفلترة وترتيب، تفاصيل وتاريخ كامل لكل عنصر، الأشهر، إدخال أرصدة شهر كامل، مقارنة أي شهرين، التحويلات الداخلية، الفئات. |
| المدخرات المتغيرة | المزرعة والمصنع والعملات الأجنبية مع سعر الصرف ونوعه (تلقائي/يدوي/محفوظ/مثبت)، وتحديث الأسعار الآن أو تلقائيًا، وسعر يدوي. |
| الديون | مستحق لي / ملتزم به، دفعات متعددة لكل دين، الحالات، المتأخر وأقرب استحقاق، المرفقات المحلية، ورسم مقارنة — ولا يدخل أي دين في إجمالي السيولة. |
| التقارير والإعدادات | 14 تقريرًا + تصدير كل البيانات إلى PDF عربي وExcel وCSV مع المشاركة، النسخ الاحتياطي المشفر والاستعادة، الأمان (PIN، البصمة، القفل التلقائي، FLAG_SECURE)، العملات، الإشعارات المحلية، سلة المحذوفات، سجل التعديلات، حذف كل البيانات. |

عند أول تشغيل تُنشأ الفئات الأساسية وعناصر العملات وأسماء الديون المطلوبة **بقيم فارغة**، ولا تظهر أي أرقام تجريبية.

## البناء

المتطلبات: JDK 17 أو أحدث، Android SDK (platform 35 و build-tools 35)، واتصال بمستودعات Google Maven و Maven Central.

```bash
./gradlew clean
./gradlew testDebugUnitTest   # اختبارات الحسابات + اختبارات Robolectric للمستودعات والواجهات
./gradlew lintDebug
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release(-unsigned).apk
./gradlew bundleRelease       # app/build/outputs/bundle/release/app-release.aab
```

يوجد أيضًا ملف GitHub Actions في `.github/workflows/android.yml` يشغّل الأوامر الستة بالترتيب ويحفظ ناتج كل أمر ورمز خروجه، ثم ينشر ملفات APK و AAB وتقارير الاختبارات.

### توقيع نسخة الإصدار

بدون مفتاح توقيع يُبنى Release غير موقّع (صالح للفحص لا للتثبيت). لإنشاء مفتاح:

```bash
keytool -genkeypair -v -keystore release.jks -alias hossammo -keyalg RSA -keysize 4096 -validity 10000
```

ثم أنشئ في جذر المشروع ملف `keystore.properties` (مستبعد من Git):

```properties
storeFile=release.jks
storePassword=كلمة_مرور_المخزن
keyAlias=hossammo
keyPassword=كلمة_مرور_المفتاح
```

بعدها `./gradlew assembleRelease bundleRelease` ينتج ملفات موقعة. احتفظ بنسخة آمنة من `release.jks` وكلمات المرور؛ فقدانها يمنع تحديث التطبيق على Google Play.
في GitHub Actions ضع المفتاح كأسرار: `KEYSTORE_BASE64` (ناتج `base64 -w0 release.jks`) و `KEYSTORE_PASSWORD` و `KEY_ALIAS` و `KEY_PASSWORD`.

### تغيير الاسم والأيقونة

- **الاسم الظاهر:** `app_name` و `app_title_ar` في `tools/strings_ar.py` ثم شغّل `python3 tools/gen_strings.py` (يولّد `app/src/main/res/values/strings.xml` ويتحقق أن كل نص مستخدم معرّف).
- **الأيقونة:** ملفات متجهة في `app/src/main/res/drawable/ic_launcher_foreground.xml` و `ic_launcher_monochrome.xml`، ولون الخلفية `ic_launcher_background` في `values/colors.xml`. يمكن أيضًا استخدام Image Asset Studio في Android Studio لاستبدالها.
- **اسم الحزمة:** `applicationId` و `namespace` في `app/build.gradle.kts` (تغييره يجعل التطبيق تطبيقًا جديدًا على الجهاز).

## التوثيق

- [بنية المشروع](docs/ARCHITECTURE.md)
- [قاعدة البيانات](docs/DATABASE.md)
- [المكتبات وسبب استخدامها](docs/LIBRARIES.md)
- [الأمان والخصوصية](docs/SECURITY.md)
