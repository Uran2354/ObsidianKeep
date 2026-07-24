# ObsidianKeep v5

Заметки в стиле Obsidian/Google Keep для Android. Kotlin + Jetpack Compose + Material 3 + Room + Hilt + WorkManager.

## Возможности

### Заметки и редактор
- Compose + Material 3, тёмная тема
- 8 цветов карточек
- Поле с нумерацией строк, моноширинный шрифт
- **Подсветка синтаксиса Markdown** прямо в режиме редактирования
- **Markdown-предпросмотр** с рендерингом заголовков, жирного, курсива, кода, списков, чек-листов, цитат, ссылок, изображений, горизонтальных линий
- **Тапаемые чек-листы** в режиме предпросмотра
- **`[[вики-ссылки]]`** + обратные ссылки (backlinks)
- **Автодополнение `[[ ]]`** с пагинацией (30 за раз, подгрузка при прокрутке)
- **Автодополнение `#тегов`**
- **Find & Replace** — поиск и замена в рамках заметки
- **Избранное** ⭐
- **Напоминания** — DatePicker + TimePicker, повтор ежедневно/по будням/еженедельно, Snooze 15 мин
- **Вставка изображений** — `![](attachments/<noteId>/<uuid>.png)`, хранение в `filesDir/attachments/`
- **Экспорт** — PDF, HTML, TXT, печать, Markdown (.md)

### Папки и безопасность
- Папки с переименованием, перемещением, массовым удалением
- **Биометрическая защита** папок
- **AES-256-GCM** шифрование, мастер-ключ в Android Keystore
- **PIN-код приложения** (PBKDF2-HMAC-SHA256, 100k итераций)
- **Decoy PIN** — фейковый PIN открывает специальную скрытую папку под именем нажатой
- **Авто-блокировка** — 15/30/60/120/300 секунд
- **Корзина 10 дней** с авто-очисткой

### Календарь
- 4 режима: **Неделя / Месяц / Год / День**
- Сетка месяца как в Google Calendar с точками-индикаторами
- Создание заметок с напоминанием на выбранный день
- Удаление заметок прямо из календаря

### Аудио-заметки (диктофон)
- Запись аудио (AAC, m4a, 128 kbps) — отдельные файлы в `filesDir/audio/`
- Воспроизведение с прогрессом «00:05 / 00:42»
- Таймер записи и воспроизведения
- Удаление записей
- НЕ создаёт текстовые заметки — чистый диктофон

### Граф связей
- Визуализация заметок как узлов, `[[ссылки]]` как рёбер
- Compose Canvas, pinch-to-zoom, drag-to-pan, tap-to-open

### Поиск
- **Быстрый поиск** с подсветкой совпадений
- **Расширенный поиск** с фильтрами: цвет, папка, тег, напоминание, избранное
- Радужный круг = «любой цвет»
- Скрытие заметок из залоченных защищённых папок

### Виджеты (Glance)
- 3 размера: **2x2** (3 заметки), **4x2** (5), **4x4** (8)
- Обновление при сохранении/удалении заметок
- Фильтр по папке

### Синхронизация и импорт
- Экспорт/импорт в .zip (manifest.json + notes/*.md)
- Импорт .md файла
- Импорт из папки (vault)
- Зеркалирование в папку для Obsidian на ПК
- Быстрый захват текста (ACTION_SEND)

### Локализация (RU/EN)
- 160+ строк
- Сохранение через `LanguageHelper` (SharedPreferences) + `SettingsManager` (DataStore)
- `attachBaseContext` применяет локаль до создания UI
- Язык не сбрасывается при выходе

## Архитектура

```
UI (Compose)
  └─ NoteViewModel (@HiltViewModel)
       └─ NoteRepository (@Singleton)
            ├─ NoteDao (Room v5)
            ├─ CryptoManager (AES-GCM + Keystore)
            ├─ BackupManager (zip)
            ├─ FileMirrorManager (SAF .md)
            ├─ PdfExporter / HtmlExporter / TxtExporter / NotePrinter
            ├─ AudioNoteManager (MediaRecorder + MediaPlayer, диктофон)
            ├─ ImageAttachmentManager (Bitmap → filesDir/attachments/)
            ├─ SettingsManager + LanguageHelper
            ├─ PinManager (PBKDF2, decoy, auto-lock)
            └─ ReminderScheduler (AlarmManager)
  ├─ WorkManager: TrashCleanupWorker
  ├─ AlarmManager → ReminderAlarmReceiver → ReminderNotifier
  └─ BootReceiver
```

## Структура

```
src/com/example/obsidiankeep/
├── ObsidianKeepApp.kt          ← @HiltAndroidApp + attachBaseContext (язык)
├── MainActivity.kt             ← навигация, LockScreen, FolderPinDialog, attachBaseContext
├── EditorScreen.kt             ← редактор: подсветка, автодополнение, Find&Replace, экспорт
├── FolderScreen.kt             ← заметки в папке + decoy-логика
├── TrashScreen.kt
├── CalendarScreen.kt           ← календарь (неделя/месяц/год/день)
├── AudioNotesScreen.kt         ← диктофон
├── GraphScreen.kt              ← граф связей на Canvas
├── AdvancedSearchScreen.kt     ← поиск с фильтрами
├── SearchScreen.kt
├── TagsScreen.kt
├── MarkdownHelpScreen.kt
├── SettingsScreen.kt           ← PIN/decoy/auto-lock, язык, синхронизация
├── LockScreen.kt               ← экран блокировки + FolderPinDialog
├── NoteViewModel.kt
├── SortOrder.kt
├── components/
│   ├── LineNumberedTextField.kt    ← поле + подсветка синтаксиса
│   ├── MarkdownRenderer.kt         ← парсер + рендер (включая изображения)
│   ├── LinksPanel.kt
│   └── AutocompletePanel.kt        ← автодополнение с пагинацией
├── data/                           ← Room v5 + DAO + entities
├── di/                             ← Hilt
├── repository/                     ← NoteRepository + impl
├── security/
│   ├── CryptoManager.kt
│   └── PinManager.kt
├── backup/                         ← zip + зеркалирование
├── audio/AudioNoteManager.kt       ← диктофон
├── attachments/ImageAttachmentManager.kt
├── reminder/                       ← AlarmManager + receivers
├── export/                         ← Pdf/Html/Txt/Printer
├── settings/                       ← SettingsManager + LanguageHelper
├── sync/WebDavSyncManager.kt
├── paging/NotesPagingSource.kt
├── widget/MultiNoteWidget.kt       ← Small/Wide/Large
└── work/TrashCleanupWorker.kt
```

## Технологии

| Категория | Технология |
|-----------|------------|
| Язык | Kotlin 2.2.10 |
| UI | Jetpack Compose, Material 3 |
| Архитектура | MVVM + Repository |
| DI | Hilt (Dagger 2.60.1) |
| БД | Room 2.7.0 (v5, 4 миграции, индексы) |
| Фон | WorkManager, AlarmManager |
| Навигация | Navigation Compose 2.8.5 |
| Безопасность | Keystore, AES-256-GCM, BiometricPrompt, PBKDF2 |
| Аудио | MediaRecorder (AAC), MediaPlayer |
| Печать | PrintManager + WebView |
| Файлы | SAF, FileProvider |
| Настройки | DataStore + SharedPreferences |
| Пагинация | Paging 3 (SQL LIMIT/OFFSET) |
| Виджет | Glance 1.1.0 |

## Установка

1 Вариант

```bash
git clone https://github.com/Uran2354/ObsidianKeep.git
cd ObsidianKeep
./gradlew assembleDebug
```

Установите `app/build/outputs/apk/debug/app-debug.apk` (API 29+).

2 Вариант

Скачать [ObsidianKeep-v5.zip](https://github.com/user-attachments/files/30327987/ObsidianKeep-v5.zip) 

См. **BUILD_FIX.md** для инструкций по Gradle, version catalog и AndroidManifest.

## Миграции БД

| Версия | Что добавлено |
|--------|---------------|
| v1 → v2 | `deletedAt`, `isEncrypted`, `encryptedKey` |
| v2 → v3 | `reminderAt` |
| v3 → v4 | `repeatRule` + индексы |
| v4 → v5 | `isFavorite`, таблицы `tags`, `note_tags` + индексы |

## Лицензия

MIT
