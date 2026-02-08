package photoeditor.filters;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import photoeditor.core.ImageAnalysis; // Analiz sınıfını import ettik
import photoeditor.utils.ColorSpaceConverter;

import java.util.ArrayList;
import java.util.List;

public class BasicFilters {

    /// Parlaklık ve Kontrast
    public static Mat adjustBrightnessContrast(Mat source, double brightness, double contrast) {
        Mat destination = new Mat();

        // 1. KONTRAST HESABI (Alpha)
        // Slider 0 iken alpha = 1.0 olmalı.
        // Slider 100 iken alpha = 2.0 (veya 3.0) olmalı.
        // Slider -100 iken alpha = 0.0 olmalı.
        double alpha;
        if (contrast >= 0) {
            // 0..100 arası -> 1.0..2.0 arası değişir
            alpha = 1.0 + (contrast / 100.0);
        } else {
            // -100..0 arası -> 0.0..1.0 arası değişir
            // Örn: -50 gelirse -> 1.0 + (-0.5) = 0.5 (Yarı kontrast)
            alpha = 1.0 + (contrast / 100.0);
        }

        // 2. PARLAKLIK HESABI (Beta)
        // Slider değerini doğrudan beta olarak kullanabiliriz ama
        // 0-255 skalasında biraz daha etkili olsun diye 1.2 ile çarptım.
        // -100 -> -120 (Koyu), +100 -> +120 (Parlak)
        double beta = brightness * 1.2;

        // 3. UYGULAMA
        // OpenCV Formülü: Pixel_Yeni = (Pixel_Eski * alpha) + beta
        try {
            source.convertTo(destination, -1, alpha, beta);
        } catch (Exception e) {
            e.printStackTrace();
            return source.clone();
        }

        return destination;
    }

    /// Pozlama
    public static Mat adjustExposure(Mat source, double value) {
        Mat destination = new Mat();
        source.convertTo(destination, -1, value, 0);
        return destination;
    }

    /// SB Çevirme ::: Tek Kanallı Görsele Uygulanamaz
    public static Mat adjustGrayscale(Mat source, double value) {
        // 1. Performans: Değer 0'a çok yakınsa işlem yapma, orijinali dön.
        if (Math.abs(value) < 1.0) {
            return source.clone();
        }

        // 2. Performans: Eğer tam +100 ise en hızlı yöntem olan klasik dönüşümü yap.
        if (value >= 100.0) {
            Mat gray = new Mat();
            Mat result = new Mat();
            // BGR -> GRAY -> BGR (Gri tonlamalı ama 3 kanallı format)
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.cvtColor(gray, result, Imgproc.COLOR_GRAY2BGR);
            gray.release(); // Temizlik
            return result;
        }

        Mat hsv = new Mat();
        Mat destination = new Mat();

        try {
            // --- MANTIK: HSV FORMATINA GEÇİŞ ---
            // Siyah-Beyaz dengesi aslında "Saturation" (Doygunluk) kanalıyla oynanarak yapılır.
            Imgproc.cvtColor(source, hsv, Imgproc.COLOR_BGR2HSV);

            List<Mat> channels = new ArrayList<>();
            Core.split(hsv, channels); // Kanalları ayır: 0:Hue, 1:Saturation, 2:Value

            // --- HESAPLAMA ---
            // value +100 ise -> scale = 0.0 (Renk yok, Gri)
            // value 0 ise    -> scale = 1.0 (Orijinal)
            // value -100 ise -> scale = 2.0 (İki kat renkli)
            double scale = 1.0 - (value / 100.0);

            // Sadece Saturation (1. Kanal) üzerinde çarpma işlemi yapıyoruz.
            // convertTo fonksiyonu, çıkan sonucu otomatik olarak 0-255 arasına sığdırır (clamp).
            // Bu işlem C++ tarafında optimize edildiği için loop kurmaktan daha hızlıdır.
            channels.get(1).convertTo(channels.get(1), -1, scale, 0);

            // --- BİRLEŞTİRME ---
            Core.merge(channels, hsv);
            Imgproc.cvtColor(hsv, destination, Imgproc.COLOR_HSV2BGR);

            // Kanalları temizle
            for (Mat m : channels) m.release();

        } catch (Exception e) {
            e.printStackTrace();
            return source.clone();
        } finally {
            // Ana geçici matrisi temizle
            if (hsv != null) hsv.release();
        }

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
    public static Mat applySharpness(Mat source, double value) {
        // İşlem yoksa kopyasını döndür
        if (Math.abs(value) < 1.0) {
            return source.clone();
        }

        Mat destination = new Mat();
        Mat blurred = new Mat();

        try {
            // Keskinleştirme işlemi "ince detaylar" ile ilgilidir.
            // Bu yüzden Clarity'nin aksine burada çekirdek (kernel) boyutu sabit ve küçük tutulur.
            // Size(0,0) verip sigmaX=3 vermek standart bir yaklaşımdır.
            Imgproc.GaussianBlur(source, blurred, new Size(0, 0), 3);

            if (value > 0) {
                // --- POZİTİF: KESKİNLEŞTİRME (Sharpen) ---
                // Formül: Orijinal + (Orijinal - Bulanık) * Güç
                // Matematik: Orijinal * (1 + Güç) + Bulanık * (-Güç)

                double strength = value / 100.0; // 0.0 - 1.0 arası

                // addWeighted, piksel taşmalarını (0-255) otomatik halleder.
                Core.addWeighted(source, 1.0 + strength, blurred, -strength, 0, destination);
            } else {
                // --- NEGATİF: BULANIKLAŞTIRMA (Blur) ---
                // Orijinal resim ile bulanık resmi karıştırıyoruz.
                // -100'e yaklaştıkça bulanık resim baskın hale gelir.

                double strength = Math.abs(value) / 100.0; // 0.0 - 1.0 arası

                // Orijinal resim azalır, bulanık resim artar
                Core.addWeighted(source, 1.0 - strength, blurred, strength, 0, destination);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return source.clone();
        } finally {
            // Hafıza Temizliği
            if (blurred != null) blurred.release();
        }

        return destination;
    }

    /// Netlik (Clarity)
    public static Mat adjustClarity(Mat source, double value) {
        // 1. İşlem yoksa kopyasını döndür (Hız optimizasyonu)
        if (Math.abs(value) < 1.0) {
            return source.clone();
        }

        Mat destination = new Mat();
        Mat blurred = new Mat();

        try {
            // --- ADIM 1: BULANIK KATMAN OLUŞTURMA (Her iki durum için gerekli) ---

            // Sigma değeri ne kadar yüksekse "yarıçap" o kadar artar.
            // Clarity efekti için genelde biraz geniş bir yarıçap iyidir.
            // Değer arttıkça etki alanı genişlesin diye dinamik bir sigma kullanıyoruz.
            double sigma = Math.max(3.0, Math.abs(value) / 5.0);

            // GaussianBlur ile "Low Frequency" (Detaysız) görüntüyü al
            Imgproc.GaussianBlur(source, blurred, new Size(0, 0), sigma);

            if (value > 0) {
                // --- POZİTİF: KESKİNLEŞTİRME (Unsharp Masking) ---
                // Mantık: Orijinal resimden bulanık resmi çıkarırsan geriye "kenarlar" kalır.
                // Bu kenarları orijinal resmin üzerine eklersin.
                // Formül: Src + (Src - Blur) * Amount
                // Matematiksel Sadeleştirme: Src * (1 + Amount) + Blur * (-Amount)

                double strength = value / 100.0; // 0.0 ile 1.0 arası

                // addWeighted(src1, alpha, src2, beta, gamma, dst)
                Core.addWeighted(source, 1.0 + strength, blurred, -strength, 0, destination);

            } else {
                // --- NEGATİF: YUMUŞATMA (Softening / Bloom) ---
                // Mantık: Orijinal resim ile bulanık resmi karıştır (Blend).
                // Sola çektikçe bulanık resmin baskınlığı artar.

                double strength = Math.abs(value) / 100.0; // 0.0 ile 1.0 arası

                // Orijinal resim azalır (1.0 - strength), Bulanık resim artar (strength)
                Core.addWeighted(source, 1.0 - strength, blurred, strength, 0, destination);
            }

        } catch (Exception e) {
            e.printStackTrace();
            // Hata durumunda boş dönmemek için orijinali kopyala
            return source.clone();
        } finally {
            // --- BELLEK TEMİZLİĞİ (Memory Leak Prevention) ---
            // 'blurred' matrisi sadece ara işlemdi, işimiz bitti, siliyoruz.
            if (blurred != null) {
                blurred.release();
            }
            // 'destination' return edileceği için silinmez.
            // 'source' parametre olduğu için dokunulmaz.
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