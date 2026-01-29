package photoeditor.filters;

import org.opencv.core.*;
import photoeditor.core.ImageAnalysis;
import photoeditor.utils.ParallelProcessor; // Senin yazdığın sınıf

public class AdaptiveFilters {

    private final ImageAnalysis analyzer;

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
    public void applyCandleEffect(Mat source, Mat destination) {
        Scalar candleColor = new Scalar(50, 140, 255); // BGR
        applyColorOverlayParallel(source, destination, candleColor, 0.30);
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
}