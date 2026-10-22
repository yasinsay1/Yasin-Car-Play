# Yasin Car Play

[Fermata Auto](https://github.com/AndreyPavlenko/Fermata) (GPL-3.0) tabanlı, Android Auto uyumlu medya ve TV/IPTV uygulaması.

- Uygulama adı: **Yasin Car Play**, paket adı: `com.yasincarplay.auto`
- Taban: Fermata commit `7d2bd42` (2026-09-20)
- Derleme: GitHub Actions → *Yasin Car Play APK* iş akışı (`.github/workflows/yasin-car-play.yml`)
- Derleme hata verirse log özeti `build-logs` dalındaki `last-failure.txt` dosyasına yazılır.
- `legacy/`: Fermata öncesi ilk Java iskeleti (M3U listesi ve oynatıcı). `main` dalında olduğu gibi duruyor.

## Android Auto'da görünmesi için

Uygulama Google Play dışından (GitHub APK'sı) kurulduğu için Android Auto, varsayılan olarak onu araç menüsünde göstermez.

1. Telefonda Android Auto ayarlarını aç, **Sürüm** satırına yaklaşık 10 kez dokunup geliştirici ayarlarını etkinleştir.
2. Sağ üstteki üç nokta → **Geliştirici ayarları** → **Bilinmeyen kaynaklar** seçeneğini aç.
3. Android Auto'yu kapatıp aracın bağlantısını yeniden kur. Uygulama yine görünmezse Android Auto ayarlarında **Başlatıcıyı özelleştir** bölümünden uygulamanın gizli olmadığını kontrol et.

Her derlemede `Android Auto manifest denetimi` adımı, APK içindeki Android Auto bildirimlerini (`com.google.android.gms.car.application`, projeksiyon servisi, `MediaBrowserService`, Car App Library servisi) kontrol eder; eksik varsa derleme hata verir.

## Video ve sürüş güvenliği

Android Auto, sürüş sırasında video oynatmayı kısıtlar. Bu depoya eklenen değişiklikler bu kısıtlamayı aşmayı amaçlamaz. M3U/IPTV videosu telefon ekranında izlenir; araç ekranında video yalnızca Android Auto'nun izin verdiği koşullarda (araç park hâlindeyken) kullanılmalıdır. Fermata'dan devralınan "Mirror" (ekran yansıtma) servisleri değiştirilmemiştir; sürüş sırasında video yansıtmak için kullanılmamalıdır.

Lisans: GPL-3.0 (bkz. `LICENSE`). Fermata'nın orijinal telif hakları Andrey Pavlenko ve katkıda bulunanlara aittir.
