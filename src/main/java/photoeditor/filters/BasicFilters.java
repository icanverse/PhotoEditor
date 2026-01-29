package photoeditor.filters;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import photoeditor.core.ImageAnalysis; // Analiz sınıfını import ettik
import photoeditor.utils.ColorSpaceConverter;
import photoeditor.filters.ToneAdjustment;

import java.util.ArrayList;
import java.util.List;

public class BasicFilters {

    /// Parlaklık ve Kontrast
    public static Mat adjustBrightnessContrast(Mat source, double alpha, double beta) {
        Mat destination = new Mat();
        source.convertTo(destination, -1, alpha, beta);
        return destination;
    }

    /// Pozlama
    public static Mat adjustExposure(Mat source, double value) {
        Mat destination = new Mat();
        source.convertTo(destination, -1, value, 0);
        return destination;
    }

    /// SB Çevirme ::: Tek Kanallı Görsele Uygulanamaz
    public static Mat toGrayscale(Mat source, ImageAnalysis analysis) {
        if (analysis != null && analysis.getChannels() == 1) {
            System.out.println(">> Bilgi: Resim zaten Siyah-Beyaz, işlem atlandı.");
            return source;
        }

        Mat destination = new Mat();
        Imgproc.cvtColor(source, destination, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(destination, destination, Imgproc.COLOR_GRAY2BGR); // Formatı koru
        return destination;
    }

    /// Doygunluk ::: SB Görsele Uygulanamaz
    public static Mat adjustSaturation(Mat source, ImageAnalysis analysis, double value) {
        if (analysis != null && analysis.getChannels() == 1) {
            System.out.println(">> Uyarı: Siyah-Beyaz resmin doygunluğu değiştirilemez!");
            return source;
        }

        Mat workingImage = ColorSpaceConverter.bgrToHsv(source);

        List<Mat> channels = new ArrayList<>();
        Core.split(workingImage, channels);

        channels.get(1).convertTo(channels.get(1), -1, value, 0);

        Core.merge(channels, workingImage);

        return ColorSpaceConverter.hsvToBgr(workingImage);
    }

    /// Keskinleştirme (Sharpening)
    public static Mat sharpen(Mat source, double amount) {
        Mat destination = new Mat();
        Mat blurred = new Mat();

        // Görseli hafifçe bulanıklaştır (Gürültüyü engellemek için)
        Imgproc.GaussianBlur(source, blurred, new org.opencv.core.Size(0, 0), 3);

        // Orijinal ve bulanık görüntüyü ağırlıklı olarak birleştir
        Core.addWeighted(source, 1.0 + amount, blurred, -amount, 0, destination);

        return destination;
    }

    /// Netlik (Clarity)
    public static Mat adjustClarity(Mat source, double sigma) {
        // Güvenlik Kontrolü: 0 veya negatifse orijinali döndür
        if (sigma <= 0) {
            return source.clone();
        }

        Mat destination = new Mat();
        Mat detailMask = new Mat();

        try {
            // Güvenlik Kontrolü: Minimum sigma değeri
            double safeSigma = Math.max(sigma, 0.5);

            // İşlemler
            Imgproc.GaussianBlur(source, detailMask, new org.opencv.core.Size(0, 0), safeSigma);
            Core.addWeighted(source, 1.5, detailMask, -0.5, 0, destination);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // ÇOK ÖNEMLİ: Ara işlemde kullanılan matris (detailMask) hafızadan silinmeli!
            // destination'ı silmiyoruz çünkü onu return ediyoruz (alan yer silecek).
            if (detailMask != null) {
                detailMask.release();
            }
        }

        return destination;
    }

    /**
     * Sıcaklık (Temperature)
     */
    public static Mat adjustTemperature(Mat source, double value) {
        Mat destination = new Mat();
        source.copyTo(destination);

        List<Mat> channels = new ArrayList<>();
        Core.split(destination, channels);

        // Kırmızı (2) ve Mavi (0) kanalları değiştirerek sıcaklık algısı yaratılır.
        if (value > 0) {
            // Isıt: Kırmızıyı artır, Maviyi azalt
            Core.add(channels.get(2), new Scalar(value), channels.get(2));
            Core.subtract(channels.get(0), new Scalar(value), channels.get(0));
        } else {
            // Soğut: Maviyi artır, Kırmızıyı azalt
            Core.add(channels.get(0), new Scalar(Math.abs(value)), channels.get(0));
            Core.subtract(channels.get(2), new Scalar(Math.abs(value)), channels.get(2));
        }

        Core.merge(channels, destination);
        return destination;
    }


}