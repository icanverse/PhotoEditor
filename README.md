# 📸 PhotoEditor Core Engine

`ImageProcessor`, projenin merkezinde yer alan, OpenCV kütüphanesini kullanarak gelişmiş görüntü işleme yeteneklerini **Fluent API** (zincirleme kullanım) yapısıyla sunan ana sınıftır.

---

##  Temel Özellikler

* **Akıcı Arayüz (Method Chaining):** Tüm filtreleri tek bir satırda birleştirerek temiz kod yazımı sağlar.
* **Otomatik Veri Analizi:** Her işlemden sonra görüntünün histogram ve renk verilerini (`ImageAnalysis`) otomatik olarak günceller.
* **Akıllı Bellek Yönetimi:** `byte[]` ve OpenCV `Mat` nesneleri arasında verimli dönüşüm yapar.
* **Paralel İşleme:** `ParallelProcessor` altyapısı ve OpenCV native metodları ile yüksek performanslı filtreleme sunar.
* **Yapay Zeka Segmentasyonu:** DeepLabV3 ONNX modeli ile nesne/insan ayrıştırma ve akıllı maskeleme yapar.
* **Entegre Metadata:** Görüntü işlenirken orijinal dosyanın meta verilerini (`EXIF` vb.) korur.
* **Çapraz Platform Desteği:** Windows ve Android mimarileri için özel yerel kütüphane yükleme stratejileri sunar.
---

##  Kullanım Rehberi

### 1. Başlatma
Sınıfı bir `byte[]` dizisi ile başlatın.
Arka planda `LibraryLoader` ile gerekli yerel kütüphaneler otomatik olarak yüklenir.

### 2. Kurulum Notları
#### 2.1 Windows

```gradle
dependencies {
// OpenCV Java sarmalayıcısı
implementation 'org.openpnp:opencv:4.9.0-0'

    // ONNX Runtime (CPU versiyonu yeterlidir)
    implementation 'com.microsoft.onnxruntime:onnxruntime:1.17.1'
}
```

Ve soyutlama katmanı için
```java
    public class WindowsNativeLoader implements NativeLibraryLoader {
    @Override
    public void loadLibrary() {
    nu.pattern.OpenCV.loadLocally(); // OpenCV Windows yüklemesi
    // ONNX Runtime Windows'ta otomatik yüklenir.
    }
}
```
#### 2.2 Android

```gradle
dependencies {
    .
    .
    .
    // ONNX Runtime Android Versiyonu
    implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.17.1'
}
```

Ve soyutlama katmanı için
```java
    public class AndroidNativeLoader implements NativeLibraryLoader {
    @Override
    public void loadLibrary() {
    System.loadLibrary("opencv_java4"); // OpenCV Android
    System.loadLibrary("onnxruntime4j_jni"); // ONNX Android JNI
    }
}
```
Ayrıca modeli kullanabilmek için

```java
    public class AssetUtils {
    public static String copyAssetToInternal(Context context, String fileName) {
    File file = new File(context.getFilesDir(), fileName);
    if (!file.exists()) {
    // Assets'ten oku, internal storage'a yaz (Klasik I/O işlemi)
    }
    return file.getAbsolutePath(); // Bu String'i SmartMaskFactory'ye verecekler.
    }
}
```
### 3. Tüm Yetenekler ve Filtreleme
Aşağıdaki örnekte `ImageProcessor` içinde bulunan tüm metodların kullanımını görebilirsiniz.
Fluent yapısı sayesinde istediğiniz metodları seçip uç uca ekleyebilirsiniz.


```java
import org.opencv.imgproc.Imgproc; // Font sabitleri için gerekli

    byte[] finalResult = new ImageProcessor(imageBytes)
    
    // İşlenmiş görüntüyü diske kaydedebilir veya uygulama içinde kullanmak üzere byte[] veya Mat formatında alabilirsiniz.
    
    // --- Kayıt ve Dönüş Tipleri ---
    try (ImageProcessor editor = new ImageProcessor(imageBytes)) {
    
    // Geri Al - İleri Al Kullanımı
    /// undo() , redo()         --> Uygular
    /// canUndo() , canRedo()   --> Alınabilece işlem var mı ? (boolean)
    
    processor.addBrightness(20)
         .applySepia()
         .addContrast(1.2);
         
    // Örneğin Sepyaya geri döner 
    
    if (processor.canUndo()) {
        processor.undo();
    }
    
    // Geri alınan işlem geri gelir
    
    if (processor.canRedo()) {
        processor.redo();
    }
    
    // İşlemleri uygula
    editor.addBrightness(10).makeGrayscale();

    // 1. Diske Kaydet
    editor.save("output_folder/result.jpg");
    editor.save("output/thumb.jpg", 0.5);   // Görseli *0.5 (küçülterek) kaydet

    // 2. Byte Dizisi Olarak Al (API veya UI için)
    byte[] resultJpeg = editor.getResultAsBytes();       // Varsayılan JPG
    byte[] resultPng  = editor.getResultAsBytes(".png"); // Format belirtilebilir
    byte[] psdBytes = editor.getResultAsBytes(".psd");   // PSD Çıktı verir
 
    // 3. Ham OpenCV Matrisi Olarak Al (İleri seviye işlemler için)
    Mat rawMatrix = editor.getResult();

}   // Blok bitiminde bellek (Native Memory) otomatik temizlenir.
    
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
    
    // --- İleri Seviye Geometrik İşlemler --- 
    
    // Merkez(x,y), Boyut(w,h), Açı(derece)
    RotatedRect selection = new RotatedRect(
        new Point(250, 250), 
        new Size(100, 50), 
        30.0
    );

    photoEditor
        .cropRotated(selection)
        .save("straightened_object.jpg");
        
            // Resim üzerindeki 4 köşe noktası (Sol-Üst, Sağ-Üst, Sağ-Alt, Sol-Alt)
        Point[] corners = new Point[] {
            new Point(50, 50),
            new Point(400, 80),
            new Point(380, 500),
            new Point(60, 480)
        };
    
    photoEditor
        // Köşeleri verilen alanı 500x700 boyutunda düz bir belgeye dönüştürür
        .adjustPerspective(corners, 500, 700)
        .save("scanned_document.jpg");
    
    .adjustPerspective()
    .cropRotated()

    // --- Sanatsal Efektler ---
    .applyPixelate(15)              // 15 piksel boyutunda mozaik/piksel efekti
    .applySepia()                   // Nostaljik kahverengi (Sepya) tonlama uygular
    .applyVignette(1.2)             // Kenarları karartarak (Vignette) odağı merkeze toplar
    .applyBlur(10)                  // 10 şiddetinde bulanıklık (Blur) verir
    .applyBlur_forStream()
    .applyBlur_Fast
    .applyMedianBlur()
    .applyMotionBlur(2,30)          // 2 boyutunda 30 derece bulanıklık

    // --- Adaptif Efekler ---
    .applyCandleEffect(0.4)                // Sıcak, romantik ve loş bir atmosfer verir (Sabit Profil)
    .applyAtmosphereFilter(0.25)        // Görseldeki en baskın rengi bularak 0.25 yoğunlukta filtreler
    .applyStyleFromImage("/downloads/sunset.jpg", 0.50) // Match Color, yoldaki görselin renk yoğunluğu ile ana göreli filtreler
    .applyMidnightEffect(0.4)           // Gece Efekti 0.4 sertlikle
    .applyGoldenHour(0.4)               // Altın Saat Efekti 0.4 sertlikle
    .applyDramaticBW(0.4)               // Dramatik SB 0.4 sertlikle
    .applyRetroSepia(0.4)               // Retro Sepya 0.4 sertlikle

    // 1. Maske Oluşturma
    
    // Tuval boyutlarını al
    int w = image.getWidth();
    int h = image.getHeight();
    
    // 1. Doğrusal (Linear) Geçişli Maske
    // (x1,y1)'den (x2,y2)'ye doğru azalan bir maske oluşturur
    Mask gradientMask = MaskFilters.createLinearGradient(w, h, 0, 0, w, h/2);
    
    // 2. Dairesel (Radial) Maske
    // Merkezden dışarı doğru yumuşayan bir odak alanı (Vignette mantığı)
    Mask radialMask = MaskFilters.createRadialGradient(w, h, w/2, h/2, 300);
    
    // 3. Fırça ile Müdahale (Manuel Boyama)
    // Maskeye yeni alan ekle (Add)
    mask.addBrushStroke(x, y, 50, 0.8f); // x, y, yarıçap, sertlik
    
    // Maskeden alan çıkar (Silgi / Eksiye İnme)
    mask.removeBrushStroke(x, y, 30, 0.9f);                        // Maskeye fırça darbesi ekler (Kümülatif)
        
    // 2. Maskeyi Uygulama
    
    new PhotoEditor("image.jpg")
    // Örnek 1: Sadece maskeli alanı aydınlat (Dodge Efekti)
    .applyMaskedFilter(radialMask, p -> p.addExposure(0.5))

    // Örnek 2: Maskeli alanı Siyah-Beyaz yap (Selective Color)
    .applyMaskedFilter(gradientMask, p -> p.makeGrayscale())

    // Örnek 3: Komplex İşlemler (Zincirleme)
    // Seçili alana aynı anda hem kontrast hem sıcaklık uygula
    .applyMaskedFilter(mask, p -> {
        p.addContrast(1.2);
        p.addTemperature(15);
        p.addBlur(10); // Sadece arka planı flulaştırmak için ideal
    })
    .save("output.jpg");
    
    
    // Eğitilmiş model (AI) özelliklerini kullanabilmek için bir kez SmartMaskFactory başlatılmalıdır:
    SmartMaskFactory factory = new SmartMaskFactory("model_raw.onnx");
    
    // --- AI & Maske Tabanlı Efektler ---
    .addPortraitEffect(30.0, 25)    // Arka planı bulanıklaştır (Sigma: 30, Yumuşaklık: 25)
    .addMotionBlur(50, 0, 15)       // Yatay hız efekti ver (Hız: 50, Açı: 0°)
    .addColorSplash(11)             // Arka planı siyah-beyaz yap, insanı renkli bırak
    .changeBackground(beachMat, 21) // Arka planı yeni bir Mat ile değiştir
    
    // --- Metin ve Filigran (Watermark) ---
    .addWatermark("PROJE X", 2.0, 255, 0, 0, Imgproc.FONT_HERSHEY_COMPLEX) // Ortaya kırmızı yazı
    .addText("v1.0", 50, 50, 1.0, 255, 255, 255) // Koordinata (x=50, y=50) beyaz yazı ekler
    .addFooterText("© 2026")        // Sol alt köşeye küçük imza atar
    .addSticker("assets/watermark.png", 50, 50, 200, 100, 0.3);    // %30 Opaklık ile Çıkartma ekle