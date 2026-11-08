<p align="center">
  <img src="docs/media/gimmer-icon.svg" width="88" height="88" alt="Gimmer dambıl simgesi">
</p>

<h1 align="center">Gimmer</h1>

<p align="center">
  Android için antrenman ve beslenme günlüğü.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-9%2B-183054?style=flat-square" alt="Android 9 ve üzeri">
  <img src="https://img.shields.io/badge/Kotlin-Android-273448?style=flat-square" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack-Compose-315D9B?style=flat-square" alt="Jetpack Compose">
</p>

Gimmer’da programındaki setleri ve öğünlerini kaydedebilir, hareket bazında ağırlık ve tekrar değişimini takip edebilirsin. Kayıtlar cihazda saklanır. İstersen Google Drive’da düzenli yedek tutabilir veya geçmişini JSON dosyası olarak indirebilirsin.

## Ekranlar

<p align="center">
  <img src="docs/media/antrenman.png" width="230" alt="Antrenman günü ve programdaki hareketler">
  <img src="docs/media/beslenme.png" width="230" alt="Beslenme günlüğü ve günlük kalori ile makro hedefleri">
  <img src="docs/media/gelisim.png" width="230" alt="Harekete özel ağırlık ve tekrar grafiği">
</p>

<p align="center"><sub>Antrenman · Beslenme · Hareket gelişimi</sub></p>

## Özellikler

| Bölüm | Neler yapabilirsin? |
| --- | --- |
| **Antrenman** | Programa göre hareket seç. Ağırlık, tekrar, RIR (yedekte kalan tekrar) ve not gir. Isınma ve çalışma setlerini ayrı kaydet. |
| **Zamanlayıcı** | Set kaydında otomatik başlayan dinlenme sayacını elle de çalıştır. 90, 120 ve 180 saniye arasından seçim yap. |
| **Beslenme** | Öğün ve porsiyon kaydet. Kalori, protein, karbonhidrat ve yağ hedeflerini takip et. Ürün adı ya da barkodla ara veya besini elle gir. |
| **Gelişim** | Aylık takvimden antrenman günlerini incele. Vücut ağırlığını kaydet ve her hareketin ağırlık ile tekrar grafiğine bak. |
| **Yedekleme** | Drive’a elle veya seçtiğin aralıkta yedekle, kayıtlı bir sürümü geri yükle veya JSON dosyasını istediğin klasöre kaydet. |

## Kurulum

Android Studio’da projeyi açıp **Android SDK 36.1** ve **JDK 21** ile Gradle senkronizasyonunu tamamla. Uygulamayı Android 9 veya üzeri bir cihazda ya da emülatörde çalıştırabilirsin.

```bash
git clone https://github.com/mahmutconger/Gimmer.git
cd Gimmer
./gradlew :app:assembleDebug
```

Debug APK, `app/build/outputs/apk/debug/app-debug.apk` konumunda oluşturulur.

### Google Drive

Drive yedeklemesi için Google Play Hizmetleri, etkinleştirilmiş Drive API ve APK’nın imzasıyla eşleşen bir Android OAuth istemcisi gerekir. Yapılandırma adımları için [Drive kurulum rehberine](PREMIUM_SETUP.md) bak.

### Testler

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

Cihaz testleri için çalışan bir emülatör veya bağlı Android cihaz gerekir.

## Altyapı

Arayüz **Kotlin**, **Jetpack Compose** ve **Material 3** ile geliştirildi. **Room** yerel kayıtları, **WorkManager** otomatik yedeklemeyi, **Vico** grafikleri yönetir. Besin aramasında [Open Food Facts](https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/) verileri kullanılır.
