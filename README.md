# 📸 PhotoEditor Core Engine

`ImageProcessor`, projenin merkezinde yer alan, OpenCV kütüphanesini kullanarak gelişmiş görüntü işleme yeteneklerini **Fluent API** (zincirleme kullanım) yapısıyla sunan ana sınıftır.

---

##  Temel Özellikler

* **Akıcı Arayüz (Method Chaining):** Tüm filtreleri tek bir satırda birleştirerek temiz kod yazımı sağlar.
* **Otomatik Veri Analizi:** Her işlemden sonra görüntünün histogram ve renk verilerini (`ImageAnalysis`) otomatik olarak günceller.
* **Akıllı Bellek Yönetimi:** `byte[]` ve OpenCV `Mat` nesneleri arasında verimli dönüşüm yapar.
* **Entegre Metadata:** Görüntü işlenirken orijinal dosyanın meta verilerini (`EXIF` vb.) korur.

---

##  Kullanım Rehberi

### 1. Başlatma
Sınıfı bir `byte[]` dizisi ile başlatın. 
Arka planda `LibraryLoader` ile gerekli yerel kütüphaneler otomatik olarak yüklenir.

### 2. Tüm Yetenekler ve Filtreleme
Aşağıdaki örnekte `ImageProcessor` içinde bulunan tüm metodların kullanımını görebilirsiniz.
Fluent yapısı sayesinde istediğiniz metodları seçip uç uca ekleyebilirsiniz.
Sınıfınıza ImageProcessor eklemek yeterlidir.

```java
byte[] finalResult = processor
    // --- Renk ve Işık Ayarları ---
    .addBrightness(25.0)            // Parlaklık ekler (pozitif veya negatif)
    .addContrast(1.5)              // Kontrastı artırır veya azaltır
    .addSaturation(1.2)            // Renk doygunluğunu ayarlar
    .makeGrayscale()               // Görüntüyü siyah-beyaz yapar

    // --- Geometrik İşlemler ---
    .rotate(45.0)                  // Resmi 45 derece döndürür (Varsayılan beyaz arka plan)
    .rotate(45.0, 0, 0, 0)         // 45 derece döndürür, boşlukları siyah (RGB: 0,0,0) yapar
    .rotateRight()                 // 90 derece sağa döndürür
    .rotateLeft()                  // 90 derece sola döndürür
    .flipHorizontal()              // Yatayda aynalar (sağ-sol takla)
    .scale(0.5)                    // Resmi %50 oranında ölçeklendirir
    .resize(800, 600)              // Net piksel değerlerine göre boyutlandırır
    .cropCenterSquare()            // Görüntüyü merkezden kare olacak şekilde kırpar

    // --- Sanatsal Efektler ---
    .applyPixelate(10)             // 10 piksel boyutunda mozaik/piksel efekti uygular

    // --- Sonuç ve Çıktı ---
    .process();                    // Tüm işlemleri uygular ve byte[] çıktı üretir
