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
}