package photoeditor.filters;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import photoeditor.core.ImageAnalysis;
import photoeditor.utils.ParallelProcessor; // Senin yazdığın sınıf

public class AdaptiveFilters {

    private final ImageAnalysis analyzer;
    private Mat source;      // Hata almamak için buraya ekledik
    private Mat destination; // Hata almamak için buraya ekledik

    public AdaptiveFilters() {
        // Analiz nesnesini boş başlatıyoruz, her işlemde yeniden oluşturacağız
        this.analyzer = null;
    }

    /// Dış Kaynaklı Filtre (External Mood)
    public void applyCustomColorFilter(Mat source, Mat destination, Scalar customColor, double intensity) {
        // Doğrudan mevcut motoru kullanıyoruz
        applyColorOverlayParallel(source, destination, customColor, intensity);
    }

    /// Atmosphere Filtresi
    public void applyAtmosphereFilter(Mat source, Mat destination, double intensity) {
        // Rengi bul
        ImageAnalysis tempAnalyzer = new ImageAnalysis(source);
        Scalar dominantColor = tempAnalyzer.getDominantColor(source);

        // Paralel Blending
        applyColorOverlayParallel(source, destination, dominantColor, intensity);
    }

    /// Candle Filtresi
    public void applyCandleEffect(Mat source, Mat destination, double intensity) {
        // BGR: Mavi: 50, Yeşil: 140, Kırmızı: 255
        Scalar candleColor = new Scalar(50, 140, 255);

        // İşlemi paralel motor üzerinden yapıyoruz
        applyColorOverlayParallel(source, destination, candleColor, intensity);
    }

    /**
     * Çekirdek Metot (Paralel Renk Karıştırma)
     * ParallelProcessor kullanarak resmi dilimler ve karıştırır.
     */
    private void applyColorOverlayParallel(Mat source, Mat destination, Scalar color, double intensity) {
        // Hedef matrisi hazırla
        if (destination.empty() || source.size().width != destination.size().width) {
            destination.create(source.size(), source.type());
        }

        ParallelProcessor.splitAndRun(source.rows(), (startRow, endRow) -> {

            // Kaynak resimden ilgili şeridi al
            Mat srcSlice = source.submat(startRow, endRow, 0, source.cols());
            Mat destSlice = destination.submat(startRow, endRow, 0, destination.cols());

            // Şerit boyutunda (küçük) bir renk katmanı oluşturuyoruz.
            Mat smallOverlay = new Mat(srcSlice.size(), srcSlice.type(), color);

            // Karıştırma İşlemi (OpenCV native metodu)
            Core.addWeighted(srcSlice, 1.0 - intensity, smallOverlay, intensity, 0, destSlice);

            // Thread içi bellek temizliği
            srcSlice.release();
            destSlice.release();
            smallOverlay.release();
        });
    }

    /// Midnight (Gece Modu) Filtresi
    // intensity: 0.0 (etkisiz) - 1.0 (tam etkili)
    public void applyMidnightEffect(Mat source, Mat destination, double intensity) {
        Mat temp = new Mat();
        // Kontrast ve parlaklık ayarını yoğunluğa göre ölçeklendiriyoruz
        // 1.2 kontrast çarpanı ve -20 parlaklık değerini intensity ile çarpıyoruz
        double alpha = 1.0 + (0.2 * intensity);
        double beta = -20 * intensity;

        source.convertTo(temp, -1, alpha, beta);

        Scalar coolBlue = new Scalar(100, 30, 10); // BGR
        applyColorOverlayParallel(temp, destination, coolBlue, 0.25 * intensity);
        temp.release();
    }

    /// Golden Hour (Altın Saat) Filtresi
    public void applyGoldenHour(Mat source, Mat destination, double intensity) {
        Scalar goldenTone = new Scalar(20, 120, 220);
        // Renk bindirmesini yoğunluğa göre uygula
        applyColorOverlayParallel(source, destination, goldenTone, 0.20 * intensity);

        // Parlaklık artışını (10) yoğunluğa göre ekle
        double brightnessBoost = 15 * intensity;
        Core.add(destination, new Scalar(brightnessBoost, brightnessBoost, brightnessBoost), destination);
    }

    /// Dramatic Black & White (Parametrik Siyah Beyaz)
    public void applyDramaticBW(Mat source, Mat destination, double intensity) {
        Mat gray = new Mat();
        Mat bwResult = new Mat();

        // Siyah beyaz dönüşümü ve histogram eşitleme
        Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(gray, gray);
        Imgproc.cvtColor(gray, bwResult, Imgproc.COLOR_GRAY2BGR);

        // Orijinal resim ile BW sonucu yoğunluğa göre karıştır
        Core.addWeighted(source, 1.0 - intensity, bwResult, intensity, 0, destination);

        gray.release();
        bwResult.release();
    }

    /// Retro/Sepia Filtresi
    public void applyRetroSepia(Mat source, Mat destination, double intensity) {
        Mat sepiaResult = new Mat();
        Mat sepiaKernel = new Mat(3, 3, CvType.CV_32F);

        sepiaKernel.put(0, 0, 0.272, 0.534, 0.131);
        sepiaKernel.put(1, 0, 0.349, 0.686, 0.168);
        sepiaKernel.put(2, 0, 0.393, 0.769, 0.189);

        Core.transform(source, sepiaResult, sepiaKernel);

        // Orijinal resim ile Sepia sonucunu karıştır
        Core.addWeighted(source, 1.0 - intensity, sepiaResult, intensity, 0, destination);

        sepiaKernel.release();
        sepiaResult.release();
    }
}