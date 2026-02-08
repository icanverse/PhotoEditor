package photoeditor.MaskProcessors;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

public class MaskRefiner {

    /**
     * AI maskesini Sobel kenar algılama ile rafine eder.
     * @param aiMask AI modelinden gelen ham maske
     * @param originalImage Orijinal renkli resim
     * @return Kenarlara oturtulmuş yeni Mask nesnesi
     */
    public static Mask refineWithEdges(Mask aiMask, Mat originalImage) {
        int width = originalImage.width();
        int height = originalImage.height();

        // 1. ADIM: Orijinal resmi gri tonlamaya çevir ve gürültüyü azalt
        Mat gray = new Mat();
        Imgproc.cvtColor(originalImage, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.GaussianBlur(gray, gray, new Size(3, 3), 0);

        // 2. ADIM: Sobel Gradyanlarını Hesapla
        Mat gradX = new Mat();
        Mat gradY = new Mat();
        Mat absGradX = new Mat();
        Mat absGradY = new Mat();

        // X ve Y yönündeki türevler
        Imgproc.Sobel(gray, gradX, CvType.CV_16S, 1, 0, 3, 1, 0, Core.BORDER_DEFAULT);
        Imgproc.Sobel(gray, gradY, CvType.CV_16S, 0, 1, 3, 1, 0, Core.BORDER_DEFAULT);

        // Mutlak değerleri al ve 8-bit'e çevir
        Core.convertScaleAbs(gradX, absGradX);
        Core.convertScaleAbs(gradY, absGradY);

        // Gradyan Büyüklüğünü Birleştir (Edge Map)
        Mat edgeMap = new Mat();
        Core.addWeighted(absGradX, 0.5, absGradY, 0.5, 0, edgeMap);

        // 3. ADIM: AI Maskesini Mat formatına çevir
        Mat maskMat = convertToMat(aiMask);

        // 4. ADIM: Hibrit İyileştirme (Edge-Aware Refinement)
        // Maskenin kenar bölgelerini (0.1 - 0.9 arası) gerçek kenarlara çekiyoruz
        byte[] maskData = new byte[width * height];
        byte[] edgeData = new byte[width * height];
        maskMat.get(0, 0, maskData);
        edgeMap.get(0, 0, edgeData);

        for (int i = 0; i < maskData.length; i++) {
            int mVal = maskData[i] & 0xFF;
            int eVal = edgeData[i] & 0xFF;

            // Eğer maske "belirsiz" bir bölgedeyse ve orada güçlü bir kenar varsa
            if (mVal > 0 && mVal < 255) {
                if (eVal > 80) { // Kenar eşik değeri
                    // Maskeyi kenarın gücüne göre 255'e (beyaz) yaklaştır
                    int refined = Math.min(255, mVal + eVal);
                    maskData[i] = (byte) refined;
                }
            }
        }

        maskMat.put(0, 0, maskData);

        // 5. ADIM: Sonucu Mask nesnesine geri dönüştür
        Mask refinedMask = new Mask(width, height);
        refinedMask.setFromMat(maskMat);

        // Belleği temizle
        gray.release(); gradX.release(); gradY.release();
        absGradX.release(); absGradY.release(); edgeMap.release();
        maskMat.release();

        return refinedMask;
    }

    private static Mat convertToMat(Mask mask) {
        Mat m = new Mat(mask.getHeight(), mask.getWidth(), CvType.CV_8UC1);
        byte[] data = new byte[mask.getWidth() * mask.getHeight()];
        for (int i = 0; i < data.length; i++) {
            int x = i % mask.getWidth();
            int y = i / mask.getWidth();
            data[i] = (byte) (mask.getIntensity(x, y) * 255);
        }
        m.put(0, 0, data);
        return m;
    }
}