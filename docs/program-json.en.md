# Workout routine JSON

[Türkçe](program-json.md) · **English**

Open **Ayarlar → Antrenman programı → Program JSON’unu kopyala** (Settings → Workout Routine → Copy Routine JSON) to copy your active routine to the clipboard. Paste the edited text into **Program JSON’u içe aktar** (Import Routine JSON), preview the training days, and confirm with **Programı değiştir** (Replace Routine).

```json
{
  "routineVersion": 1,
  "days": [
    {
      "id": "tuesday",
      "name": "Salı",
      "programName": "Alt Vücut",
      "exercises": [
        {
          "id": "romanian_deadlift",
          "name": "Rumen yerden çekişi",
          "targetSets": 3,
          "targetReps": "8-12",
          "muscleGroup": "Bacak",
          "isCompound": true,
          "isBarbell": true
        }
      ]
    }
  ]
}
```

The app displays days and exercises in the order given in the JSON. Keep the same exercise ID when you add the exercise to another day. Set and rep targets can vary by day. Use a string such as `"8"` or `"8-12"` for a rep target, or `"30-45 sn"` for a timed exercise.

- Add the optional `pairs` field to a day object, for example `[["machine_chest_press", "lat_pulldown"]]`. The first ID is A1 and the second is A2. Both exercises must be on that day, and each exercise can be in only one pair.
- `isCompound` controls the automatic rest timer. Compound exercises use 180 seconds and isolation exercises use 90 seconds. After A2, the timer uses the longer rest duration in the pair.
- `isBarbell` enables the plate calculator for a standard 20 kg bar.
- Import replaces the active routine and preserves workout, measurement, and meal records. Keep existing exercise IDs to retain their links to set history.
- Full JSON exports and Drive backups include the custom routine and daily sleep records. You can import the `routine` object from a full JSON export.
