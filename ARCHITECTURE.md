# Pomemo — Architecture

Pomodoro timer + recurring reminders that ring like alarms **without** touching the system Clock app,
plus small markdown notes. Single module, MVVM, Jetpack Compose, Hilt, Room, DataStore.

## Lessons from the reference apps

| | Tomato (timer) | Med (alarms) | Pomemo takes |
|---|---|---|---|
| DI | Manual `AppContainer` on the Application | None (`object`s, `getSharedPreferences` everywhere) | Hilt — receiver, activity and VMs all need the same DB/scheduler |
| Storage | Room (stats + prefs) | Whole JSON file reloaded in every receiver call | Room for reminders/notes, DataStore for settings + timer state |
| Timer | Foreground service (`specialUse`) with a coroutine loop ticking 60×/s; completion fires only when the loop notices `time < 0` | — | No service. Store `endAt`, exact alarm at `endAt`, notification countdown chronometer |
| Alarm trigger | Service loop → `MediaPlayer` + in-app `AlarmDialog`; screen-on via a lambda the Activity injects into the container (only works while MainActivity exists) | `AlarmManager.setExactAndAllowWhileIdle` → `NotificationReceiver` → notification with `setFullScreenIntent` + `FLAG_INSISTENT` channel sound → `AlarmActivity` | Med's pipeline, for both reminders and Pomodoro phase ends |
| Full screen | Compose `Dialog` inside MainActivity | Separate `AlarmActivity` (`showWhenLocked`, `turnScreenOn`, `singleTask`), Compose `AlarmScreen` with swipe slider or two buttons, closed by a broadcast | Separate `AlarmActivity`, closes by observing a `StateFlow` |
| Recurrence | — | `recurrenceDays: List<DayOfWeek>`, `intervalGap`, `endDate`; next occurrence = loop 30 days | `days` bitmask, `nextTrigger()` pure function + unit test |
| Reboot | — | `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` → reschedule all | Same |

Notes from Med: `AlarmService` (looping MediaPlayer) is never started — only `stopService` is called — so the
sound actually comes from the insistent notification channel. Pomemo will not copy the dead service.

## Packages

```
com.serranoie.app.pomemo
├─ PomemoApp.kt            @HiltAndroidApp, creates notification channels
├─ MainActivity.kt         @AndroidEntryPoint, bottom NavigationBar: Timer | Reminders | Notes (flat tabs, editors are sheets → no nav library)
├─ di/AppModule.kt         Room DB, DAOs, DataStore, AlarmManager, NotificationManager
├─ data/
│  ├─ AppDatabase.kt       Room: Reminder, Note (+ LocalTime/Instant converters)
│  ├─ Reminder.kt          entity + ReminderDao
│  ├─ Note.kt              entity + NoteDao
│  └─ Settings.kt          DataStore: pomodoro lengths, ring style, snooze minutes, timer state
├─ alarm/
│  ├─ AlarmScheduler.kt    schedule/cancel via AlarmManager (one PendingIntent per reminder id, one for the timer)
│  ├─ AlarmReceiver.kt     @AndroidEntryPoint BroadcastReceiver: FIRE, SNOOZE, DISMISS, BOOT
│  ├─ Ringer.kt            builds the ringing notification (full-screen intent or plain), holds `ringing: StateFlow`
│  └─ AlarmActivity.kt     lock-screen activity + AlarmScreen composable
├─ timer/
│  ├─ PomodoroTimer.kt     @Singleton state machine: start/pause/skip/reset, persisted in DataStore
│  ├─ TimerViewModel.kt
│  └─ TimerScreen.kt
├─ reminders/
│  ├─ ReminderParser.kt    "stretch every weekday at 10:30" → draft (title, time, days)
│  ├─ Recurrence.kt        nextTrigger(reminder, now): LocalDateTime?
│  ├─ RemindersViewModel.kt
│  └─ RemindersScreen.kt   list + composer sheet
└─ notes/
   ├─ NotesViewModel.kt
   └─ NotesScreen.kt       list + editor (edit/preview toggle)
```

No repository interfaces: ViewModels inject DAOs/`PomodoroTimer`/`AlarmScheduler` directly. A use case only
appears if two callers need the same multi-step logic (e.g. "save reminder + schedule" lives in one
`RemindersViewModel.save()` and the receiver calls `AlarmScheduler` itself).

## Data

```kotlin
@Entity data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",          // markdown, shown on the alarm screen
    val time: LocalTime,
    val days: Int = 0,              // bitmask Mon=1..Sun=64; 0 = one-shot on `date`
    val date: LocalDate? = null,    // one-shot date (or start date for recurring)
    val ring: Boolean = true,       // true = full-screen alarm, false = plain notification
    val enabled: Boolean = true,
)

@Entity data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val body: String,               // markdown; title = first line
    val updatedAt: Instant,
)
```

Timer state (DataStore, survives process death): `phase` (FOCUS/SHORT/LONG), `cycle`, `endAt: Long?`
(wall-clock millis, null = paused), `remainingMs` (when paused), `running`.

## Flows

**Pomodoro**
1. Start → `endAt = now + phaseLength`, persist, `AlarmScheduler.setTimer(endAt)`, post ongoing notification
   with `setUsesChronometer(true).setChronometerCountDown(true).setWhen(endAt)` — the system counts down,
   no ticking code.
   On Android 16+ the notification is a promoted Live Update (`setRequestPromotedOngoing`, permission
   `POST_PROMOTED_NOTIFICATIONS`) with a `ProgressStyle` bar segmented per phase of the whole set (Tomato's
   design). The status-bar chip is the chronometer, so it ticks by itself; only the bar needs reposting,
   done by a once-a-minute exact alarm (`ACTION_TIMER_TICK`) while running — still no service.
2. UI: `TimerViewModel` derives remaining from `endAt` with a `while(isActive) { delay(…) }` that only runs while
   the screen is collected (`WhileSubscribed`).
3. Pause → store `remainingMs`, cancel alarm. Resume → new `endAt`.
4. Alarm fires → `AlarmReceiver` → advance phase (paused) → `Ringer.ring(...)`; the ring screen and notification offer "Start next" instead of Snooze. No auto-start yet.

**Reminder**
1. Save → Room insert → `AlarmScheduler.set(id, nextTrigger(reminder, now))`.
2. Fire → `AlarmReceiver` loads the one reminder by id → `Ringer.ring` → schedules the next occurrence
   (or disables a one-shot).
3. Snooze → one-off alarm `now + snooze` under a separate request code. Dismiss → cancel notification.
4. `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / `TIME_SET` / `TIMEZONE_CHANGED` → reschedule all enabled reminders,
   reset timer (its `endAt` is meaningless after reboot).

**Ringing (shared by both)**
- Channel `ringing`: IMPORTANCE_HIGH, **silent**. Verified on Android 15: a channel's `USAGE_ALARM` sound
  is rewritten to `USAGE_NOTIFICATION`, so silent mode would mute it (Med has this bug).
- `RingService` (foreground, `mediaPlayback`) plays the alarm ringtone via `MediaPlayer` with `USAGE_ALARM`
  and vibrates while `Ringer.ringing` is set; its notification is the ringing notification.
- Notification: `CATEGORY_ALARM`, ongoing, actions Snooze/Dismiss, `setFullScreenIntent(AlarmActivity)`.
- `AlarmActivity`: `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, `FLAG_KEEP_SCREEN_ON`, `singleTask`,
  `excludeFromRecents`. Renders title + markdown note + swipe slider (Med's `AlarmSlider`) or buttons.
  Finishes when `Ringer.ringing` goes null — no close broadcast.
- Auto-stop after 10 minutes (`RingService` timeout). Missed-reminder notification: later.

## Alarm APIs — why not Clock alarms

- `setExactAndAllowWhileIdle(RTC_WAKEUP, …)`, **not** `setAlarmClock` — `setAlarmClock` puts the reminder into
  the system "next alarm" slot (status bar icon, lock screen), mixing it with the sleep alarm you want to keep
  alone.
- Permissions: `USE_EXACT_ALARM` (API 33+, auto-granted, valid for reminder/alarm apps) +
  `SCHEDULE_EXACT_ALARM` with `maxSdkVersion="32"`; `POST_NOTIFICATIONS`; `USE_FULL_SCREEN_INTENT`
  (Android 14+ may revoke it → check `canUseFullScreenIntent()` and deep-link
  `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`, as Med's onboarding does); `RECEIVE_BOOT_COMPLETED`; `VIBRATE`.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` for `RingService` only (it lives only while ringing).
  The timer itself still needs no service. No `WAKE_LOCK`.

## Natural reminder creation

One text field at the top of the Reminders screen; chips under it show what was understood and are tappable to
correct (time picker, day toggles). Save is enabled once a time is found.

`ReminderParser.parse(text, now): Draft(title, time?, days, date?)` — hand-rolled, no NLP library:

| Input | Parsed |
|---|---|
| `stretch every weekday at 10:30` | title "stretch", 10:30, Mon–Fri |
| `water plants mon wed fri 8pm` | 20:00, Mon/Wed/Fri |
| `take out trash every tuesday night` | 21:00 (night = 21:00 default), Tue |
| `call mom tomorrow at 6` | one-shot, tomorrow 18:00 (bare hour < 7 → pm) |
| `drink water daily 9am` | 09:00, every day |
| `pay rent on weekends at noon` | 12:00, Sat/Sun |

English and Spanish are parsed together, whatever the phone language ("regar las plantas lun mié vie 8pm",
"llamar a mamá mañana a las 6 de la tarde", "a las 7 y media de la mañana", "los lunes" = recurring, "el viernes" =
next Friday, bare "mañana" = tomorrow). Accents optional. `\b` is rewritten to a Unicode letter boundary because
the JVM's is ASCII-only. Covered by `ReminderParserTest` and `SpanishReminderParserTest`.

## Notes

Third tab. `Note` (Room, DB v2 via hand-written `MIGRATION_1_2`), `NotesViewModel`, `NotesScreen` (cards, newest
first) and a full-screen `NoteEditor` dialog. No markdown library: `notes/Markdown.kt` highlights in place (syntax
stays visible, styled: sized headings, bold/italic/strike, code, quotes, lists) — the same function styles the editor
(as a `VisualTransformation`), the cards and reminder notes on the alarm screen. The editor toolbar is a scrollable
row of toggles (inline: wrap/unwrap selection; line: swap prefix) plus a Save FAB. Back, ✕ and Save all save; only
Delete discards. Tests: `MarkdownEditingTest`, `MarkdownHighlightTest`.

## Dependencies to add

Hilt (+ `hilt-lifecycle-viewmodel-compose`), KSP, Room (runtime/ktx/compiler), DataStore Preferences,
Material3 pinned to `1.5.0-beta01` (+ `material3-adaptive-navigation-suite`) because the Expressive components
(wavy progress, toggle/connected buttons, size tokens, flexible app bars) are not in stable 1.4. Move back to the
BOM version once 1.5 is stable
(runtime + ui), lifecycle-viewmodel-compose, markdown renderer. Also bump the stale template versions
(`core-ktx 1.10.1`, `lifecycle 2.6.1`, `activity-compose 1.8.0`).

## Deliberately skipped

Stats/graphs (Tomato), DND during focus (Tomato), Wear OS sync, backup/export, widgets. Each is additive
later; none changes the structure above.

## Build order

1. Gradle deps + Hilt app/activity + Room/DataStore. ✅ (Navigation 3 dropped: tabs are a `rememberSaveable` int.)
2. `alarm/` pipeline end-to-end with a hardcoded test reminder (prove lock-screen full-screen ringing first —
   it's the riskiest part).
3. Reminders: entity, `nextTrigger` + test, parser + test, composer UI.
4. Pomodoro timer on top of the same scheduler/ringer.
5. Notes.
