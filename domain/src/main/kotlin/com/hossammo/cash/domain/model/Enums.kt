package com.hossammo.cash.domain.model

/** نوع الأصل لعنصر السيولة. */
enum class AssetType { CASH, BANK_ACCOUNT, GOLD, STOCKS, PROJECT, FOREIGN_CURRENCY, LIQUIDATABLE_ASSET, CUSTOM }

/** درجة السيولة. */
enum class LiquidityLevel { IMMEDIATE, WITHIN_DAYS, NEEDS_LIQUIDATION, UNSPECIFIED }

/** حالة العنصر مقارنة بالشهر السابق. */
enum class TrendStatus { INCREASED, DECREASED, STABLE, NEW }

/** مصدر سعر الصرف المستخدم داخل سجل شهري. */
enum class RateSource {
    /** العملة الأساسية، لا تحتاج سعرًا. */
    BASE,
    /** آخر سعر تم جلبه تلقائيًا. */
    AUTO,
    /** سعر أدخله المستخدم يدويًا. */
    MANUAL,
    /** آخر سعر محفوظ محليًا (عند عدم توفر الإنترنت). */
    SAVED,
    /** سعر مثبت للشهر. */
    LOCKED
}

/** اتجاه الدين. */
enum class DebtDirection { OWED_TO_ME, I_OWE }

/** حالة الدين المحسوبة. */
enum class DebtStatus { OPEN, PARTIALLY_PAID, COMPLETED, OVERDUE, NO_DUE_DATE }
