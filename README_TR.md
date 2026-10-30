# tvOS Launcher (`v2.0.3`)

> 🇬🇧 **For English documentation and setup guide:** **[README.md (English)](README.md)**  
> 📦 **En Son APK İndir (`v2.0.3`):** **[GitHub Releases](https://github.com/techgenius1/tvOS-Launcher/releases/latest)**

![tvOS Launcher Afiş](screenshots_tr/Header_TR.jpg)

Android TV ve Google TV cihazlar için hiçbir üçüncü parti çalışma zamanı (runtime) kütüphanesi kullanılmadan **%100 saf Java** ile geliştirilmiş, hızlı ve ultra hafif **tvOS 26 & macOS** tasarımlı ana ekran başlatıcısı (**arka planda `%0.0` CPU kullanımı ve yalnızca ~30 MB özel RAM / ~38 MB toplam PSS tüketimi**). **1 GB RAM'li** uygun fiyatlı TV kutularında ve eski **Android 7.0 / 8.0** televizyonlarda bile 60 FPS akıcılık sunarken; **macOS Liquid Glass Dock**, dönüşümlü **Gece Sahneleri**, **Özel Duvar Kağıtları (`Özel 1..5`)**, **Uygulama Simgesi Değiştirici ve Simge Düzenleyici**, **Otomatik GitHub Uygulama Güncellemeleri**, **bilgisayar gerektirmeyen dahili CleanYourTV Debloat aracı (341 TV modeli)**, **Evrensel Denetim Paneli**, **Son Uygulamalar Değiştiricisi**, **çoklu basış (`1x / 2x / 3x`) Kumanda Tuş Atayıcı**, **Keep Alive otomatik izin onarımı** ve **tek tıkla Yerel ADB kurulumu** özelliklerini bir araya getirir.

---

## ✨ Öne Çıkan Özellikler

- **Çift Görünüm Modu (`Gece Sahneleri` & `Klasik`) + Özel Duvar Kağıtları**
  - **Gece Sahneleri (Night Scenes)**: Dağ ve manzara katmanlarının arkasında duran dev cam saat (*iPhone Duo*, *iPhone Rounded*, *Tall Glass*), derinlikli arka plan sahneleri (*Gece*, *Altın Saat*, *Su Çizgisi*, *Sis*, *Yağmur Ormanı Şafağı* ve **Sol / Orta / Sağ Saat Konumu** seçilebilen **5 adede kadar Özel Sahne Duvar Kağıdı**), kişisel karşılama mesajı, tarih, canlı hava durumu ve dinamik boyutlanan **macOS Liquid Glass Dock**.
  - **Uygulama Simgesi Değiştirici & Simge Düzenleyici (`JPG / PNG / ICO`)**:
    - Dock veya ızgara üzerindeki herhangi bir uygulamaya **OK** tuşuyla basılı tutup **Uygulama simgesini değiştir** seçeneğiyle dahili depolama veya USB bellekten `.jpg`, `.jpeg`, `.png` ya da `.ico` dosyası seçebilirsiniz.
    - Dahili **Uygulama Simgesi Düzenleyici** ile canlı önizleme, **14 arka plan rengi** (otomatik kenar rengi algılama dahil), **Yakınlaştırma / Uzaklaştırma** ve **Sol / Sağ / Yukarı / Aşağı** hizalama ayarlarını anında yapabilirsiniz.
  - **Uygulama Yeniden Adlandırma, Diğer Uygulamalar & Gizlenen Uygulamalar Ayrımı**:
    - Dock üzerindeki herhangi bir uygulamaya **OK** tuşuyla basılı tutarak **Yeniden Adlandır**, **Uygulama simgesini değiştir**, **Taşı**, **Diğer Uygulamalara Taşı**, **Gizle**, **Uygulama Bilgisi**, **Kaldır** veya **Düzeni Sıfırla** seçeneklerini kullanabilirsiniz.
    - **Diğer Uygulamalara Taşı**: Uygulamayı ilk dock satırından kaldırır ancak dock'taki **`...` (Diğer uygulamalar)** butonunun içinde tek tıkla erişilebilir tutar.
    - **Gizlenen Uygulamalar (`Ayarlar → Uygulamalar → Gizlenen uygulamalar`)**: Uygulamayı hem ana dock satırından hem de Diğer Uygulamalar menüsünden tamamen gizler. **Ayarlar → Uygulamalar → Gizlenen uygulamalar** menüsünden gizlenen bir uygulamanın üzerine **OK** ile basarak doğrudan açabilir veya **OK tuşuna basılı tutarak** ana ekrana geri yükleyebilirsiniz.
  - **Klasik (Classic)**: 6 sütunlu uygulama afiş ızgarası, ortalanmış cam dock, **Diğer Uygulamalar** klasör kutucuğu, yerleşik fotoğraflar, ortam rengi temaları veya kendi **Özel Duvar Kağıdınız** ve isteğe bağlı Üst Raf (Top Shelf).
- **Otomatik ve Manuel GitHub Güncellemeleri (`Ayarlar → Güncellemeler`)**
  - Hakkında menüsünün hemen üzerinde yer alan **Güncellemeler** menüsü ile mevcut sürümü ve GitHub üzerindeki en son sürümü görüntüleyebilir, tek tıkla güncelleme kontrolü yapabilir veya **Otomatik güncellemeler** seçeneğini açıp kapatabilirsiniz.
- **CleanYourTV (Beta) — Bilgisayar Gerektirmeyen Dahili Android TV Debloat Aracı (`Ayarlar → Gelişmiş`)**
  - **[CleanYourTV](https://github.com/Gathin23/cleanyourtv)** debloat profillerini **Yerel ADB (`127.0.0.1:5555`)** üzerinden doğrudan TV'nizde çalıştırır — **bilgisayar veya harici CLI gerektirmez**.
  - **341 Gömülü TV & Box Profili**: *TCL, Sony, Xiaomi, Hisense, Philips, Skyworth, OnePlus, Realme, Nvidia Shield, Motorola, Toshiba, Sharp, Onn, Chromecast with Google TV* ve daha fazlası.
  - **Akıllı Model Algılama & ADB Paket Benzerlik Tarayıcısı**: TV'nizin donanım özelliklerinden tam veya yakın modelleri otomatik bulur, 341 profil içinde arama yapmanızı sağlar veya TV'nizdeki sistem paketlerini ADB ile tarayarak en yakın üretici/sistem paketlerine sahip modelleri yüzdelik benzerlik oranıyla sıralar.
  - **Seçmeli Debloat Kontrol Listesi**: Telemetri, reklam satırları ve gereksiz ön yüklü uygulamalar ön seçili gelirken; **isteğe bağlı uygulamalar** (*Google Asistan, YouTube Music, Play Oyunlar, TalkBack, Chromecast alıcısı, Telefon Kumanda Servisi*) otomatik seçilmez ve listenin en altında yer alır.
  - **Google TV `setupwraith` Wi-Fi Koruması**: Google TV cihazlarda `setupwraith` / `launcherx` devre dışı bırakılmadan önce Wi-Fi ağını unutup yeniden eklemeniz konusunda uyarır (veya tek dokunuşla `setupwraith`'i güvenle atlamanızı sağlar).
  - **Tek Tıkla Yedekleme, Geri Yükleme & AdGuard DNS**: Devre dışı bırakılan paketleri kaydeder, canlı **Debloat Durumunu** (`Tamamlandı` / `Yapılmadı`) gösterir, istediğiniz zaman tek dokunuşla tüm uygulamaları geri yükler ve **Reklam Engelleyici Özel DNS (`dns.adguard-dns.com`)** anahtarı sunar.
- **Evrensel Cam Denetim Paneli (Control Panel Overlay)**
  - Ana ekrandan veya Tuş Atayıcı erişilebilirlik servisi sayesinde **çalışan herhangi bir uygulamanın üzerinde** gerçek zamanlı bulanık arka plan ile açılır.
  - **Wi-Fi**, **Bluetooth**, **RAM / Son Uygulamalar**, **Rahatsız Etmeyin**, **Ses Çıkışı**, **Uyku Zamanlayıcısı**, **Ortam Modu**, **Kapat / Ekranı Kapat**, **Girişler (HDMI)**, **Tuş Atayıcı**, **Ayarlar** ve **Arama** kısayollarını içerir.
  - **Evrensel Marka Etkinlik Seçici (`Ayarlar → Denetim Paneli`)**: **Wi-Fi**, **Bluetooth**, **Ses** veya **Girişler** kutucuklarına tıklandığında TV'nizdeki hangi sistem uygulamasının ve alt ekranın (Activity) açılacağını özgürce seçebilirsiniz.
- **Son Uygulamalar Değiştiricisi (Recent Apps Switcher) & RAM Temizleyici**
  - Atanmış kumanda tuşuyla veya Denetim Panelindeki **RAM** kutucuğundan her uygulamanın üzerinde açılır.
  - Canlı bellek kullanımını (`kullanılan / toplam RAM`), yatay uygulama kartlarını, **Yukarı (DPAD UP)** tuşuyla tekil uygulama kapatmayı (`force-stop`) ve **Tümünü Kapat** butonunu içerir; tüm uygulamalar kapatıldığında 1.5 saniye sonra otomatik olarak ana ekrana döner.
- **Çoklu Basış Kumanda Tuş Atayıcı (`1x` / `2x` / `3x`) & Ses Tuşu Düzeltmesi**
  - Seçtiğiniz herhangi bir kumanda tuşuna **Tek Basış (1x)**, **Çift Basış (2x)** ve **Üçlü Basış (3x)** için bağımsız görevler atamanızı sağlar.
  - **Ses Tuşu Basılı Tutma Düzeltmesi (Varsayılan Açık)**: Erişilebilirlik servisi etkinleştirildiğinde ses açma/kısma tuşuna basılı tutmanın çalışmaması sorununu otomatik olarak çözer.
- **Yerel ADB ile Kolay Kurulum & Keep Alive Otomatik İzin Onarımı**
  - **Kolay Kurulum (ADB ile Otomatik)** `127.0.0.1:5555` üzerinden bağlanarak Varsayılan Ana Ekran, Erişilebilirlik Servisi, Cihaz Yöneticisi, Bildirim Erişimi ve Kullanım İzinlerini tek tıkla verir.
  - **Keep Alive (Varsayılan Açık)**: Arka planda sıfır ek CPU/RAM yüküyle izinleri kontrol eder ve TV yazılımı Erişilebilirlik iznini kapatırsa Yerel ADB üzerinden otomatik olarak onarır.
- **13 Dil Desteği**
  - **Türkçe, İngilizce, İsveççe, Almanca, Fransızca, İspanyolca, İtalyanca, Rusça, Portekizce, Arapça, Hintçe, Japonca ve Çince** dillerinde tam yerelleştirilmiş arayüz.
- **Çoklu Kullanıcı Profilleri & Canlı Hava Durumu**
  - Her biri kendi uygulama sırasına, gizli uygulamalarına ve İzlemeye Devam Et listesine sahip 3'e kadar kullanıcı profili ve API anahtarı gerektirmeyen 5 günlük hava durumu tahmini.

---

## 📸 Ekran Görüntüleri

### Ana Ekran, Denetim Paneli ve Son Uygulamalar Değiştiricisi

| Gece Sahneleri Ana Ekranı & macOS Cam Dock | Cam Denetim Paneli (Control Panel) |
| :---: | :---: |
| ![Ana Ekran](screenshots_tr/Home.png) | ![Denetim Paneli](screenshots_tr/controlpanel.png) |

| Son Uygulamalar Değiştiricisi & RAM Temizleyici |
| :---: |
| ![Son Uygulamalar](screenshots_tr/Recent%20apps.png) |

---

### İlk Kurulum Sihirbazı

| 1. Hoş Geldiniz & Dil Seçimi | 2. Profil & Avatar Kurulumu |
| :---: | :---: |
| ![Hoş Geldiniz](screenshots_tr/Welcome.png) | ![Profil Kurulumu](screenshots_tr/User.png) |

| 3. Görünüm Modu (`Gece Sahneleri` / `Klasik`) | 4. Hava Durumu & Konum |
| :---: | :---: |
| ![Görünüm Kurulumu](screenshots_tr/Apperance.png) | ![Hava Durumu Kurulumu](screenshots_tr/Weather.png) |

| 5. Kolay Kurulum (Otomatik ADB) ve Manuel İzinler | 6. Kumanda Tuş Atayıcı Kurulumu (`1x / 2x / 3x`) |
| :---: | :---: |
| ![Kurulumu Tamamla](screenshots_tr/Setup.png) | ![Tuş Atayıcı Kurulumu](screenshots_tr/buttonmapper.png) |

---

### Ana Ekran Ayarları

| Görünüm & Sahneler | Evrensel Denetim Paneli Eylemleri |
| :---: | :---: |
| ![Ayarlar - Görünüm ve Sahneler](screenshots_tr/Setting1.png) | ![Ayarlar - Denetim Paneli](screenshots_tr/Setting2.png) |

| Tuş Atayıcı (`1x / 2x / 3x Basış`) | Hakkında, ADB Durumu & İzin Kontrolü |
| :---: | :---: |
| ![Ayarlar - Tuş Atayıcı](screenshots_tr/Setting3.png) | ![Ayarlar - Hakkında ve ADB](screenshots_tr/Setting4.png) |

---

## 🚀 Hızlı Başlangıç (Derleme & Kurulum)

### Gereksinimler
- **JDK 17+** (`javac`, `jar`)
- **Android SDK** (`build-tools;34.0.0` ve `platforms;android-34`)

### Windows'ta Derleme (PowerShell)
```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
# Çıktı APK -> build\tvos-home.apk
```

### macOS / Linux'ta Derleme
```sh
./build.sh
# Çıktı APK -> build/tvos-home.apk
```

### ADB ile Kurulum
```sh
adb connect <tv-ip>:5555
adb install -r build/tvos-home.apk
adb shell am start -n com.techgenius.tvoslauncher/.MainActivity
```

---

## 📋 Sistem Gereksinimleri

- **Android TV / Google TV 7.0+ (API 24+)**

---

## 🙌 Teşekkürler & Katkıda Bulunan Projeler

**tvOS Launcher** projesine ilham veren ve katkıda bulunan açık kaynak projelere teşekkürler:

- **[Hume Launcher](https://github.com/petrobot-admin/hume-launcher)** ([@petrobot-admin](https://github.com/petrobot-admin)) — Sıfır bağımlılıklı Android TV Java mimarisi, Klasik ana ekran düzeni ve sistem entegrasyonları için temel yapı.
- **[Couch TV](https://github.com/Seagey/couch-tv)** ([@Seagey](https://github.com/Seagey)) — Gece Sahneleri derinlikli duvar kağıtları, dev cam saat ve cam efektli Ayarlar arayüzü tasarımı.
- **[CleanYourTV](https://github.com/Gathin23/cleanyourtv)** ([@Gathin23](https://github.com/Gathin23)) — Dahili CleanYourTV (Beta) aracına güç veren topluluk tarafından hazırlanmış Android TV / Google TV debloat profilleri (`examples/`).
- **Inter Yazı Tipi** (SIL Open Font License 1.1) & **Project Nayuki QR Kod Üretici** (MIT Lisansı).
- Hava durumu verileri: **Open-Meteo**, **MET Norway** ve **wttr.in**.

---

## 📄 Lisans

**Apache License 2.0** kapsamında lisanslanmıştır — ayrıntılar için [LICENSE](LICENSE) ve [NOTICE](NOTICE) dosyalarına göz atabilirsiniz.
