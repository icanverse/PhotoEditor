package photoeditor.filters;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import photoeditor.utils.ParallelProcessor;
import photoeditor.utils.TiledProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class ArtisticFilters {

    private static final Mat cachedSmallLayer = new Mat();

    /**
     * Pikselleştirme Efekti (CPU Parallel)
     * Java döngüleri ile yapıldığı için ParallelProcessor kullanmak performansı artırır.
     * @param source    İşlenecek resim
     * @param pixelSize Karelerin boyutu
     */

    public static void applyPixelate(Mat source, int pixelSize) {
        if (pixelSize < 2) return;

        int rows = source.rows();
        int cols = source.cols();

        ParallelProcessor.splitAndRun(rows, (startRow, endRow) -> {
            int alignedStartRow = (startRow / pixelSize) * pixelSize;
            if (alignedStartRow >= rows) return;

            for (int y = alignedStartRow; y < endRow; y += pixelSize) {
                for (int x = 0; x < cols; x += pixelSize) {

                    // Resim sınırlarını aşmamak için kontrol
                    int blockHeight = Math.min(pixelSize, rows - y);
                    int blockWidth = Math.min(pixelSize, cols - x);

                    Rect rect = new Rect(x, y, blockWidth, blockHeight);
                    Mat blockROI = source.submat(rect);
                    Scalar meanColor = Core.mean(blockROI);
                    Imgproc.rectangle(source, rect, meanColor, -1);
                    blockROI.release();
                }
            }
        });
    }

    /**
     * Sepya Efekti (Native Parallel)
     * @param source    İşlenecek resim
     */
    public static void applySepia(Mat source) {
        Mat sepiaKernel = new Mat(3, 3, CvType.CV_32F);
        // BGR sırasına göre Sepya katsayıları
        sepiaKernel.put(0, 0, 0.272, 0.534, 0.131);
        sepiaKernel.put(1, 0, 0.349, 0.686, 0.168);
        sepiaKernel.put(2, 0, 0.393, 0.769, 0.189);

        Mat destination = new Mat();
        // Native optimize dönüşüm
        Core.transform(source, destination, sepiaKernel);

        // Sonucu orijinal matrise geri kopyala (In-place modification)
        destination.copyTo(source);

        // Bellek temizliği
        destination.release();
        sepiaKernel.release();
    }

    /**
     * Vignette (Kenar Karartma veya Beyazlatma)
     * @param source    İşlenecek resim
     * @param intensity Vinyet şiddeti.
     * Pozitif (+) değerler köşeleri KARARTIR.
     * Negatif (-) değerler köşeleri BEYAZLATIR.
     */
    public static void applyVignette(Mat source, double intensity) {
        // 0 kontrolü: Etki yoksa işlem yapma
        if (Math.abs(intensity) < 0.001) return;

        int rows = source.rows();
        int cols = source.cols();

        // 1. Sigma hesaplarken mutlak değer al (Negatif sigma olamaz)
        double k = Math.abs(intensity);
        double sigmaX = cols / (1.5 * k);
        double sigmaY = rows / (1.5 * k);

        // 2. Kernel oluşturma
        Mat kernelX = Imgproc.getGaussianKernel(cols, sigmaX, CvType.CV_32F);
        Mat kernelY = Imgproc.getGaussianKernel(rows, sigmaY, CvType.CV_32F);

        Mat kernel = new Mat();
        Core.gemm(kernelY, kernelX.t(), 1, new Mat(), 0, kernel);
        Core.normalize(kernel, kernel, 0, 1, Core.NORM_MINMAX);

        // Maskeyi 3 kanala çıkar (Renkli resim için)
        Mat mask = new Mat();
        List<Mat> maskChannels = Arrays.asList(kernel, kernel, kernel); // ArrayList yerine Arrays.asList daha pratiktir
        Core.merge(maskChannels, mask);

        // İşlem için float'a çevir
        Mat sourceFloat = new Mat();
        source.convertTo(sourceFloat, CvType.CV_32F);

        Mat result = new Mat();

        if (intensity > 0) {
            // --- POZİTİF: SİYAH VİNYET ---
            // Formül: Source * Mask
            // Maske merkezde 1 (aynen kalır), köşelerde 0 (siyahlaşır)
            Core.multiply(sourceFloat, mask, result);
        } else {
            // --- NEGATİF: BEYAZ VİNYET ---
            // Formül: Source * Mask + White * (1 - Mask)
            // Mantık: Resmin orijinalliğini koru ama maskenin azaldığı yerleri beyaza tamamla.

            Mat inverseMask = new Mat();
            Mat ones = new Mat(mask.size(), mask.type(), new Scalar(1.0, 1.0, 1.0));

            // 1 - Mask işlemini yap (Ters maske: Merkez 0, Köşeler 1)
            Core.subtract(ones, mask, inverseMask);

            // Köşeleri beyaz yapmak için: (InverseMask * 255)
            // 32F formatında beyaz 255.0'dır.
            Mat whitePart = new Mat();
            Core.multiply(inverseMask, new Scalar(255, 255, 255), whitePart);

            // Orijinal resmin maskelenmiş hali (Merkez net, köşeler karanlık)
            Mat sourcePart = new Mat();
            Core.multiply(sourceFloat, mask, sourcePart);

            // İkisini topla: (Merkez Resim + Köşeler Beyaz)
            Core.add(sourcePart, whitePart, result);

            // Temizlik
            inverseMask.release();
            ones.release();
            whitePart.release();
            sourcePart.release();
        }

        // Sonucu kaynağa geri yaz
        result.convertTo(source, CvType.CV_8UC3);

        // Genel Temizlik
        kernelX.release();
        kernelY.release();
        kernel.release();
        mask.release();
        sourceFloat.release();
        result.release();
    }
    /**
     * Resme Gaussian Blur (Bulanıklık) uygular.
     * @param source Kaynak matris
     * @param sigma Bulanıklık şiddeti (1 ile 50 arası idealdir)
     */
    public static void applyBlur(Mat source, double sigma) {
        // Padding = Kernel Yarıçapı
        int radius = (int) Math.ceil(sigma * 2.5);
        int kernelSize = radius * 2 + 1;

        // Yeni bir destination oluştur (Source bozulmasın diye)
        Mat destination = new Mat();

        TiledProcessor.apply(source, destination, radius, (inputChunk, outputChunk) -> {
            Imgproc.GaussianBlur(inputChunk, outputChunk, new Size(kernelSize, kernelSize), sigma);
        });

        // Sonucu kaynağa geri kopyalar ve Temizlik
        destination.copyTo(source);
        destination.release();
    }

    /**
     * Bulanıklığı küçültüp uygular
     * */
     public static void applyBlur_forStream(Mat source, double sigma, boolean highQuality) {
        if (highQuality) {
            applyBlur(source, sigma);
            return;
        }

        double targetHeight = 144.0;

        if (source.rows() <= targetHeight) {
            int kSize = (int) Math.ceil(sigma * 2.5) | 1;
            if(kSize < 1) kSize = 1;
            Imgproc.GaussianBlur(source, source, new Size(kSize, kSize), sigma);
            return;
        }

        double scale = targetHeight / (double) source.rows();

        // Küçült (Downscale)
        Imgproc.resize(source, cachedSmallLayer, new Size(), scale, scale, Imgproc.INTER_NEAREST);

        // Blur
        double scaledSigma = sigma * scale;
        int kSize = (int) Math.ceil(scaledSigma * 2.5) | 1;
        if (kSize < 1) kSize = 1;

        Imgproc.GaussianBlur(cachedSmallLayer, cachedSmallLayer, new Size(kSize, kSize), scaledSigma);

        // Büyüt (Upscale)
        Imgproc.resize(cachedSmallLayer, source, source.size(), 0, 0, Imgproc.INTER_NEAREST);
    }

    /**
     * Görselin küçük halini döndürür.
     * Büyütme işini UI (Android ImageView veya JavaFX) GPU kullanarak yapmalıdır.
     */
    public static Mat applyBlur_Fast_toPreview(Mat source, double sigma) {
        double targetHeight = 144.0;

        // Resim zaten küçükse direkt blur
        if (source.rows() <= targetHeight) {
            Mat result = source.clone();
            int kSize = (int) Math.ceil(sigma * 2.5) | 1;
            if(kSize < 1) kSize = 1;
            Imgproc.GaussianBlur(result, result, new org.opencv.core.Size(kSize, kSize), sigma);
            return result;
        }

        double scale = targetHeight / (double) source.rows();
        Mat smallPreview = new Mat();

        // Nearest Neighbor (En hızlı küçültme)
        // Kalite kaybı blur efekti sayesinde maskelenir.
        Imgproc.resize(source, smallPreview, new org.opencv.core.Size(), scale, scale, Imgproc.INTER_NEAREST);

        // Sigma ölçekleme
        double scaledSigma = sigma * scale;
        int kSize = (int) Math.ceil(scaledSigma * 2.5) | 1;
        if (kSize < 1) kSize = 1;

        // Blur
        Imgproc.GaussianBlur(smallPreview, smallPreview, new org.opencv.core.Size(kSize, kSize), scaledSigma);

        return smallPreview;
    }

    /**
     * Resme Hareket Bulanıklığı (Motion Blur) uygular.
     * Sanki fotoğraf makinesi hareket ederken çekilmiş gibi bir etki yaratır.
     *
     * @param source Kaynak matris (Üzerine yazar - In Place)
     * @param size   Hareketin büyüklüğü (Kernel Size). Örn: 15, 30. Ne kadar büyükse o kadar hızlı görünür.
     * @param angle  Hareketin açısı (Derece cinsinden). 0 = Yatay, 90 = Dikey.
     */
    public static void applyMotionBlur(Mat source, int size, double angle) {
        // Güvenlik: Boyut çok küçükse işlem yapma
        if (size < 2) return;

        // Kernel boyutu tek sayı olmalı
        if (size % 2 == 0) size++;

        // --- 1. ADIM: Motion Blur Kernel'ini Oluştur ---
        // Bu işlem ağırdır, o yüzden TiledProcessor'a girmeden SADECE BİR KERE yapıyoruz.
        Mat kernel = createMotionKernel(size, angle);

        // --- 2. ADIM: Paralel İşleme (TiledProcessor) ---
        // Padding değeri kernel boyutunun yarısı kadar olmalı ki kenarlarda siyahlık oluşmasın.
        int padding = size / 2;

        // Kaynak bozulmasın diye geçici hedef oluştur
        Mat destination = new Mat();

        // Lambda içinde dışarıdaki 'kernel' nesnesini kullanıyoruz
        photoeditor.utils.TiledProcessor.apply(source, destination, padding, (srcTile, dstTile) -> {
            // filter2D: Özel kernel ile konvolüsyon işlemi yapar
            // ddepth -1: Kaynakla aynı derinliği (CV_8U) koru demektir.
            Imgproc.filter2D(srcTile, dstTile, -1, kernel);
        });

        // --- 3. ADIM: Sonucu Kaydet ve Temizlik ---
        destination.copyTo(source);
        destination.release();
        kernel.release();
    }

    /**
     * Yardımcı Metot: Belirtilen açı ve boyutta bir çizgi (Kernel) oluşturur.
     */
    private static Mat createMotionKernel(int size, double angle) {
        // 1. Boş bir kare matris oluştur (Siyah)
        Mat kernel = Mat.zeros(size, size, org.opencv.core.CvType.CV_32F);

        // 2. Açıyı radyana çevir
        double angleRad = Math.toRadians(angle);

        // 3. Merkez noktayı bul
        int center = size / 2;

        // 4. Çizginin başlangıç ve bitiş noktalarını hesapla (Trigonometri)
        // Cosinüs yatay, Sinüs dikey bileşeni verir.
        int xStart = (int) Math.round(center - (size / 2.0) * Math.cos(angleRad));
        int yStart = (int) Math.round(center - (size / 2.0) * Math.sin(angleRad));
        int xEnd = (int) Math.round(center + (size / 2.0) * Math.cos(angleRad));
        int yEnd = (int) Math.round(center + (size / 2.0) * Math.sin(angleRad));

        // 5. Matrisin üzerine beyaz bir çizgi çiz
        org.opencv.core.Point p1 = new org.opencv.core.Point(xStart, yStart);
        org.opencv.core.Point p2 = new org.opencv.core.Point(xEnd, yEnd);
        Imgproc.line(kernel, p1, p2, new org.opencv.core.Scalar(1.0), 1);

        // 6. Normalizasyon (Kernelin toplamı 1 olmalı, yoksa resim çok parlar veya kararır)
        org.opencv.core.Scalar sum = org.opencv.core.Core.sumElems(kernel);
        if (sum.val[0] > 0) {
            org.opencv.core.Core.divide(kernel, sum, kernel);
        }

        return kernel;
    }


    /**
     * Resme Median Blur (Gürültü Giderici) uygular.
     * @param source Kaynak matris
     * @param kernelSize Filtre boyutu (Mutlaka tek sayı olmalıdır: 3, 5, 7...)
     */
    public static void applyMedianBlur(Mat source, int kernelSize) {
        // Kernel boyutu kontrolü: OpenCV kuralı gereği tek sayı olmalı
        if (kernelSize % 2 == 0) {
            kernelSize++; // Çift ise bir artırıp tek yap
        }

        // Çok küçükse işlem yapma
        if (kernelSize < 1) kernelSize = 1;

        // Padding Hesaplama
        // Median blur, kernel boyutunun yarısı kadar komşuya bakar.
        // Örn: Kernel 5 ise, merkezden 2 piksel sağa/sola bakar. Padding = 2 olmalı.
        int padding = kernelSize / 2;

        // Geçici Hedef Matris (Paralel işlem için güvenli alan)
        Mat destination = new Mat();

        // TiledImageProcessor sınıfımızı çağırıyoruz
        final int finalKSize = kernelSize;

        TiledProcessor.apply(source, destination, padding, (inputChunk, outputChunk) -> {
            // --- Burası her çekiredekte ayrı çalışır ---
            Imgproc.medianBlur(inputChunk, outputChunk, finalKSize);
        });

        // Sonucu ana kaynağa geri yaz ve temizle
        destination.copyTo(source);
        destination.release();
    }

}