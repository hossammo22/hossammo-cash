# المكتبات وسبب استخدامها

| المكتبة | الإصدار | السبب |
|---|---|---|
| Kotlin / Coroutines / StateFlow | 2.1.21 / 1.10.2 | اللغة والبرمجة غير المتزامنة وحالة الواجهة. |
| Jetpack Compose (BOM) + Material 3 | 2025.04.01 | الواجهات الحديثة، الوضع الداكن، دعم RTL. |
| Navigation Compose | 2.8.9 | التنقل بين الشاشات. |
| Lifecycle / ViewModel | 2.8.7 | MVVM وجمع الحالة بأمان. |
| Hilt + hilt-work + hilt-navigation-compose | 2.56.2 / 1.2.0 | حقن الاعتماديات. |
| Room | 2.7.1 | قاعدة البيانات المحلية مع Transactions و Migrations. |
| SQLCipher for Android (net.zetetic) | 4.9.0 | تشفير قاعدة البيانات بالكامل ومتوافق مع Room. |
| Android Keystore (من النظام) | — | حماية مفتاح تشفير القاعدة. |
| DataStore Preferences | 1.1.4 | الإعدادات غير المالية. |
| WorkManager | 2.10.1 | الفحص اليومي للإشعارات المحلية والتحديث التلقائي للأسعار. |
| Biometric | 1.1.0 | الدخول بالبصمة عبر BiometricPrompt. |
| Fragment KTX | 1.8.6 | FragmentActivity المطلوبة لـ BiometricPrompt. |
| OkHttp | 4.12.0 | طلب أسعار العملات عبر HTTPS مع مهلات. |
| kotlinx.serialization | 1.8.1 | قراءة ردود الأسعار وصيغة النسخ الاحتياطي. |
| FastExcel | 0.18.4 | تصدير Excel (xlsx) خفيف ومناسب لأندرويد. |
| Android PdfDocument + StaticLayout (من النظام) | — | PDF عربي RTL بخط Noto Naskh Arabic مضمّن، دون مكتبات ثقيلة. |
| FileProvider (core) | — | مشاركة التقارير والمرفقات بأمان. |
| Compose Canvas (رسومات داخلية) | — | رسومات تفاعلية بدون مكتبة خارجية: خطي، أعمدة، دائري، أشرطة، مع RTL والضغط لعرض التفاصيل. |
| JUnit 4, Robolectric 4.14.1, Compose UI Test, coroutines-test, Room testing | — | اختبارات الحسابات والمستودعات والسيناريوهات والواجهات. |

لا يستخدم التطبيق Firebase أو Supabase أو أي خدمة سحابية أو تحليلات أو تقارير أعطال خارجية أو WebView.
