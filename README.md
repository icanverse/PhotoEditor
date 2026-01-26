# 📸 PhotoEditor Core Engine

`ImageProcessor`, projenin merkezinde yer alan, OpenCV kütüphanesini kullanarak gelişmiş görüntü işleme yeteneklerini **Fluent API** (zincirleme kullanım) yapısıyla sunan ana sınıftır.

---

##  Temel Özellikler

* **Akıcı Arayüz (Method Chaining):** Tüm filtreleri tek bir satırda birleştirerek temiz kod yazımı sağlar.
* **Otomatik Veri Analizi:** Her işlemden sonra görüntünün histogram ve renk verilerini (`ImageAnalysis`) otomatik olarak günceller.
* **Akıllı Bellek Yönetimi:** `byte[]` ve OpenCV `Mat` nesneleri arasında verimli dönüşüm yapar.
* **Paralel İşleme:** `ParallelProcessor` altyapısı ve OpenCV native metodları ile yüksek performanslı filtreleme sunar.
* **Entegre Metadata:** Görüntü işlenirken orijinal dosyanın meta verilerini (`EXIF` vb.) korur.

---

##  Kullanım Rehberi

### 1. Başlatma
Sınıfı bir `byte[]` dizisi ile başlatın. 
Arka planda `LibraryLoader` ile gerekli yerel kütüphaneler otomatik olarak yüklenir.

### 2. Tüm Yetenekler ve Filtreleme
Aşağıdaki örnekte `ImageProcessor` içinde bulunan tüm metodların kullanımını görebilirsiniz.
Fluent yapısı sayesinde istediğiniz metodları seçip uç uca ekleyebilirsiniz.

```java
import org.opencv.imgproc.Imgproc; // Font sabitleri için gerekli

byte[] finalResult = new ImageProcessor(imageBytes)
    // --- Temel İşlemler ---
    .addBrightness(25.0)            // Parlaklık ekler (pozitif veya negatif)
    .addContrast(1.5)               // Kontrastı artırır (>1 artırır, <1 azaltır)
    .addSaturation(1.2)             // Renk doygunluğunu canlandırır
    .addExposure(1.1)               // Pozlamayı (Exposure) artırır
    .addSharpen(0.5)                // Keskinleştirme uygular (Detayları belirginleştirir)
    .addClarity(5.0)                // Netlik (Clarity) ekler (Orta ton kontrastı)
    .makeGrayscale()                // Görüntüyü siyah-beyaz yapar

        // --- Ton Ayarlamaları --- 
    .addShadows(0.5)         // Sadece karanlık bölgeleri aydınlatır
    .addHighlights(-0.3)     // Çok parlak alanları kısar (Detay kurtarır)
    .addVibrance(1.5);       // Soluk renkleri canlandırır (Doygunları korur)
    
    
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
    .applyBlur(10)                  // 10 şiddetinde bulanıklık (Blur) verir

    // --- Maskeleme & Bölgesel İşlemler (Lightroom Style) ---

    // 1. Maske Oluşturma
    
    Mask gradientMask = MaskFilters.createLinearGradient(w, h, x1, y1, x2, y2); // Doğrusal geçişli maske oluşturur
    Mask radialMask = MaskFilters.createRadialGradient(w, h, cx, cy, radius);   // Merkezden dışa dairesel maske oluşturur
    mask.addBrushStroke(x, y, radius, hardness);                                // Maskeye fırça darbesi ekler (Kümülatif)
    
    // 2. Maskeyi Uygulama
    
    .applyMaskedFilter(mask, p -> p.addExposure(0.5))       // Filtreyi sadece maskeli alana uygular (Dodge)
    .applyMaskedFilter(mask, p -> p.makeGrayscale())        // Sadece seçili alanı siyah-beyaz yapar
    .applyMaskedFilter(mask, p -> {                         // Seçili alana birden fazla işlem uygular
        p.addContrast(1.1);
        p.addTemperature(20);
    })
    
    // --- Metin ve Filigran (Watermark) ---
    .addWatermark("PROJE X", 2.0, 255, 0, 0, Imgproc.FONT_HERSHEY_COMPLEX) // Ortaya kırmızı yazı
    .addText("v1.0", 50, 50, 1.0, 255, 255, 255) // Koordinata (x=50, y=50) beyaz yazı ekler
    .addFooterText("© 2026")        // Sol alt köşeye küçük imza atar
    .addSticker("assets/watermark.png", 50, 50, 200, 100, 0.3);    // %30 Opaklık ile Çıkartma ekle

    // --- Sonuç ve Çıktı ---
    .process();                     // Tüm işlemleri uygular ve byte[] çıktı üretir

