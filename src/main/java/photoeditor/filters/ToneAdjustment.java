package photoeditor.filters;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import photoeditor.utils.ParallelProcessor;

import java.util.ArrayList;
import java.util.List;

public class ToneAdjustment {

    /**
     * Gölgeleri Açar (+) veya Koyulaştırır (-)
     * Değer Aralığı: -100 ile +100 arası
     */
    public static Mat applyShadows(Mat source, double value) {
        return processParallel(source, value, true);
    }

    /**
     * Parlak Alanları Kısar (+) veya Patlatır (-)
     * Değer Aralığı: -100 ile +100 arası
     * Pozitif değer: Detay kurtarır (Recovery). Negatif değer: Parlatır.
     */
    public static Mat applyHighlights(Mat source, double value) {
        return processParallel(source, value, false);
    }

    /**
     * Akıllı Canlılık (Vibrance) Ayarı
     * Değer Aralığı: -100 (Gri tonlama) ile +100 (Süper canlı) arası
     */
    public static Mat applyVibrance(Mat source, double value) {
        Mat lut = createVibranceLut(value);
        Mat destination = new Mat(source.size(), source.type());

        ParallelProcessor.splitAndRun(source.rows(), (startRow, endRow) -> {
            Mat srcSub = source.submat(startRow, endRow, 0, source.cols());
            Mat dstSub = destination.submat(startRow, endRow, 0, source.cols());

            Mat hsvSub = new Mat();
            Imgproc.cvtColor(srcSub, hsvSub, Imgproc.COLOR_BGR2HSV);

            List<Mat> channels = new ArrayList<>();
            Core.split(hsvSub, channels);

            // Sadece Saturation (1. Kanal) üzerine LUT uygula
            Core.LUT(channels.get(1), lut, channels.get(1));

            Core.merge(channels, hsvSub);
            Imgproc.cvtColor(hsvSub, dstSub, Imgproc.COLOR_HSV2BGR);

            hsvSub.release();
            for (Mat m : channels) m.release();
        });

        lut.release();
        return destination;
    }

    private static Mat createVibranceLut(double value) {
        Mat lut = new Mat(1, 256, CvType.CV_8U);
        byte[] lutData = new byte[256];

        // Giriş değerini -1.0 ile 1.0 arasına normalize et
        double strength = value / 100.0;

        for (int i = 0; i < 256; i++) {
            double originalSat = i;
            double satScale = originalSat / 255.0; // 0.0 - 1.0 arası normalizasyon
            double modifiedSat;

            if (strength >= 0) {
                // POZİTİF (CANLILIK ARTIRMA)
                // İyileştirme: 128.0 sabitini kaldırdık.
                // Doygunluğu zaten yüksek olanları koru, düşük olanları artır.
                // Formül: Sat + (DoymamışlıkPayı * Güç * Esneklik)

                // Buradaki 60.0 katsayısı "128"e göre daha yumuşaktır, patlamayı önler.
                double boostAmount = strength * (1.0 - satScale) * 60.0;
                modifiedSat = originalSat + boostAmount;
            } else {
                // NEGATİF (SOLUKLAŞTIRMA)
                // strength -1.0 ise tamamen siyah beyaz olur.
                // strength -0.5 ise renkler yarı yarıya azalır.
                modifiedSat = originalSat * (1.0 + strength);
            }

            // Çakılmayı önle (Clamp 0-255)
            modifiedSat = Math.max(0, Math.min(255, modifiedSat));
            lutData[i] = (byte) modifiedSat;
        }
        lut.put(0, 0, lutData);
        return lut;
    }

    private static Mat processParallel(Mat source, double value, boolean isShadowAdjustment) {
        Mat lut = createLut(value, isShadowAdjustment);
        Mat destination = new Mat(source.size(), source.type());

        ParallelProcessor.splitAndRun(source.rows(), (startRow, endRow) -> {
            Mat srcSub = source.submat(startRow, endRow, 0, source.cols());
            Mat dstSub = destination.submat(startRow, endRow, 0, source.cols());

            Mat hlsSub = new Mat();
            Imgproc.cvtColor(srcSub, hlsSub, Imgproc.COLOR_BGR2HLS);

            List<Mat> channels = new ArrayList<>();
            Core.split(hlsSub, channels);

            // LUT sadece Lightness (1. Kanal) üzerine uygulanır
            Core.LUT(channels.get(1), lut, channels.get(1));

            Core.merge(channels, hlsSub);
            Imgproc.cvtColor(hlsSub, dstSub, Imgproc.COLOR_HLS2BGR);

            hlsSub.release();
            for (Mat m : channels) m.release();
        });

        lut.release();
        return destination;
    }

    private static Mat createLut(double value, boolean isShadowAdjustment) {
        Mat lut = new Mat(1, 256, CvType.CV_8U);
        byte[] lutData = new byte[256];

        // Kullanıcı arayüzünden -100 ile +100 arası değer geldiğini varsayıyoruz.
        // Ancak formülde çok sert etki olmaması için bunu ölçekleyelim.
        // Örneğin max etki +/- 50 birim parlaklık değişimi olsun.
        double adjustment = value * 0.5;

        for (int i = 0; i < 256; i++) {
            double original = i;
            double modified = original;

            if (isShadowAdjustment) {
                // --- GÖLGE AYARLARI ---
                // Hedef: Koyu pikseller (0'a yakın olanlar)
                // Factor: Piksel ne kadar koyuysa, etki o kadar büyük olur.
                double factor = (1.0 - (original / 255.0));

                // Eğer value pozitifse: adjustment (+) -> Parlaklık eklenir (Gölge açılır)
                // Eğer value negatifse: adjustment (-) -> Parlaklık çıkarılır (Gölge koyulaşır)
                modified = original + (adjustment * factor);

            } else {
                // --- PARLAKLIK (HIGHLIGHT) AYARLARI ---
                // Hedef: Parlak pikseller (255'e yakın olanlar)
                // Factor: Piksel ne kadar parlaksa, etki o kadar büyük olur.
                double factor = (original / 255.0);

                // Highlight mantığı genellikle "Recovery" (kurtarma) üzerine kuruludur.
                // Pozitif değer (+): Parlak alanları KISAR (Detail Recovery).
                // Negatif değer (-): Parlak alanları ARTIRIR (Glow/Bloom etkisi).
                modified = original - (adjustment * factor);
            }

            // 0-255 Sınırla
            modified = Math.max(0, Math.min(255, modified));
            lutData[i] = (byte) modified;
        }
        lut.put(0, 0, lutData);
        return lut;
    }
}