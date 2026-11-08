# Gimmer’a katkıda bulunma

**Türkçe** · [English](CONTRIBUTING.en.md)

Kurulum ve geliştirme ortamı için [README](README.md) dosyasına bak.

## Değişikliğe başlama

1. Hata bildirirken sorunu yeniden üretme adımlarını, beklenen ve gözlenen davranışı, Android sürümünü ve cihaz modelini yaz. Büyük değişikliklerin kapsamını bir GitHub kaydında tartış.
2. Güncel `main` dalından ayrı bir dal aç. Örneğin `fix/set-girisi`, `feat/program-aktarimi` veya `docs/kurulum`.
3. Her PR’ı tek bir konuya odakla. Kısa, değişikliği anlatan commit mesajları kullan.

## Kod ve doğrulama

- Kullanıcıya gösterilen metinleri Türkçe yaz. Kotlin’de sınıf, fonksiyon ve değişken adlarını İngilizce tut.
- Arayüz değişikliklerinde mevcut Material 3 bileşenlerini ve tema renklerini kullan. Cihazda dene ve ekran görüntüsünü PR’a ekle.
- Room şeması değişirse mevcut kayıtları koruyan bir geçiş ekle ve eski veritabanından yükseltmeyi test et. Yedek veya JSON biçimi değişirse önceki dosyaların okunabildiğini doğrula.
- Ekran görüntüleri ve test verileri için örnek kayıtlar kullan. Hesap bilgilerini, anahtarları, erişim belirteçlerini ve yerel ayar dosyalarını PR’a ekleme.

Kod değişikliklerinde derleme ve birim testlerini çalıştır.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Room geçişi, yedekleme veya cihaz davranışı değiştiğinde bağlı cihazda ya da emülatörde Android testlerini de çalıştır.

```bash
./gradlew :app:connectedDebugAndroidTest
```

Belge değişikliklerinde metni, biçimlendirmeyi ve bağlantıları kontrol et.

## PR açma ve inceleme

- Hedef dalı `main` olarak seç. PR açıklamasında değişikliğin nedenini ve sonucunu yaz. Bir GitHub kaydını kapatıyorsa `Fixes #123` biçiminde ilişkilendir.
- Çalıştırdığın testleri ve sonuçlarını belirt. Kapsam dahilinde olup çalıştıramadığın bir kontrol varsa nedenini yaz.
- İnceleme geri bildirimlerini ele al. İlgili kontroller tamamlandığında PR birleştirilebilir. [PR şablonu](.github/pull_request_template.md)
