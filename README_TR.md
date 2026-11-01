# tvOS Launcher (`v2.0.6`)

> 🇬🇧 **For English documentation and setup guide:** **[README.md (English)](README.md)**  
> 📦 **En Son APK İndir (`v2.0.6`):** **[GitHub Releases](https://github.com/techgenius1/tvOS-Launcher/releases/latest)**

![tvOS Launcher Afiş](screenshots_tr/Header_TR.jpg)

Android TV ve Google TV cihazlar için hiçbir üçüncü parti çalışma zamanı (runtime) kütüphanesi kullanılmadan **%100 saf Java** ile geliştirilmiş, hızlı ve ultra hafif **tvOS 26 & macOS** tasarımlı ana ekran başlatıcısı (**arka planda `%0.0` CPU kullanımı ve yalnızca ~30 MB özel RAM / ~38 MB toplam PSS tüketimi**). **1 GB RAM'li** uygun fiyatlı TV kutularında ve eski **Android 7.0 / 8.0** televizyonlardan **Android 14** modellere kadar 60 FPS akıcılık sunarken; **boyutlandırılabilir macOS Liquid Glass Dock (`%50–%150`)**, dönüşümlü **Gece Sahneleri**, **Özel Duvar Kağıtları (`Özel 1..5`) + Klasörden Karışık Duvar Kağıdı**, **Dahili Kilit Ekranı / Ekran Koruyucu**, **Uygulama Simgesi Değiştirici ve Simge Düzenleyici**, **Otomatik GitHub Uygulama Güncellemeleri**, **bilgisayar gerektirmeyen dahili CleanYourTV Debloat aracı (341 TV modeli + Manuel Sistem Uygulaması Debloat + Sistem Başlatıcılarını Devre Dışı Bırakma)**, **Özelleştirilebilir Evrensel Denetim Paneli (`Hızlı Ayarlar`)**, **Son Uygulamalar Değiştiricisi**, **çoklu basış (`1x / 2x / 3x`) Kumanda Tuş Atayıcı + Tuş Kodu Gönder (`Send Key Code`) + Ana Ekran Tuşu Düzeltmesi** ve **Çift Modlu Güvenli Yerel ADB Kurulumu (Port 5555'i açık bırakmadan Otomatik Onarım & Ses Tuşu Düzeltmesi)** özelliklerini bir araya getirir.

---

## 🆕 `v2.0.6` Sürümündeki Yenilikler

- **Çift Modlu ADB Güvenlik Mimarisi & Tek Tıkla ADB Aç/Kapat (`Ayarlar → Gelişmiş` & İlk Kurulum)**:
  - **Güvenli Mod (İzinleri Ver ve ADB'yi Kapat — Varsayılan & Önerilen)**: Kurulum sırasında `WRITE_SECURE_SETTINGS` ve `DUMP` izinlerini bir kez alıp ağ ADB bağlantısını (`adb_enabled = 0`) hemen kapatır; böylece yerel ağınızda `5555` portu açık kalmaz. ADB tamamen kapalıyken bile Erişilebilirlik Servisini (`ServiceRepair`) ve **Ses Tuşu Basılı Tutma Düzeltmesini** uygulama içinden çalıştırır.
  - **Kalıcı ADB Modu (Onay Pencereli İsteğe Bağlı Mod)**: CleanYourTV gibi çalışma zamanı ADB araçları için ADB'yi açık tutmak isteyen kullanıcılara özel mod. ADB kapalıyken Kalıcı Mod seçildiğinde USB Hata Ayıklamayı açıp geçmek için onay penceresi gösterir; ADB kapatıldığında ise otomatik olarak Güvenli Moda döner.
  - **Tek Tıkla ADB Aç / Kapat Butonu**: Kablosuz Hata Ayıklamayı (`adb_wifi_enabled`) açmadan yalnızca USB Hata Ayıklamayı (`adb_enabled`) doğrudan `Ayarlar → Gelişmiş` menüsünden açıp kapatabilirsiniz.
  - **Cihaz Yöneticisi İzninin Kaldırılması & İsteğe Bağlı Keep Alive**: Uygulamanın silinmesini zorlaştıran `BIND_DEVICE_ADMIN` izni tamamen kaldırıldı (ekran kapatma Android 9+ `GLOBAL_ACTION_LOCK_SCREEN` ile çalışır) ve Keep Alive varsayılan olarak kapalı (`İsteğe Bağlı`) hale getirildi.
- **Dahili Kilit Ekranı & Boşta Kalma Zamanlayıcısı (`Ayarlar → Denetim Paneli → Ortam Modu`)**:
  - Denetim Panelindeki Ortam Modu tuşuna basıldığında veya **Boşta Kalma Süresi (`Kapalı / 5 / 10 / 15 / 30 / 60 dk`)** dolduğunda **Ortam Modu (Sistem Varsayılanı)** ya da yeni **Dahili Kilit Ekranı** açılacak şekilde seçim yapabilirsiniz.
  - **Kilit Ekranı Ayarları Alt Menüsü**: Tüm Gece Sahneleri, Klasik duvar kağıtları, Özel 1–5 veya **Klasörden Karıştır** seçeneklerinden birini seçebilir, **Saat Konumunu (`Sol / Orta / Sağ`)** ayarlayabilir ve duvar kağıdı üzerinde saat, tarih, karşılama mesajı ile canlı hava durumunu görüntüleyebilirsiniz (kumandadan herhangi bir tuşa basıldığında anında kaldığınız yere döner).
- **Klasörden Karışık Duvar Kağıdı (`Ayarlar → Görünüm ve Sahneler` & `Kilit Ekranı`)**:
  - Yalnızca 5 özel görselle sınırlı kalmadan dahili depolama veya USB bellekteki herhangi bir klasörü seçip (**Klasör Seç ve Karıştır**) içindeki tüm görselleri masaüstü veya kilit ekranı arka planı olarak karışık oynatabilirsiniz.
- **Gece Sahneleri Ana Ekran Hava Durumu Kısayolu (`Ayarlar → Uygulamalar`)**:
  - Gece Sahneleri ana ekranında sağ üstteki Denetim Paneli butonunun yanına, tıklandığında 5 günlük hava durumu penceresini açan cam **Hava Durumu Simgesi** eklendi (varsayılan açık, `Ayarlar → Uygulamalar → Ana ekran hava durumu kısayolu` üzerinden kapatılabilir).
- **Özelleştirilebilir Denetim Paneli Öğeleri (`Ayarlar → Denetim Paneli`)**:
  - Denetim Panelindeki tüm kutucukları (**Wi-Fi, Bluetooth, RAM, Rahatsız Etmeyin, Ses, Uyku Zamanlayıcısı, Ortam Modu, Kapat, Girişler, Tuş Atayıcı, Ayarlar, Arama**) isteğinize göre tek tek açıp kapatabilirsiniz (tümü varsayılan olarak açıktır).
- **Düğme Eşleyicide Tuş Kodu Gönder (`Send Key Code`) Seçeneği (`Ayarlar → Düğme Eşleyici`)**:
  - tvQuickActions gibi üçüncü parti uygulamalara gerek kalmadan kumandadaki herhangi bir tuşa (`1x / 2x / 3x` basış) **Tuş Kodu Gönder** (*Oynat/Duraklat, Sonraki, Önceki, Sessiz, Ses Aç/Kıs, Geri, Ana Ekran, Menü, TV Girişi, Kanal İleri/Geri, Altyazı, Rehber, Bilgi* veya özel Android `KeyEvent` kodu) atayabilirsiniz.
- **Evrensel Ses Tuşu Basılı Tutma Düzeltmesi (Hem Güvenli Modda Hem Kalıcı ADB Modunda Çalışır)**:
  - `android.permission.DUMP` izniyle çalışan uygulama içi `AudioService` Binder izleyicisi sayesinde **ADB tamamen kapalıyken (Güvenli Mod)** ve **Kalıcı ADB Modunda**, hem Android 9–12 (`Audio event log: volume changes`, Magcubic HY320 projeksiyon cihazları dahil) hem de Android 13–14+ (`Events log: volume changes`) TV'lerde sorunsuz çalışır.
- **Açılış Hızı, Uyumluluk ve İzlemeye Devam Et Düzeltmeleri**:
  - **Daha Hızlı Sistem Açılışında Başlatma**: `PowerService`, sistem açılışında Erişilebilirlik Servisi bağlandığı anda (`BOOT_COMPLETED` gecikmesini beklemeden) başlatıcıyı öne getirir ve açılışta stok başlatıcı pencerelerini yakalar.
  - **Philips Canlı TV (`org.droidtv.playtv`) Düzeltmesi**: Philips TV'lerde canlı TV / uydu alıcısı paketi olan `org.droidtv.playtv`, `KNOWN_SYSTEM_LAUNCHERS` listesinden çıkarıldı.
  - **Android 9 İzin Kaldırma Çökme Düzeltmesi & Anlık `5 / 5` İzin Sayacı**: Android 9 TV'lerde izinleri geri alırken oluşan çökme düzeltildi ve izinler onarıldığında `Ayarlar → Gelişmiş` ekranındaki sayacın anında `5 / 5 aktif` olarak güncellenmesi sağlandı.
  - **Varsayılan Ana Ekran Seçici & İlk Kurulum Sorun Giderme Menüsü**: Android sistem ana ekran seçicisi (`ROLE_HOME` / `ACTION_HOME_SETTINGS`) onarıldı ve 4 adımlı Varsayılan Ana Ekran sorun giderme menüsü İlk Kurulum Sihirbazına da eklendi.
  - **İzlemeye Devam Et (`Continue Watching`) Düzeltmesi**: Gerçek medya içeriği olmadığında sahte/demo içeriklerin gösterilmesi tamamen engellendi (içerik yoksa satır gizlenir) ve YouTube / SmartTube videolarına tıklandığında yalnızca uygulamayı açmak yerine doğrudan seçilen videoyu başlatan `Intent.parseUri` / `vnd.youtube` derin bağlantı desteği eklendi.

---

## 🆕 `v2.0.5` Sürümündeki Yenilikler

- **Boyutlandırılabilir Gece Sahneleri Dock'u (`%50` – `%150`) & Çok Satırlı Navigasyon**: *Görünüm ve sahneler* menüsünün en üstünden Gece Sahneleri dock boyutunu `%50` ile `%150` arasında (`%100` varsayılan) ölçeklendirebilirsiniz. Dock büyütüldüğünde ekrana sığmayan fazla uygulamalar otomatik olarak **Diğer uygulamalar (`...`)** menüsüne taşınır; açılan çok satırlı dock içerisinde **Yukarı / Aşağı / Sol / Sağ** yön tuşlarıyla gezinebilir ve klasör boşaldığında `Diğer uygulamalar` simgesi otomatik gizlenir.
- **Yenilenen Apple tvOS Klasik Dock & Simetrik Odaklama**: Klasik görünümde dock ve uygulama simgelerinin köşe yuvarlaklığı Apple tvOS stilinde güncellendi, uygulama kutucukları büyütüldü, ilk ve son sıradaki uygulama seçildiğinde simetrik kenar boşluğu sağlandı ve satırlar arası geçiş animasyonu akıcı hale getirildi.
- **Ayarlar İçinde Yerel Sağ Panel Alt Menüleri**: CleanYourTV, Manuel Debloat, Sistem Başlatıcılarını Devre Dışı Bırakma ve diğer tüm alt menüler artık açılır pencere (popup) yerine Ayarlar menüsünün sağ tarafında yerel alt menü olarak açılır; 341 TV modeli ve sistem uygulamaları arka planda önbelleğe alınarak anında yüklenir.
- **Tek Tıkla Sistem Başlatıcılarını Devre Dışı Bırakma & Manuel Debloat**: Yüklü stok başlatıcıları (*Google TV Home / LauncherX, Android TV Home / Leanback, TCL, Philips, Xiaomi, Sony* vb.) tespit edip tek dokunuşla devre dışı bırakan **Sistem başlatıcılarını devre dışı bırak** menüsü ve tüm sistem paketlerini arayıp yönetebileceğiniz **Manuel debloat (Tüm sistem uygulamaları)** ekranı eklendi.
- **Gelişmiş TCL, Philips, Xiaomi ve Android 11–14 Uyumluluğu**:
  - **Android 13/14 Kısıtlanmış Ayarlar (`ACCESS_RESTRICTED_SETTINGS`) Kilidi Açma**: Yerel ADB izin verme ve onarma işlemleri artık `appops ACCESS_RESTRICTED_SETTINGS allow` komutunu otomatik çalıştırarak Android 13 ve 14 TV'lerde Erişilebilirlik Servisinin sistem tarafından engellenmesini önler.
  - **Ana Ekran (Home) Tuşunu Düzelt (`Ayarlar → Düğme eşleyici`)**: Varsayılan başlatıcı ayarını yok sayan TCL ve Philips (Android 11/12) TV'ler için Home tuşunu doğrudan tvOS Launcher'a yönlendiren isteğe bağlı seçenek (varsayılan kapalı).
  - **Açılışta Otomatik Başlat (`Ayarlar → Gelişmiş`)**: Sistem açılışından hemen sonra başlatıcıyı otomatik çalıştıran seçenek (varsayılan kapalı).
  - **Tüm İzinleri Kaldır / Cihaz Yöneticisini Devre Dışı Bırak (`Ayarlar → Gelişmiş`)**: Cihaz Yöneticisi kapatma ekranı bulunmayan TV'lerde uygulamayı kolayca kaldırabilmek için tek tıkla tüm izinleri geri alma seçeneği.
  - **Çökme Korumalı Kumanda Tuş Yönetimi & Tuş Sesleri**: ADB izin onarımı sırasındaki siyah ekran döngüsü giderildi, `PowerService` kumanda tuşlarını hiçbir koşulda kilitlemeyecek şekilde güçlendirildi, *Hızlı düğmeler* başlığı 13 dilde **Hızlı ayarlar** olarak güncellendi ve **Tuş sesleri** varsayılan olarak **Kapalı** ayarlandı.

---

## ✨ Öne Çıkan Özellikler

- **Çift Görünüm Modu (`Gece Sahneleri` & `Klasik`) + Özel Duvar Kağıtları**
  - **Gece Sahneleri (Night Scenes)**: Dağ ve manzara katmanlarının arkasında duran dev cam saat (*iPhone Duo*, *iPhone Rounded*, *Tall Glass*), derinlikli arka plan sahneleri (*Gece*, *Altın Saat*, *Su Çizgisi*, *Sis*, *Yağmur Ormanı Şafağı* ve **Sol / Orta / Sağ Saat Konumu** seçilebilen **5 adede kadar Özel Sahne Duvar Kağıdı**), kişisel karşılama mesajı, tarih, canlı hava durumu ve dinamik boyutlanan **macOS Liquid Glass Dock (`%50` – `%150`)**.
  - **Uygulama Simgesi Değiştirici & Simge Düzenleyici (`JPG / PNG / ICO`)**:
    - Dock veya ızgara üzerindeki herhangi bir uygulamaya **OK** tuşuyla basılı tutup **Uygulama simgesini değiştir** seçeneğiyle dahili depolama veya USB bellekten `.jpg`, `.jpeg`, `.png` ya da `.ico` dosyası seçebilirsiniz.
    - Dahili **Uygulama Simgesi Düzenleyici** ile canlı önizleme, **14 arka plan rengi** (otomatik kenar rengi algılama dahil), **Yakınlaştırma / Uzaklaştırma** ve **Sol / Sağ / Yukarı / Aşağı** hizalama ayarlarını anında yapabilirsiniz.
  - **Uygulama Yeniden Adlandırma, Diğer Uygulamalar & Gizlenen Uygulamalar Ayrımı**:
    - Dock üzerindeki herhangi bir uygulamaya **OK** tuşuyla basılı tutarak **Yeniden Adlandır**, **Uygulama simgesini değiştir**, **Taşı**, **Diğer Uygulamalara Taşı**, **Gizle**, **Uygulama Bilgisi**, **Kaldır** veya **Düzeni Sıfırla** seçeneklerini kullanabilirsiniz.
    - **Diğer Uygulamalara Taşı**: Uygulamayı ilk dock satırından kaldırır ancak dock'taki **`...` (Diğer uygulamalar)** butonunun içinde tek tıkla erişilebilir tutar (klasör boşsa otomatik gizlenir, çok satırlı olduğunda Yukarı/Aşağı yön tuşlarıyla gezilebilir).
    - **Gizlenen Uygulamalar (`Ayarlar → Uygulamalar → Gizlenen uygulamalar`)**: Uygulamayı hem ana dock satırından hem de Diğer Uygulamalar menüsünden tamamen gizler. **Ayarlar → Uygulamalar → Gizlenen uygulamalar** menüsünden gizlenen bir uygulamanın üzerine **OK** ile basarak doğrudan açabilir veya **OK tuşuna basılı tutarak** ana ekrana geri yükleyebilirsiniz.
  - **Klasik (Classic)**: Apple tvOS tarzı yuvarlatılmış köşelere sahip 6 sütunlu uygulama ızgarası ve ortalanmış cam dock, simetrik odak boşlukları, akıcı satır kaydırma animasyonu, yerleşik fotoğraflar, ortam rengi temaları veya kendi **Özel Duvar Kağıdınız** ve isteğe bağlı Üst Raf (Top Shelf).
- **Otomatik ve Manuel GitHub Güncellemeleri (`Ayarlar → Güncellemeler`)**
  - Hakkında menüsünün hemen üzerinde yer alan **Güncellemeler** menüsü ile mevcut sürümü ve GitHub üzerindeki en son sürümü görüntüleyebilir, tek tıkla güncelleme kontrolü yapabilir veya **Otomatik güncellemeler** seçeneğini açıp kapatabilirsiniz.
- **CleanYourTV (Beta) — Bilgisayar Gerektirmeyen Dahili Android TV Debloat Aracı (`Ayarlar → Gelişmiş`)**
  - **[CleanYourTV](https://github.com/Gathin23/cleanyourtv)** debloat profillerini **Yerel ADB (`127.0.0.1:5555`)** üzerinden doğrudan TV'nizde yerel sağ panel menülerinde çalıştırır — **bilgisayar veya harici CLI gerektirmez**.
  - **341 Gömülü TV & Box Profili**: *TCL, Sony, Xiaomi, Hisense, Philips, Skyworth, OnePlus, Realme, Nvidia Shield, Motorola, Toshiba, Sharp, Onn, Chromecast with Google TV* ve daha fazlası.
  - **Gelişmiş Üretici (OEM) Model Algılama & ADB Paket Benzerlik Tarayıcısı**: TV'nizin donanım özelliklerinden tam veya yakın modelleri otomatik bulur, 341 profili gecikmesiz aramanızı sağlar veya TV'nizdeki üreticiye özgü sistem paketlerini ADB ile ağırlıklı olarak tarayarak en yakın modelleri yüzdelik benzerlik oranıyla sıralar.
  - **Sistem Başlatıcılarını Devre Dışı Bırakma & Manuel Debloat**: Yüklü stok başlatıcıları (*Google TV Home, Android TV Home*, OEM başlatıcılar) tek tıkla devre dışı bırakma veya yeniden etkinleştirme menüsü ve tüm sistem uygulamalarını tek tek yönetebileceğiniz **Manuel debloat** ekranı.
  - **Seçmeli Debloat Kontrol Listesi**: Telemetri, reklam satırları ve gereksiz ön yüklü uygulamalar ön seçili gelirken; **isteğe bağlı uygulamalar** (*Google Asistan, YouTube Music, Play Oyunlar, TalkBack, Chromecast alıcısı, Telefon Kumanda Servisi*) otomatik seçilmez ve listenin en altında yer alır.
  - **Google TV `setupwraith` Wi-Fi Koruması**: Google TV cihazlarda `setupwraith` / `launcherx` devre dışı bırakılmadan önce Wi-Fi ağını unutup yeniden eklemeniz konusunda uyarır (veya tek dokunuşla `setupwraith`'i güvenle atlamanızı sağlar).
  - **Tek Tıkla Yedekleme, Geri Yükleme & AdGuard DNS**: Devre dışı bırakılan paketleri kaydeder, canlı **Debloat Durumunu** (`Tamamlandı` / `Yapılmadı`) gösterir, istediğiniz zaman tek dokunuşla tüm uygulamaları geri yükler ve **Reklam Engelleyici Özel DNS (`dns.adguard-dns.com`)** anahtarı sunar.
- **Evrensel Cam Denetim Paneli (`Hızlı Ayarlar`)**
  - Ana ekrandan veya Tuş Atayıcı erişilebilirlik servisi sayesinde **çalışan herhangi bir uygulamanın üzerinde** gerçek zamanlı bulanık arka plan ile açılır.
  - **Wi-Fi**, **Bluetooth**, **RAM / Son Uygulamalar**, **Rahatsız Etmeyin**, **Ses Çıkışı**, **Uyku Zamanlayıcısı**, **Ortam Modu**, **Kapat / Ekranı Kapat**, **Girişler (HDMI)**, **Tuş Atayıcı**, **Ayarlar** ve **Arama** kısayollarını içerir.
  - **Evrensel Marka Etkinlik Seçici (`Ayarlar → Denetim Paneli`)**: **Wi-Fi**, **Bluetooth**, **Ses** veya **Girişler** kutucuklarına tıklandığında TV'nizdeki hangi sistem uygulamasının ve alt ekranın (Activity) açılacağını özgürce seçebilirsiniz.
- **Son Uygulamalar Değiştiricisi (Recent Apps Switcher) & RAM Temizleyici**
  - Atanmış kumanda tuşuyla veya Denetim Panelindeki **RAM** kutucuğundan her uygulamanın üzerinde açılır.
  - Canlı bellek kullanımını (`kullanılan / toplam RAM`), yatay uygulama kartlarını, **Yukarı (DPAD UP)** tuşuyla tekil uygulama kapatmayı (`force-stop`) ve **Tümünü Kapat** butonunu içerir; tüm uygulamalar kapatıldığında 1.5 saniye sonra otomatik olarak ana ekrana döner.
- **Çoklu Basış Kumanda Tuş Atayıcı (`1x` / `2x` / `3x`), Ana Ekran Tuşu & Ses Tuşu Düzeltmesi**
  - Seçtiğiniz herhangi bir kumanda tuşuna **Tek Basış (1x)**, **Çift Basış (2x)** ve **Üçlü Basış (3x)** için bağımsız görevler atamanızı sağlar.
  - **Ana Ekran (Home) Tuşunu Düzelt (Varsayılan Kapalı)**: Varsayılan ana ekran ayarını yok sayan TV'lerde Home tuşunu doğrudan tvOS Launcher'ı açacak şekilde yeniden eşler.
  - **Ses Tuşu Basılı Tutma Düzeltmesi (Varsayılan Açık)**: Erişilebilirlik servisi etkinleştirildiğinde ses açma/kısma tuşuna basılı tutmanın çalışmaması sorununu otomatik olarak çözer.
- **Yerel ADB ile Kolay Kurulum, Keep Alive Otomatik İzin Onarımı & Tek Tıkla İzin Kaldırma**
  - **Kolay Kurulum (ADB ile Otomatik)** `127.0.0.1:5555` üzerinden bağlanarak `ACCESS_RESTRICTED_SETTINGS` (Android 13/14+) kilidini açar ve Varsayılan Ana Ekran, Erişilebilirlik Servisi, Cihaz Yöneticisi, Bildirim Erişimi ve Kullanım İzinlerini tek tıkla verir.
  - **Keep Alive (Varsayılan Açık)**: Arka planda sıfır ek CPU/RAM yüküyle izinleri kontrol eder ve TV yazılımı Erişilebilirlik iznini kapatırsa Yerel ADB üzerinden otomatik olarak onarır.
  - **Tüm İzinleri Kaldır (`Ayarlar → Gelişmiş`)**: Uygulamayı kaldırmak istediğinizde Cihaz Yöneticisi ve diğer özel izinleri tek dokunuşla devre dışı bırakır.
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

| Görünüm & Sahneler (`Dock Boyutu %50–%150`) | Denetim Paneli (`Hızlı Ayarlar`) |
| :---: | :---: |
| ![Ayarlar - Görünüm ve Sahneler](screenshots_tr/Setting1.png) | ![Ayarlar - Denetim Paneli](screenshots_tr/Setting2.png) |

| Düğme Eşleyici (`1x / 2x / 3x` & Home Tuşu Düzeltmesi) | Gelişmiş, ADB Durumu & İzin Kontrolü (`5 / 5`) |
| :---: | :---: |
| ![Ayarlar - Düğme Eşleyici](screenshots_tr/Setting3.png) | ![Ayarlar - Gelişmiş ve ADB](screenshots_tr/Setting4.png) |

| CleanYourTV — Sistem Başlatıcılarını Devre Dışı Bırak | Otomatik ve Manuel GitHub Güncellemeleri |
| :---: | :---: |
| ![Ayarlar - Sistem Başlatıcılarını Devre Dışı Bırak](screenshots_tr/Setting5.png) | ![Ayarlar - Güncellemeler](screenshots_tr/Setting6.png) |

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
