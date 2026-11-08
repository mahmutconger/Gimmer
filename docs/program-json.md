# Antrenman programı JSON’u

**Ayarlar → Antrenman programı → Program JSON’unu kopyala** seçeneği etkin programı panoya aktarır. Düzenlenen metni **Program JSON’u içe aktar** ekranına yapıştır, günleri önizle ve **Programı değiştir** düğmesiyle onayla.

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

Gün sırası ve hareket sırası JSON’daki sırayı izler. Aynı hareketi başka bir güne eklerken kimliğini koru. Set ve tekrar hedefleri güne göre değişebilir. Hedefi `"8"`, `"8-12"` veya süreli hareketler için `"30-45 sn"` biçiminde yaz.

- `isCompound` otomatik dinlenme süresini belirler. Bileşik hareketler 180, izole hareketler 90 saniye kullanır.
- `isBarbell` standart 20 kg bar için plaka hesaplayıcıyı açar.
- İçe aktarma yalnızca etkin programı değiştirir. Antrenman, ölçüm ve öğün kayıtları korunur. Düzenlenen hareketlerin geçmişini bağlamak için mevcut hareket kimliklerini kullan.
- Özel program ve günlük uyku kayıtları tam JSON dışa aktarımına ve Drive yedeğine dahildir. Tam JSON’daki `routine` nesnesi program içe aktarmada kullanılabilir.
