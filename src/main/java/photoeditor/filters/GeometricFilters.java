package photoeditor.filters;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

public class GeometricFilters {
    public static Mat rotate(Mat source, double angle, Scalar backgroundColor) {
        Point center = new Point(source.width() / 2.0, source.height() / 2.0);

        Mat rotationMatrix = Imgproc.getRotationMatrix2D(center, angle, 1.0);

        Rect bbox = new RotatedRect(center, source.size(), angle).boundingRect();

        double offsetX = (bbox.width / 2.0) - center.x;
        double offsetY = (bbox.height / 2.0) - center.y;

        double[] matrixData02 = rotationMatrix.get(0, 2);
        double[] matrixData12 = rotationMatrix.get(1, 2);

        rotationMatrix.put(0, 2, matrixData02[0] + offsetX);
        rotationMatrix.put(1, 2, matrixData12[0] + offsetY);

        Mat destination = new Mat();

        // warpAffine metoduna artık sabit beyaz yerine parametreden gelen rengi veriyoruz
        Imgproc.warpAffine(
                source,
                destination,
                rotationMatrix,
                new Size(bbox.width, bbox.height),
                Imgproc.INTER_LINEAR,
                Core.BORDER_CONSTANT,
                backgroundColor
        );

        rotationMatrix.release();
        return destination;
    }

    public static Mat flip(Mat source, boolean horizontal, boolean vertical) {
        Mat destination = new Mat();
        int flipCode;

        if (horizontal && vertical) flipCode = -1; // Hem yatay hem dikey
        else if (horizontal) flipCode = 1;         // Sadece yatay (Ayna modu)
        else if (vertical) flipCode = 0;           // Sadece dikey (Su yansıması)
        else return source;

        Core.flip(source, destination, flipCode);
        return destination;
    }

    public static Mat resize(Mat source, int width, int height) {
        Mat destination = new Mat();
        Size newSize = new Size(width, height);

        int interpolation = (width < source.width()) ? Imgproc.INTER_AREA : Imgproc.INTER_LINEAR;

        Imgproc.resize(source, destination, newSize, 0, 0, interpolation);
        return destination;
    }

    public static Mat scale(Mat source, double scaleFactor) {
        int newWidth = (int) (source.width() * scaleFactor);
        int newHeight = (int) (source.height() * scaleFactor);
        return resize(source, newWidth, newHeight);
    }

    public static Mat crop(Mat source, int x, int y, int width, int height) {
        // Güvenlik Kontrolü: Resim sınırlarının dışına çıkmayı engelle
        int safeX = Math.max(0, x);
        int safeY = Math.max(0, y);
        int safeWidth = Math.min(width, source.width() - safeX);
        int safeHeight = Math.min(height, source.height() - safeY);

        Rect roi = new Rect(safeX, safeY, safeWidth, safeHeight);

        return source.submat(roi).clone();
    }

    /**
     * Açılı Kırpma (Rotated Crop):
     * Belirtilen eğik dikdörtgen (RotatedRect) alanını resimden düzleştirerek çıkarır.
     */
    public static Mat cropRotated(Mat source, RotatedRect rotatedRect) {
        Mat destination = new Mat();

        // Döndürme Matrisi Oluştur
        // Belirtilen dikdörtgenin açısı ve merkezi kullanılarak döndürme matrisi hesaplanır.
        Mat rotationMatrix = Imgproc.getRotationMatrix2D(rotatedRect.center, rotatedRect.angle, 1.0);

        // Resmi Döndür
        Mat rotatedImage = new Mat();
        Imgproc.warpAffine(source, rotatedImage, rotationMatrix, source.size(), Imgproc.INTER_CUBIC);

        // Alt Resim Çıkarma (Sub-pixel accuracy)
        // Döndürülmüş resmin merkezinden, istenen boyutta parçayı alır.
        Imgproc.getRectSubPix(rotatedImage, rotatedRect.size, rotatedRect.center, destination);

        // Bellek temizliği
        rotationMatrix.release();
        rotatedImage.release();

        return destination;
    }

    /**
     * Perspektif Düzeltme (Perspective Correction):
     * Verilen 4 köşe noktasını alarak resmi kuş bakışı (flat) görünüme dönüştürür.
     * @param source Kaynak resim
     * @param srcPoints Resim üzerindeki 4 köşe noktası (Sırasıyla: Sol-Üst, Sağ-Üst, Sağ-Alt, Sol-Alt)
     * @param width Çıktı resminin istenen genişliği
     * @param height Çıktı resminin istenen yüksekliği
     */

    public static Mat adjustPerspective(Mat source, Point[] srcPoints, int width, int height) {
        if (srcPoints.length != 4) {
            throw new IllegalArgumentException("Perspektif dönüşümü için tam olarak 4 nokta gereklidir.");
        }

        Mat destination = new Mat();

        // Kaynak Noktaları Tanımla
        // OpenCV MatOfPoint2f formatına dönüştürme
        MatOfPoint2f srcMarker = new MatOfPoint2f(srcPoints);

        // Hedef Noktaları Tanımla
        // Çıktı resminin köşelerine denk gelecek koordinatlar (0,0 -> width,height)
        Point[] dstPoints = new Point[] {
                new Point(0, 0),          // Sol-Üst
                new Point(width, 0),      // Sağ-Üst
                new Point(width, height), // Sağ-Alt
                new Point(0, height)      // Sol-Alt
        };
        MatOfPoint2f dstMarker = new MatOfPoint2f(dstPoints);

        // Perspektif Dönüşüm Matrisini Hesapla
        Mat perspectiveTransform = Imgproc.getPerspectiveTransform(srcMarker, dstMarker);

        // Dönüşümü Uygula (Warp)
        Imgproc.warpPerspective(source, destination, perspectiveTransform, new Size(width, height));

        // Bellek temizliği
        srcMarker.release();
        dstMarker.release();
        perspectiveTransform.release();

        return destination;
    }


}