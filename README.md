Music Player — Android
Толық функционалды Android-плеер: қараңғы тақырып, жанрлар, альбом мұқабалары және плейлисттерді қолдау.

Жоба құрылымы

```
MusicPlayer/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/musicplayer/
│       │   ├── MainActivity.kt          ← Главный экран (треки + плейлисты)
│       │   ├── PlayerActivity.kt        ← Полноэкранный плеер
│       │   ├── PlaylistActivity.kt      ← Просмотр плейлиста
│       │   ├── adapters/
│       │   │   ├── TrackAdapter.kt      ← Список треков
│       │   │   └── PlaylistAdapter.kt   ← Список плейлистов
│       │   ├── models/
│       │   │   ├── Track.kt             ← Модель трека
│       │   │   └── Playlist.kt          ← Модели плейлиста и связки
│       │   ├── services/
│       │   │   └── MusicService.kt      ← Фоновый сервис воспроизведения
│       │   └── utils/
│       │       ├── AppDatabase.kt       ← Room БД + DAO
│       │       └── MediaScanner.kt      ← Сканирование музыки с устройства
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   ├── activity_player.xml
│           │   ├── activity_playlist.xml
│           │   ├── item_track.xml
│           │   └── item_playlist.xml
│           ├── drawable/               ← Все иконки (векторные)
│           ├── menu/main_menu.xml      ← Поиск + сортировка
│           └── values/
│               ├── colors.xml
│               ├── strings.xml
│               └── themes.xml
├── build.gradle
├── settings.gradle
└── gradle.properties
```

Android Studio-да орнату қадамдары
Жобаны ашу  
File → Open → MusicPlayer қалтасын таңда
Gradle синхрондауын күт (1–3 мин болуы мүмкін)

SDK тексеру  
File → Project Structure → SDK Location
Android SDK 34 орнатылғанына көз жеткіз
Егер жоқ болса: SDK Manager → Android 14.0 (API 34) → Install

Kotlin плагинін тексеру  
File → Settings → Plugins → Kotlin — 1.9.x нұсқасы болуы керек

Gradle синхрондау  
"Sync Now" батырмасын бас немесе
File → Sync Project with Gradle Files

Жобаны іске қосу  
USB-отладкасы қосулы Android құрылғысын жалға немесе API 26+ эмулятор жаса
Run батырмасын бас

Функциялар
Автосканерлеу — алғашқы іске қосқанда барлық MP3/FLAC/AAC табады

Альбом мұқабалары — барлық жерде көрсетіледі

Жанрлар — ID3 метадеректерінен оқылады

Сұрыптау — атау, қосылған күні, жанр, орындаушы, альбом, жыл бойынша

Іздеу — атау, орындаушы, альбом бойынша нақты уақытта

Плейлисттер — жасау, қайта атау, жою, тректерді қосу

Түстер палитрасы — толық плеер фонды альбом мұқабасына сәйкес өзгертеді

Қайталау — жоқ / барлығы / бір трек

Shuffle — кездейсоқ ойнату

Мини-плеер — экранның төменгі жағында әрқашан көрінеді

Хабарлама — ойнатуды шторкадан басқару

База деректер — Room, тректер мен плейлисттер сақталады

Мүмкін қателер және шешімдер
Unresolved reference: MusicBinder → Build → Clean Project → Rebuild

Manifest merger failed → minSdk ≥ 26 екеніне көз жеткіз

Тректер жоқ → Қолданбаға файлдарға рұқсат бер

Room schema export warning → елемеуге болады

Gradle синхрондау тоқтап қалды → интернетті тексер

Минималды талаптар
Android 8.0 (API 26) және жоғары

Медиафайлдарды оқу рұқсаты

Алғашқы Gradle тәуелділіктерін жүктеу үшін интернет

Түстер схемасы
Түс	HEX	Қолданылуы
Фон	#0F0F14	Негізгі фон
Беткі қабат	#1A1A24	Тулбар, мини-плеер
Акцент	#BB86FC	Батырмалар, жанр-чиптер
Текст	#FFFFFF	Заголовоктар
Қосымша	#9E9EA8	Уақыт, қосымша жазулар

Crossfade тректер арасында

Дыбыс визуализациясы (Visualizer API)
