# Gimmer kurulum ve veri rehberi

## Google Drive kurulumu

1. Google Cloud projesinde Google Drive API hizmetini etkinleştirin.
2. OAuth izin ekranını yapılandırın; test modunda kullanılacak hesapları test kullanıcılarına ekleyin.
3. Android OAuth istemcisi oluşturun: paket adı `com.mcclabs.gimmer`; geliştirme ve yayın imzalarının SHA-1 parmak izlerini kaydedin. Parmak izleri için `./gradlew :app:signingReport` kullanın.
4. Google Play Hizmetleri bulunan bir cihazda Ayarlar ekranından yedeklemeyi deneyin.

Google Identity AuthorizationClient hesap seçimi ve izin akışını yürütür.
İstenen tek kapsam: `https://www.googleapis.com/auth/drive.appdata`.
İstemci paket adı ve imzayla eşleşir; istemci sırrı veya erişim anahtarı kaynak koda eklenmez.
Token bellekte tutulur.

Drive REST API v3, `appDataFolder` içinde her kurulum için ayrı SQLite yedekleri oluşturur.
Boş veritabanları atlanır. Aynı içerik için yeni sürüm oluşturulmaz. Yeni kopya doğrulandıktan
sonra o kurulumun son beş sürümü saklanır. Önceki `gimmer-v3.db` ve
`gimmer-backup.json` yedekleri geri yükleme listesinde seçilebilir.
Kayıtlar Room'dan aynı işlem içinde okunur, geçici ayrı bir Room veritabanına yazılır.
Bu veritabanı TRUNCATE günlük modunda kapatıldıktan sonra yüklenir; açık WAL dosyası kopyalanmaz.
Geçici dosyalar işlem sonunda temizlenir.

Geri yükleme SQLite başlığını, boyutu, bütünlüğü, Room şemasını ve kayıt değerlerini doğrular.
Mevcut veriler silinmeden tek Room işlemi içinde birleştirilir. Aynı set içerikleri tekrar eklenmez.
Aynı tarihte vücut ağırlığı çakışmasında yerel değer korunur.
Yerelde düzenlenmiş bir setin eski yedek sürümü ayrı kayıt olarak eklenebilir.
Ayarlar → Bulut yedeği altında otomatik yedekleme açılabilir. Hesabı seçip izin verdikten
sonra Android, internet bağlantısı uygun olduğunda işi seçilen aralıkta çalıştırır.
Aralıklar 1 saat, 6 saat, 24 saat ve 7 gündür. Google yeniden onay istediğinde
Ayarlar'daki **İzni yenile** düğmesini kullanın.

## Room ve set kayıtları

Room şema geçişleri mevcut kayıtları korur. Şema 3, `isWarmup`, `rir` ve
`bodyweight_logs` tablosunu, şema 4 ise beslenme kayıtlarını ekler. Yeni antrenman kayıtları tek set içerir.
Eski toplu kayıtların `sets` sayısı korunur; girilmemiş RIR değerleri null/Bilinmiyor olarak kalır.
Yeni setlerde RIR 0–4 arasında seçilir. Isınma setleri gelişim hesabından çıkarılır;
Gelişim ekranındaki “Isınma setlerini göster” seçeneğiyle geçmişteki ısınmalar da düzenlenebilir.

Vücut ağırlığı günde tek ölçümdür; tekrar kaydetmek bugünkü ölçümü günceller.
Vücut ağırlığı grafiği en az iki farklı günün ölçümüyle görünür. Son yedi ölçüm metin olarak da sunulur.

JSON dışa aktarma schemaVersion, weightUnit, sessions, bodyweights ve nutrition alanlarını içerir.
Ayarlar → Veri aktarımı → **JSON dosyasını indir** ile Android dosya seçicisinden kayıt konumu seçilebilir.
Seanslarda RIR, ısınma bilgisi, not, kas bölgesi, gün, ISO UTC tarih ve epoch milisaniye bulunur.
V2 JSON belgeleri de okunabilir. İçe aktarma 10 MB ile sınırlıdır.

## Sayaç ve arayüz

Başarılı bir set kaydından sonra 90/120/180 saniyelik dinlenme sayacı başlar.
Yeni set sayacı yeniden başlatır; aktifken süre seçmek yeni süreyle yeniden başlatır.
Sayaç monotonic elapsedRealtime son zamanını kullanır, sekme değişiminde ve döndürmede korunur.
SavedStateHandle, Android'in kaydettiği etkinlik durumundan geri dönüşü destekler.
Süre dolunca görsel bildirim ve destekleyen cihazda kısa titreşim oluşur.
Arka plan servisi/alarm kullanılmaz; uygulama öldürülmüşken tam zamanında arka plan alarmı garanti edilmez.

Uygulama Türkçe arayüz, Material 3 koyu tema ve İngilizce hareket adları kullanır.
Machine Chest Press, Lat Pulldown ve Hack Squat için Türkçe form adımları bulunur.
Set/tekrar hedefleri önceki sürümdeki örnek program değerleridir.

## Doğrulama

`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest`

Bağlı cihazda `./gradlew :app:connectedDebugAndroidTest`:
eski veritabanı geçişi, arama/filtreleme, güncelleme/silme, SQLite yedek
döngüsü ve yinelenen kayıtların atlanması test edilir.
Drive uçtan uca testi OAuth yapılandırması ve kullanıcı etkileşimi gerektirir.
