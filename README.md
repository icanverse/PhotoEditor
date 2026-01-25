# 📸 PhotoEditor Core Engine

`ImageProcessor`, projenin merkezinde yer alan, OpenCV kütüphanesini kullanarak gelişmiş görüntü işleme yeteneklerini **Fluent API** (zincirleme kullanım) yapısıyla sunan ana sınıftır.

---

## ✨ Temel Özellikler

* **Akıcı Arayüz (Method Chaining):** Tüm filtreleri tek bir satırda birleştirerek temiz kod yazımı sağlar.
* **Otomatik Veri Analizi:** Her işlemden sonra görüntünün histogram ve renk verilerini (`ImageAnalysis`) otomatik olarak günceller.
* **Akıllı Bellek Yönetimi:** `byte[]` ve OpenCV `Mat` nesneleri arasında verimli dönüşüm yapar.
* **Paralel İşleme:** `ParallelProcessor` altyapısı ve OpenCV native metodları ile yüksek performanslı filtreleme sunar.
* **Entegre Metadata:** Görüntü işlenirken orijinal dosyanın meta verilerini (`EXIF` vb.) korur.

---

## 📦 Kurulum (Installation)

Projeye dahil etmek için aşağıdaki iki yöntemden birini seçebilirsiniz. **JitPack (Yöntem 1) en kolay ve hızlı olanıdır.**

### Yöntem 1: JitPack ile (Önerilen - Token Gerektirmez) 🚀

**Adım 1:** Projenizin kök dizinindeki `settings.gradle` (veya proje düzeyindeki `build.gradle`) dosyasına JitPack deposunu ekleyin:

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url '[https://jitpack.io](https://jitpack.io)' }
    }
}

**Adım 2: Modül düzeyindeki (app) build.gradle dosyanıza bağımlılığı ekleyin:

dependencies {
    implementation 'com.github.icanverse:PhotoEditor:1.0.1'
}

Yöntem 2: GitHub Packages ile (Token Gerektirir) 🔒
Eğer GitHub Packages kullanmayı tercih ederseniz:

GitHub ayarlarından read:packages yetkisine sahip bir Personal Access Token (Classic) oluşturun.

gradle.properties dosyanıza kullanıcı adınızı ve token'ınızı ekleyin (gpr.usr ve gpr.key).

Proje düzeyindeki build.gradle dosyanıza şu bloğu ekleyin:

repositories {
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("[https://maven.pkg.github.com/icanverse/PhotoEditor](https://maven.pkg.github.com/icanverse/PhotoEditor)")
        credentials {
            username = project.findProperty("gpr.usr") ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation 'com.github.icanverse:photo-editor:1.0.1'
}


🚀 Kullanım Rehberi
1. Başlatma
Sınıfı bir byte[] dizisi ile başlatın. Arka planda LibraryLoader ile gerekli yerel kütüphaneler otomatik olarak yüklenir.

2. Tüm Yetenekler ve Filtreleme
Aşağıdaki örnekte ImageProcessor içinde bulunan tüm metodların kullanımını görebilirsiniz. Fluent yapısı sayesinde istediğiniz metodları seçip uç uca ekleyebilirsiniz.

Java

import org.opencv.imgproc.Imgproc; // Font sabitleri için gerekli
import com.github.icanverse.photoeditor.core.ImageProcessor;

// Görüntüyü byte dizisi olarak al
byte[] imageBytes = ...; 

byte[] finalResult = new ImageProcessor(imageBytes)
    // --- Renk, Işık ve Detay Ayarları ---
    .addBrightness(25.0)            // Parlaklık ekler (pozitif veya negatif)
    .addContrast(1.5)               // Kontrastı artırır (>1 artırır, <1 azaltır)
    .addSaturation(1.2)             // Renk doygunluğunu canlandırır
    .addExposure(1.1)               // Pozlamayı (Exposure) artırır
    .addSharpen(0.5)                // Keskinleştirme uygular (Detayları belirginleştirir)
    .addClarity(5.0)                // Netlik (Clarity) ekler (Orta ton kontrastı)
    .makeGrayscale()                // Görüntüyü siyah-beyaz yapar

    // --- Geometrik İşlemler ---
    .rotate(45.0)                   // Resmi 45 derece döndürür (Varsayılan beyaz arka plan)
    .rotate(45.0, 0, 0, 0)          // 45 derece döndürür, boşlukları siyah yapar
    .rotateRight()                  // 90 derece sağa döndürür
    .rotateLeft()                   // 90 derece sola döndürür
    .flipHorizontal()               // Yatayda aynalar (sağ-sol takla)
    .scale(0.5)                     // Resmi %50 oranında küçültür
    .resize(800, 600)               // Net piksel değerlerine göre boyutlandırır
    .cropCenterSquare()             // Görüntüyü merkezden kare olacak şekilde kırpar

    // --- Sanatsal Efektler ---
    .applyPixelate(15)              // 15 piksel boyutunda mozaik/piksel efekti
    .applySepia()                   // Nostaljik kahverengi (Sepya) tonlama uygular
    .applyVignette(1.2)             // Kenarları karartarak (Vignette) odağı merkeze toplar

    // --- Metin ve Filigran (Watermark) ---
    .addWatermark("PROJE X", 2.0, 255, 0, 0, Imgproc.FONT_HERSHEY_COMPLEX) // Ortaya kırmızı yazı
    .addText("v1.0", 50, 50, 1.0, 255, 255, 255) // Koordinata (x=50, y=50) beyaz yazı ekler
    .addFooterText("© 2026")        // Sol alt köşeye küçük imza atar

    // --- Sonuç ve Çıktı ---
    .process();                     // Tüm işlemleri uygular ve byte[] çıktı üretir

📄 Lisans
Bu proje Apache License 2.0 ile lisanslanmıştır. Daha fazla bilgi için LICENSE dosyasına bakabilirsiniz.

Copyright © 2026 icanverse
