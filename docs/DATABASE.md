# قاعدة البيانات

Room (الإصدار 1) مشفرة بالكامل عبر SQLCipher. كل المعرفات UUID نصية، والمبالغ مخزنة كنص BigDecimal لتجنب أخطاء الفاصلة العائمة. كل جدول قابل للحذف المؤقت يحتوي `createdAt` و `updatedAt` و `deletedAt`.

| الجدول | الوصف | أهم الفهارس / المفاتيح |
|---|---|---|
| `savings_categories` | الفئات | deletedAt, sortOrder |
| `locations` | المواقع والمشروعات | deletedAt, name |
| `liquidity_items` | عناصر السيولة (الاسم، الفئة، الموقع، نوع الأصل، العملة، اللون، الأيقونة، درجة السيولة، ضمن الإجمالي، مؤرشف، متغير، ملاحظات) | FK → الفئة والموقع؛ categoryId, locationId, currencyCode, deletedAt, isArchived |
| `savings_months` | الأشهر (yyyyMM) | monthKey فريد، deletedAt |
| `monthly_balances` | الرصيد الشهري: القيمة الأصلية، العملة، سعر الصرف، القيمة بالجنيه، مصدر السعر، مثبت، ملاحظات | FK → الشهر والعنصر؛ (monthId,itemId) فريد، monthKey, itemId, currencyCode, deletedAt |
| `transfers` | التحويلات الداخلية: من، إلى، المبلغ، المستلم، العملة، الرسوم، التاريخ، المرجع | FK → العناصر؛ fromItemId, toItemId, monthKey, deletedAt |
| `currency_rates` | آخر سعر ناجح لكل عملة ومصدره | PK currencyCode، fetchedAt |
| `monthly_currency_snapshots` | السعر المثبت لعملة داخل شهر | FK → الشهر؛ (monthId,currencyCode) فريد |
| `debts` | الديون: الاسم، الاتجاه، المبلغ الأصلي (اختياري)، البداية، الاستحقاق | direction, dueDate, deletedAt |
| `debt_payments` | دفعات الديون (عدة دفعات لكل دين) | FK → الدين؛ debtId, paymentDate, deletedAt |
| `attachments` | مرفقات محلية للديون والدفعات | (ownerType, ownerId), deletedAt |
| `app_settings` | إعدادات مرتبطة بالبيانات (العملة الأساسية، خصم الرسوم) | key |
| `backup_history` | سجل النسخ الاحتياطية | createdAt |
| `audit_log` | سجل التعديلات المحلي | timestamp, (entityType, entityId) |

## الترحيل (Migrations)

المخطط محفوظ في `app/schemas/` (exportSchema). عند تغيير البنية: ارفع `AppDatabase.VERSION` وأضف `Migration` إلى `AppDatabase.MIGRATIONS`. لا يوجد `fallbackToDestructiveMigration` حتى لا تُحذف بيانات المستخدم أبدًا. ملفات النسخ الاحتياطي تحمل رقم إصدار المخطط ويُحوَّل محتواها خطوة بخطوة في `BackupManager.migrate`.

## الإعدادات غير المالية

تُحفظ في DataStore: الاسم، الوضع، شكل الأرقام، إخفاء القيم، PIN (مجزأ PBKDF2 مع ملح)، البصمة، القفل التلقائي، FLAG_SECURE، مصدر الأسعار ودورية التحديث، والإشعارات.
