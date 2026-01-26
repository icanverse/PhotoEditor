package photoeditor;

import photoeditor.MaskProcessors.Mask;
import photoeditor.MaskProcessors.MaskFilters;
import photoeditor.core.ImageProcessor;
import photoeditor.utils.ImageUtils;
import photoeditor.utils.ImageViewer;
import photoeditor.utils.LibraryLoader;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

public class Main {
    public static void main(String[] args) {
        LibraryLoader.load();

        // 1. Orijinal Resmi Yükle (Mat olarak)
        Mat rawImage = Imgcodecs.imread("src/test/resources/blonde.jpg");
        if (rawImage.empty()) {
            System.out.println("Resim bulunamadı!");
            return;
        }

        // 2. Filtreleme İşlemi
        // Processor byte[] istiyor, verelim ama sonucu tekrar Mat'a çevireceğiz.
        byte[] inputBytes = ImageUtils.matToBytes(rawImage);
        ImageProcessor processor = new ImageProcessor(inputBytes);

        byte[] filteredBytes = processor
                .addSaturation(0.0) // Siyah Beyaz Efekti
                .process();

        // KRİTİK ADIM: Sonucu tekrar Mat nesnesine (Piksel Matrisine) çeviriyoruz.
        // Böylece sıkıştırılmış dosya boyutu farkından kurtuluyoruz.
        Mat filteredImage = ImageUtils.bytesToMat(filteredBytes);

        // Boyut kontrolü (Artık piksel bazında kontrol ediyoruz, hata vermemesi lazım)
        if (rawImage.rows() != filteredImage.rows() || rawImage.cols() != filteredImage.cols()) {
            System.err.println("Boyut uyuşmazlığı devam ediyor! Resize gerekebilir.");
            return;
        }

        int width = rawImage.cols();
        int height = rawImage.rows();

        // 3. Maske Oluştur (Fırça Testi)
        System.out.println("Maske oluşturuluyor...");
        Mask brushMask = new Mask(width, height);

        // Ortaya bir fırça darbesi
        brushMask.addBrushStroke(width / 2, height / 2, 50, 0.5f);
        // Biraz sağa ikinci fırça darbesi
        brushMask.addBrushStroke((width / 2) + 200, height / 2, 250, 0.8f);

        // 4. Maskeyi Matrisler Üzerinde Uygula
        long startTime = System.currentTimeMillis();

        // Yeni metodumuzu kullanıyoruz
        Mat finalImage = applyMaskToMat(rawImage, filteredImage, brushMask);

        long endTime = System.currentTimeMillis();
        System.out.println("Maske Uygulama Süresi: " + (endTime - startTime) + " ms");

        // 5. Sonucu Göster
        ImageViewer.show(finalImage, "Maskeli Sonuç");
    }

    /**
     * İki OpenCV Mat nesnesini (Resmi) maske değerine göre birleştirir.
     */
    private static Mat applyMaskToMat(Mat original, Mat filtered, Mask mask) {
        // Sonuç için boş bir Mat oluştur (Orijinal ile aynı boyutta ve tipte)
        Mat result = new Mat(original.rows(), original.cols(), original.type());

        int rows = original.rows();
        int cols = original.cols();
        int channels = original.channels(); // Genelde 3 (BGR)

        // Veriye hızlı erişim için byte dizilerine döküyoruz (Ama bu sefer decode edilmiş ham veri)
        byte[] orgBuff = new byte[rows * cols * channels];
        byte[] filtBuff = new byte[rows * cols * channels];
        byte[] resBuff = new byte[rows * cols * channels];

        original.get(0, 0, orgBuff);
        filtered.get(0, 0, filtBuff);

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                float maskVal = mask.getIntensity(x, y);
                int index = (y * cols + x) * channels;

                for (int c = 0; c < channels; c++) {
                    // Byte, Java'da işaretlidir (-128..127). & 0xFF ile 0..255 arasına çekeriz.
                    double valOrg = orgBuff[index + c] & 0xFF;
                    double valFilt = filtBuff[index + c] & 0xFF;

                    // Formül: Org * (1-mask) + Filt * mask
                    double blended = valOrg * (1.0 - maskVal) + valFilt * maskVal;

                    resBuff[index + c] = (byte) blended;
                }
            }
        }

        // Hesaplanmış buffer'ı sonuç Mat'ına koy
        result.put(0, 0, resBuff);
        return result;
    }
}