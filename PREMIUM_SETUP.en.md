# Gimmer setup and data guide

[Türkçe](PREMIUM_SETUP.md) · **English**

## Google Drive setup

1. Enable the Google Drive API in your Google Cloud project.
2. Configure the OAuth consent screen. If the project is in testing mode, add the accounts you plan to use as test users.
3. Create an Android OAuth client for package `com.mcclabs.gimmer`. Register the SHA-1 certificate fingerprint for each debug or release signing configuration you intend to use. Run `./gradlew :app:signingReport` to find the fingerprints.
4. On a device with Google Play services, open **Ayarlar → Bulut yedeği** (Settings → Cloud Backup) and try a backup.

Google Identity's `AuthorizationClient` handles account selection and authorization. Gimmer requests the `https://www.googleapis.com/auth/drive.appdata` scope. The Android OAuth client matches the package and signing certificate. Keep client secrets and access keys out of the source code. The access token is held in memory.

Drive REST API v3 stores SQLite backups for each installation in `appDataFolder`. Gimmer skips empty databases and creates a new version only when the content changes. It keeps the five most recent versions for the installation after verifying the new copy. Legacy `gimmer-v3.db` and `gimmer-backup.json` backups can also be selected from the restore list.

Gimmer reads records in a single Room transaction and writes them to a separate temporary Room database. It uses TRUNCATE journal mode and closes the database before upload to avoid copying an active WAL file. Temporary files are removed after the operation.

Restore checks the SQLite header, file size, integrity, Room schema, and record values. It merges records in one Room transaction while preserving existing data and skipping identical sets. If a body weight entry already exists for a date, the local value takes precedence. An older backup copy of a set edited on the device may appear as a separate record.

Enable automatic backups under **Ayarlar → Bulut yedeği** (Settings → Cloud Backup). Select an account and grant access, then choose an interval of 1 hour, 6 hours, 24 hours, or 7 days. Android runs the job when an internet connection is available and background scheduling permits it. If Google asks for authorization again, use **İzni yenile** (Renew Access) in Settings.

## Room and workout records

Room migrations preserve existing records. Schema version 3 added `isWarmup`, `rir`, and the `bodyweight_logs` table. Version 4 added nutrition records. New workout records contain one set each. Older grouped records retain their original `sets` count. An unspecified RIR remains `null` and appears as **Bilinmiyor** (Unknown).

New sets support RIR values from 0 to 4. Progress calculations exclude warm-up sets. To find and edit past warm-up sets, enable **Isınma setlerini göster** (Show Warm-up Sets) in **Gelişim** (Progress).

Gimmer stores one body weight entry per day. Saving again updates today's measurement. The chart appears once measurements exist for at least two different dates. The seven most recent measurements are also listed as text.

JSON exports include `schemaVersion`, `weightUnit`, `sessions`, `bodyweights`, `nutrition`, `dailyConditions`, and `routine`. To save a copy, select **Ayarlar → Veri aktarımı → JSON dosyasını indir** (Settings → Data Export → Download JSON File) and choose a location in the Android file picker. Session entries include RIR, warm-up status, notes, muscle group, workout day, an ISO UTC timestamp, and epoch milliseconds. The backup reader also supports version 2 JSON. Backup imports have a 10 MiB limit.

## Rest timer and interface

Individual compound sets start a 180-second timer and isolation sets start a 90-second timer. After A2 in a paired set, the timer uses the longer rest duration in the pair. Manual presets are 90, 120, and 180 seconds.

The timer uses a deadline based on monotonic `elapsedRealtime`, so it persists across tab changes and screen rotation. `SavedStateHandle` supports restoration from Android's saved activity state. When time is up, the app displays an indicator and vibrates briefly on supported devices. The timer runs without a background service or an alarm, so it may miss the deadline if Android stops the app process.

Gimmer uses a Material 3 dark theme. You can choose Turkish or English independently for the interface and built-in exercise names. Form cues are available for the machine chest press, lat pulldown, and hack squat. The set and rep targets come from the sample routine in earlier versions.

## Verification

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

Run `./gradlew :app:connectedDebugAndroidTest` with a connected device or emulator to verify database migration, search and filtering, editing and deletion, and the SQLite backup round trip with duplicate detection. End-to-end Drive testing requires OAuth setup and user interaction.
