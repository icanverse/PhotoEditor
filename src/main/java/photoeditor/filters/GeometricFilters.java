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
}