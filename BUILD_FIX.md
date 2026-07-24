# Сборка v5 — зависимости и манифест

## Разрешения в AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
```

## Receivers в AndroidManifest.xml

```xml
<receiver android:name=".reminder.ReminderAlarmReceiver" android:exported="false" />

<receiver android:name=".reminder.BootReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
    </intent-filter>
</receiver>

<receiver android:name=".reminder.SnoozeReceiver" android:exported="false" />

<receiver
    android:name=".widget.NoteWidgetReceiverSmall"
    android:exported="true"
    android:label="@string/widget_size_small">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data android:name="android.appwidget.provider" android:resource="@xml/note_widget_info_small" />
</receiver>
<receiver
    android:name=".widget.NoteWidgetReceiverWide"
    android:exported="true"
    android:label="@string/widget_size_wide">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data android:name="android.appwidget.provider" android:resource="@xml/note_widget_info_wide" />
</receiver>
<receiver
    android:name=".widget.NoteWidgetReceiverLarge"
    android:exported="true"
    android:label="@string/widget_size_large">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data android:name="android.appwidget.provider" android:resource="@xml/note_widget_info_large" />
</receiver>
```

**Важно:** `android:exported="true"` ОБЯЗАТЕЛЬНО для виджетов.

## res/xml/note_widget_info_small.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp" android:minHeight="110dp"
    android:minResizeWidth="180dp" android:minResizeHeight="110dp"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:description="@string/widget_description" />
```

Аналогично `_wide.xml` (`minWidth="320dp"`) и `_large.xml` (`minWidth="320dp"`, `minHeight="220dp"`).

**НЕ используйте** `targetCellWidth`/`previewLayout` — требуют API 31+.

## Зависимости (libs.versions.toml)

```toml
[versions]
datastore = "1.1.1"
paging = "3.3.0"
glance = "1.1.0"

[libraries]
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-paging-runtime = { group = "androidx.paging", name = "paging-runtime-ktx", version.ref = "paging" }
androidx-paging-compose = { group = "androidx.paging", name = "paging-compose", version.ref = "paging" }
androidx-glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
androidx-glance-material3 = { group = "androidx.glance", name = "glance-material3", version.ref = "glance" }
```

## Диагностика

**Напоминания:** `adb logcat -s ReminderScheduler:* ReminderAlarmReceiver:* ReminderNotifier:* BootReceiver:*`

**Виджеты:** `adb logcat -s BaseNoteWidget:* refreshAllWidgets:*`
- Если `provideGlance` не вызывается → проверьте `exported="true"` в манифесте
- Если `Loaded 0 notes` → нет не-зашифрованных не-удалённых заметок
- После установки APK удалите виджет с экрана и добавьте заново

## Локализация

- `res/values/strings.xml` — русский
- `res/values-en/strings.xml` — английский
- `LanguageHelper` (SharedPreferences) — синхронное чтение для `attachBaseContext`
- `SettingsManager` (DataStore) — Compose-флоу
- `attachBaseContext` в `ObsidianKeepApp` и `MainActivity` применяет локаль до UI
