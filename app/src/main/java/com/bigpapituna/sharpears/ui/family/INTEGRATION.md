# Family foundation (2.1, stage 1): what other teams and the integrator need

Profiles, per-profile storage, the parent gate, the picker and the family wall. Everything here is wired already;
this page says how to use it. ui/family/*, engine/family/* and data/Profiles.kt are shared files: read them, do not
edit them in stage 2 (ask the integrator).

## Who is playing

```kotlin
import com.bigpapituna.sharpears.ui.family.*

val p: Profile = LocalProfile.current      // or profile()
if (isKid()) ...                           // the kid skin is on
reader()                                   // false only for a kid who cannot read yet (animals, not letters)
hintSeconds()                              // [4, 8, 12] for kids, [6, 12, 18] for grown-ups
colourNotes()                              // Boomwhacker colours on (kids by default)
p.micOffsetDb                              // +3.0 for kids: make the mic this much easier
p.firstUnitId                              // "playground" for kids, "basics" for grown-ups (see below)
```

All of these are @Composable reads of `LocalProfile`, which MainActivity provides from `Profiles.currentFlow`, so a
rename or "switch to grown-up mode" reaches every screen without a restart. Outside composition use
`LocalProfiles.current?.current` (the store) or pass the profile in.

`Profile` (engine/family/Profile.kt, Android-free):
`Profile(id, name, avatar 0..7, kind ADULT|KID, reader, dailyGoalMin, hintSeconds: List<Int> (3), colourNotes,
micOffsetDb: Double, createdAt)`, `isKid`, `isOwner`, `firstUnitId`, `withKind(kind)`, factories
`Profile.kid(name, avatar, reader)` and `Profile.adult(name, avatar)`, `encode()`/`Profile.decode(text)`.
The owner is `Profile.OWNER_ID = "owner"` (named "Roy"); the owner cannot be deleted and stays a grown-up.

`dailyGoalMin` seeds `ProgressStore.goalMinutes` the first time a kid profile opens; after that the live goal is
`ProgressStore.goalMinutes` (the You tab can change it). A kid profile skips the onboarding.

## The firstUnitId contract (playground team, integrator)

`Profile.firstUnitId` is just a string: `"playground"` for kids, `"basics"` for grown-ups. The playground team
adds a `CourseUnit` with id `"playground"` to engine/Course.kt; the integrator orders the Learn path by it and opens
its first lesson for a new kid (MainActivity opens `Screen.Home()` for a new kid today; see the TODO below).

## Per-profile storage

Every file in `ProgressFiles.NAMES` except `"mic"` is per profile: the owner keeps the plain name (`"progress"`,
so everything from before 2.1 is the owner's), everyone else gets `"<name>.<profileId>"` (`"progress.p3"`).

```kotlin
ProgressFiles.fileFor("progress", profileId)   // "progress" for the owner, "progress.p3" otherwise; "mic" is shared
```

Stores take the id explicitly: `Prefs(context, profileId)`, `LessonStore(context, profileId)`,
`PracticeStore(context, profileId)`, `EarStore(context, profileId)`, `TheoryStore(context, profileId)`;
`ProgressStore(context, prefs)` follows its `prefs.profileId`. MainActivity builds them in `openApp()` for the
current profile; a switch rebuilds the whole app for the new one (`key(profile.id)`), so screens need nothing.

A team's own new store (WorldStore, a play store, ...) that is per person should do the same: take a `profileId`,
open `ProgressFiles.fileFor("<file>", profileId)`, and ask the integrator to add `"<file>"` to
`ProgressFiles.NAMES` so backup, import and profile deletion cover it. Phone-wide things (the mic, the report
token, imported songs, run recordings in files/runs) stay shared.

Backup: `ProgressFiles.export` writes every profile's files plus the roster (`"profiles"`); `replace` restores
them all. An old 2.0 backup (no roster) restores the owner's files only and leaves the other profiles alone.
Deleting a profile deletes its files (`Profiles.delete`).

## The store: data/Profiles.kt

```kotlin
val profiles = LocalProfiles.current!!       // provided by MainActivity
profiles.current: Profile                    // never null
profiles.currentFlow: StateFlow<Profile>
profiles.rosterFlow: StateFlow<Roster>       // everyone, owner first; roster.needsPicker when 2+
profiles.all: List<Profile>
profiles.create(profile, switch = true): Profile?   // null when 8 already or the name is blank
profiles.rename(id, name); profiles.update(id) { it.copy(...) }; profiles.setKind(id, ProfileKind.ADULT)
profiles.switchTo(id)                        // the app reopens as them (MainActivity collects currentFlow)
profiles.delete(id): Boolean                 // not the owner; deleting the current one switches to the owner
```

The rules live in engine/family/Roster.kt (JVM-tested): max 8, ids "p1", "p2"..., owner first and undeletable.

## The parent gate

Hold the pup for three seconds, then a sum of two numbers from 2 to 9 on a number pad; a wrong answer gives a new
sum. Once passed it stays open for ten minutes (the time is kept in the "profiles" file), and it closes when the app
opens as a kid.

```kotlin
val gate = LocalParentGate.current
gate.guard { ... }          // always asks: profile management, the report token, "switch to grown-up mode"
gate.guardForKids { ... }   // asks only while a kid plays: Settings, Microphone setup
gate.isOpen
Gated(onCancel = { ... }) { SomeGrownUpScreen() }   // a whole screen behind the gate
ParentGate(onPass, onCancel)                        // the gate screen itself
```

`ParentGateHost(gate)` sits last in MainActivity's root and draws the gate while an action waits. Already wired:
Settings (gear on You), Microphone setup (Songs header, Settings, Privacy), the report token's Set up and Remove in
Microphone setup, and everything in Settings > Family (add, rename, reader, grown-up or kid mode, delete).
The integrator's "grown-up mode" prompt after Beginner should call
`gate.guard { profiles.setKind(id, ProfileKind.ADULT) }`.

## The family wall (world team, integrator)

`FamilyWall(entries, onSwitch)` is on the Songs tab under the pup when there are two or more profiles. Entries come
from `familyWallEntries(context, roster, today, latestSticker = { profileId -> drawableRes or null })`; each
`FamilyWallEntry(profile, week: List<PawDay>, stickerRes: Int?, current)`. The world team fills the sticker slot
by passing `latestSticker` (MainActivity's call in the SONGS tab is the one place to change). Paws come from
`PawWeek.of(practiceLog, today)` (Monday to Sunday, any practice fills a paw); `Paw(day, size)` and
`DrawScope.drawPaw(colour, filled)` are public for the skin team's paw calendar.

## Avatars

`PupAvatar(avatar, size, selected)` draws `res/drawable-nodpi/pup_avatar_<n>.webp` (pup_wear_base with the
sweatband tinted by scripts/tint_avatars.py) on a disc of `avatarColour(n)`. `avatarLabel(n)` is the TalkBack name
("the blue pup"). Real avatar art can replace the eight webp files one for one.

## Events and logs

No ProgressEvent is added. Logcat (tag SharpEars) has `profiles: ...` for every roster change and every open
(`profiles: open as p1 (KID, reader=false)`), and `gate: asked|held for three seconds|wrong answer, new sum|passed|left`.

## Decisions to know

- Settings in a kid profile: the whole screen is behind the gate. Its Family section always is.
- The daily reminder is the phone owner's (one alarm per phone, `Reminder.schedule(context)` reads the owner's
  prefs). Settings shows the Reminder section only in the owner's profile; onboarding for another grown-up skips
  the reminder question (`OnboardingScreen(askReminder = false)`).
- Prefs ("progress") also holds settings like voice, key labels and sound, so those are per profile too; a new
  profile starts with the defaults.
- Run recordings (files/runs, "Hear my run") are not per profile: the latest run of a song is shown whoever played.
- Debug: `--es profile <id>` opens as that profile without the picker (combine with any other route).

## TODO for the integrator

- A new kid lands on Home today; open the first Playground lesson (`firstUnitId`) once the unit exists.
- Kid copy on the home greeting and the goal line are the skin team's.
