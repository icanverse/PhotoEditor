package photoeditor.filters;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

public class DecorationFilters {

    /**
     * @param source    Kaynak resim
     * @param text      Yazılacak metin
     * @param x         X koordinatı (Soldan uzaklık)
     * @param y         Y koordinatı (Yukarıdan uzaklık - Metnin sol alt köşesi)
     * @param scale     Yazı boyutu (Örn: 1.0, 2.5)
     * @param color     Renk (Scalar(B, G, R))
     * @param thickness Yazı kalınlığı
     * @param fontFace  Yazı tipi
     */

    public static void addText(Mat source, String text, int x, int y, double scale, Scalar color, int thickness, int fontFace) {
        Point position = new Point(x, y);
        Imgproc.putText(source, text, position, fontFace, scale, color, thickness, Imgproc.LINE_AA);
    }

    public static void addCenteredText(Mat source, String text, double scale, Scalar color, int thickness, int fontFace) {
        // Metin boyutunu seçilen fonta göre hesapla
        int[] baseline = new int[1];
        Size textSize = Imgproc.getTextSize(text, fontFace, scale, thickness, baseline);

        double x = (source.cols() - textSize.width) / 2;
        double y = (source.rows() + textSize.height) / 2;

        Point position = new Point(x, y);
        Imgproc.putText(source, text, position, fontFace, scale, color, thickness, Imgproc.LINE_AA);
    }
    /**
     * Resmin üzerine şeffaflık ayarlı çıkartma ekler.
     * @param source    Ana resim
     * @param sticker   Eklenecek çıkartma
     * @param x         X konumu
     * @param y         Y konumu
     * @param width     Genişlik
     * @param height    Yükseklik
     * @param opacity   Şeffaflık (0.0 = Görünmez, 1.0 = Tam Görünür, 0.5 = Yarı Saydam)
     */
    public static void addSticker(Mat source, Mat sticker, int x, int y, int width, int height, double opacity) {
        // Opacity 0 ise hiç işlem yapma
        if (opacity <= 0.0) return;
        // Opacity 1'den büyükse 1'e çek
        if (opacity > 1.0) opacity = 1.0;

        // 1. Çıkartmayı boyutlandır
        Mat resizedSticker = new Mat();
        Imgproc.resize(sticker, resizedSticker, new Size(width, height));

        // 2. Güvenli Alan (ROI) Hesaplaması (Resim sınırları dışına taşmayı önler)
        // Bunu hem 3 kanallı hem 4 kanallı işlemler için ortak kullanacağız.
        int safeX = Math.max(0, x);
        int safeY = Math.max(0, y);
        int safeW = Math.min(width, source.cols() - safeX);
        int safeH = Math.min(height, source.rows() - safeY);

        if (safeW <= 0 || safeH <= 0) return; // Görünür alanda değilse çık

        // Alt matrisleri (Submat) al
        Mat subSrc = source.submat(safeY, safeY + safeH, safeX, safeX + safeW);
        // Sticker'ın kesilmesi gereken kısmı (Eğer x veya y negatifse sticker'ın başı kesilir)
        int stickX = safeX - x;
        int stickY = safeY - y;
        Mat subStk = resizedSticker.submat(stickY, stickY + safeH, stickX, stickX + safeW);

        // 3. Kanal Kontrolü
        if (resizedSticker.channels() != 4) {
            // Şeffaf kanal yoksa (JPG vb.), OpenCV'nin hızlı karıştırma fonksiyonunu kullan
            // addWeighted formülü: src1 * alpha + src2 * beta + gamma
            Core.addWeighted(subStk, opacity, subSrc, 1.0 - opacity, 0, subSrc);
            return;
        }

        // 4. Alpha Blending (Pixel-by-Pixel)
        int rows = subStk.rows();
        int cols = subStk.cols();
        int srcChannels = source.channels();
        int stkChannels = resizedSticker.channels();

        byte[] srcBuff = new byte[rows * cols * srcChannels];
        byte[] stkBuff = new byte[rows * cols * stkChannels];

        subSrc.get(0, 0, srcBuff);
        subStk.get(0, 0, stkBuff);

        for (int i = 0; i < srcBuff.length; i += srcChannels) {
            int stkIndex = (i / srcChannels) * stkChannels;

            // Sticker'ın kendi alpha değeri (0..255)
            double pixelAlpha = (stkBuff[stkIndex + 3] & 0xFF) / 255.0;

            // GLOBAL OPACITY ÇARPIMI
            // Hem sticker'ın kendi şeffaflığı hem de bizim verdiğimiz opacity etkili olsun.
            double finalAlpha = pixelAlpha * opacity;

            if (finalAlpha <= 0.0) continue; // Tamamen şeffafsa geç

            for (int c = 0; c < 3; c++) {
                double srcVal = srcBuff[i + c] & 0xFF;
                double stkVal = stkBuff[stkIndex + c] & 0xFF;

                // Harmanla
                srcBuff[i + c] = (byte) (srcVal * (1.0 - finalAlpha) + stkVal * finalAlpha);
            }
        }

        subSrc.put(0, 0, srcBuff);
    }
}