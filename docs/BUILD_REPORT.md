# تقرير البناء والاختبار — HossamMo Cash 1.1.0

- الحزمة: `com.hossammo.cash` — الإصدار 1.1.0 (versionCode 2)
- مكان البناء: GitHub Actions (Ubuntu، JDK 17، Gradle 8.14.3، AGP 8.10.1) — البناء رقم build-16
- Commit: 0c8ea8e

## نتيجة كل أمر (Exit Code)
| الأمر | النتيجة |
|---|---|
| ./gradlew clean | 0 ✅ |
| ./gradlew testDebugUnitTest | 0 ✅ |
| ./gradlew lintDebug | 0 ✅ (0 أخطاء، 19 تحذيرًا، 3 تلميحات) |
| ./gradlew assembleDebug | 0 ✅ |
| ./gradlew assembleRelease | 0 ✅ |
| ./gradlew bundleRelease | 0 ✅ |

## الاختبارات: 64 اختبارًا، 0 فشل
| المجموعة | العدد |
|---|---|
| domain.FinanceCalculatorTest | 14 |
| domain.CurrencyConverterTest | 9 |
| domain.TransferAndDebtTest | 9 |
| domain.MetalCalculatorTest | 7 |
| data.RepositoryScenariosTest | 11 |
| data.MandatoryScenarioTest (931,000 ← 950,000 = +19,000 / 2.0408%، الدين لا يؤثر، التحويل لا يغير الإجمالي) | 3 |
| data.MetalsTest | 2 |
| data.RateProvidersTest | 4 |
| ui.ComponentsUiTest (Compose) | 5 |

## الملفات الناتجة
| الملف | الحجم | التوقيع |
|---|---|---|
| app-debug.apk | 41,630,871 بايت (~41.6 MB) | موقّع بمفتاح debug ثابت من المستودع |
| app-release-unsigned.apk | 22,844,422 بايت (~22.8 MB) | غير موقّع (يحتاج مفتاح Release) |
| app-release.aab | 16,099,975 بايت (~16.1 MB) | غير موقّع |

## التحذيرات المتبقية (lint)
- UseKtx ×12 (اقتراحات أسلوبية لاستخدام امتدادات Kotlin)
- ConfigurationScreenWidthHeight ×3، UnusedResources ×2، ObsoleteSdkInt ×1
- OldTargetApi ×1 (targetSdk 35؛ يمكن رفعه لاحقًا)

## لقطات الشاشة
التُقطت من التطبيق الحقيقي على محاكي Android 14 (Pixel 6) بسكربت `tools/screenshots/drive.py`،
والأرقام الظاهرة أُدخلت على المحاكي فقط (سيناريو الاختبار) وليست بيانات افتراضية داخل التطبيق.

## تغيير الاسم والأيقونة
- الاسم: `app_name` و`app_title_ar` في `tools/strings_ar.py` ثم `python3 tools/gen_strings.py`.
- الأيقونة: `app/src/main/res/drawable/ic_launcher_foreground.xml` و`values/colors.xml` (خلفية الأيقونة).

## إنشاء مفتاح توقيع Release
```
keytool -genkeypair -v -keystore release.jks -alias hossammo -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks   # الناتج يُحفظ في GitHub Secret باسم KEYSTORE_BASE64
```
ثم أضف الأسرار KEYSTORE_PASSWORD وKEY_ALIAS وKEY_PASSWORD في إعدادات المستودع، وسيُنتج البناء التالي APK/AAB موقّعين تلقائيًا.
