package photoeditor.filters;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import photoeditor.utils.ParallelProcessor;

import java.util.ArrayList;
import java.util.List;

public class ToneAdjustment {

    public static Mat applyShadows(Mat source, double value) {
        return processParallel(source, value, true);
    }

    public static Mat applyHighlights(Mat source, double value) {
        return processParallel(source, value, false);
    }

    /**
     * Canlılık (Vibrance) Ayarı
     */
    public static Mat applyVibrance(Mat source, double value) {
        // LUT oluştur (Hesaplama tablosu)
        Mat lut = createVibranceLut(value);

        Mat destination = new Mat(source.size(), source.type());

        // Paralel İşlem Başlat
        ParallelProcessor.splitAndRun(source.rows(), (startRow, endRow) -> {

            Mat srcSub = source.submat(startRow, endRow, 0, source.cols());
            Mat dstSub = destination.submat(startRow, endRow, 0, source.cols());

            // 1. HSV Formatına Çevir (Hue, Saturation, Value)
            // Vibrance işlemi Saturation (Doygunluk) kanalı üzerinde yapılır.
            Mat hsvSub = new Mat();
            Imgproc.cvtColor(srcSub, hsvSub, Imgproc.COLOR_BGR2HSV);

            List<Mat> channels = new ArrayList<>();
            Core.split(hsvSub, channels);

            // 2. LUT'u Sadece "Saturation" (1. Kanal) üzerine uygula
            Core.LUT(channels.get(1), lut, channels.get(1));

            // 3. Kanalları birleştir ve geri dön
            Core.merge(channels, hsvSub);
            Imgproc.cvtColor(hsvSub, dstSub, Imgproc.COLOR_HSV2BGR);

            // Temizlik
            hsvSub.release();
            for (Mat m : channels) m.release();
        });

        lut.release();
        return destination;
    }

    // Vibrance için özel LUT oluşturucu
    private static Mat createVibranceLut(double value) {
        Mat lut = new Mat(1, 256, CvType.CV_8U);
        byte[] lutData = new byte[256];

        // Kullanıcı değeri 0-100 arasında gelir, bunu -1.0 ile 1.0 arasına çekelim (normalize)
        double strength = value / 100.0;

        for (int i = 0; i < 256; i++) {
            double originalSat = i; // 0..255
            double satScale = originalSat / 255.0; // 0.0 .. 1.0 (Matematiksel doygunluk)
            double modifiedSat;

            if (strength >= 0) {
                // CANLILIK ARTIRMA FORMÜLÜ:
                // Doygunluk düşükse (satScale azsa), etki (1.0 - satScale) büyük olur.
                // Doygunluk zaten yüksekse, etki azalır.
                // Formül: Sat + Güç * (1 - Sat)
                modifiedSat = originalSat + (strength * (1.0 - satScale) * 128.0);
                // Not: 128.0 katsayısı etkinin şiddetini ayarlar.
            } else {
                // CANLILIK AZALTMA:
                // Standart doygunluk düşürme gibi davranır.
                modifiedSat = originalSat + (originalSat * strength);
            }

            // 0-255 sınırla
            modifiedSat = Math.max(0, Math.min(255, modifiedSat));
            lutData[i] = (byte) modifiedSat;
        }
        lut.put(0, 0, lutData);
        return lut;
    }

    private static Mat processParallel(Mat source, double value, boolean isShadowAdjustment) {
        // 1. LUT (Hesap Tablosunu) önceden hazırla.
        // Bu işlem nanosaniyeler sürdüğü için tek thread yeterli.
        Mat lut = createLut(value, isShadowAdjustment);

        // Sonuç için boş bir resim oluştur (Orijinal ile aynı boyutta)
        Mat destination = new Mat(source.size(), source.type());

        // 2. PARALEL İŞLEM BAŞLATIYORUZ
        // Resmi yatay şeritlere bölüp işleyeceğiz.
        ParallelProcessor.splitAndRun(source.rows(), (startRow, endRow) -> {

            // --- BURASI AYRI BİR THREAD (ÇEKİRDEK) İÇİNDE ÇALIŞIR ---

            // Sadece sorumlu olduğumuz alanı (ROI - Region of Interest) kesip alıyoruz.
            // submat veri kopyalamaz, sadece referans tutar (Hızlıdır).
            Mat srcSub = source.submat(startRow, endRow, 0, source.cols());
            Mat dstSub = destination.submat(startRow, endRow, 0, source.cols());

            // a. Renk uzayını değiştir (BGR -> HLS)
            Mat hlsSub = new Mat();
            Imgproc.cvtColor(srcSub, hlsSub, Imgproc.COLOR_BGR2HLS);

            // b. Kanalları ayır
            List<Mat> channels = new ArrayList<>();
            Core.split(hlsSub, channels);

            // c. LUT'u sadece L (Lightness - 1. kanal) kanalına uygula
            Core.LUT(channels.get(1), lut, channels.get(1));

            // d. Kanalları birleştir
            Core.merge(channels, hlsSub);

            // e. Tekrar BGR'ye çevirip hedef (dstSub) matrisine yaz
            Imgproc.cvtColor(hlsSub, dstSub, Imgproc.COLOR_HLS2BGR);

            // f. Geçici hafızayı temizle (Memory Leak önlemi)
            hlsSub.release();
            for (Mat m : channels) m.release();
            // srcSub ve dstSub release edilmemeli, onlar ana resmin parçası.
        });

        lut.release(); // İş bitince tablosunu temizle
        return destination;
    }

    // LUT oluşturma mantığını ayırdık (Eski kodun aynısı)
    private static Mat createLut(double value, boolean isShadowAdjustment) {
        Mat lut = new Mat(1, 256, CvType.CV_8U);
        byte[] lutData = new byte[256];

        for (int i = 0; i < 256; i++) {
            double original = i;
            double modified;

            if (isShadowAdjustment) {
                double factor = (1.0 - (original / 255.0));
                modified = original + (value * factor);
            } else {
                double factor = (original / 255.0);
                modified = original - (value * factor);
            }

            modified = Math.max(0, Math.min(255, modified));
            lutData[i] = (byte) modified;
        }
        lut.put(0, 0, lutData);
        return lut;
    }
}