package photoeditor.core; // Paket ismini kendi yapına göre ayarla

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

public class ImageAnalysis {

    private final int width;
    private final int height;
    private final int channels;
    private final double averageBrightness;
    private final String colorSpace;

    public ImageAnalysis(Mat image) {
        if (image.empty()) {
            throw new IllegalArgumentException("Analiz edilecek resim boş!");
        }

        this.width = image.width();
        this.height = image.height();
        this.channels = image.channels();

        if (this.channels == 1) {
            this.colorSpace = "Grayscale (Siyah-Beyaz)";
        } else if (this.channels == 3) {
            this.colorSpace = "Renkli (BGR/RGB)";
        } else {
            this.colorSpace = "Bilinmiyor (" + this.channels + " kanal)";
        }

        Scalar meanColor = Core.mean(image);
        if (channels == 3) {
            this.averageBrightness = (meanColor.val[0] + meanColor.val[1] + meanColor.val[2]) / 3.0;
        } else {
            this.averageBrightness = meanColor.val[0];
        }
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getChannels() { return channels; }
    public double getAspectRatio() { return (double) width / height; }
    public String getColorSpace() { return colorSpace; }
    public double getAverageBrightness() { return averageBrightness; }

    // Baskın rengi bulur (K-Means)
    public Scalar getDominantColor(Mat sourceImage) {
        // Küçük resim
        Mat smallImage = new Mat();
        Size size = new Size(50, 50);
        Imgproc.resize(sourceImage, smallImage, size);

        // 2. K-Means için veriyi hazırla: (Piksel Sayısı x 3) boyutunda matris
        // reshape(1, ...) kanalları sütunlara ayırır.
        Mat samples = smallImage.reshape(1, (int) (smallImage.total()));
        samples.convertTo(samples, CvType.CV_32F);

        // 3. K-Means Parametreleri
        int k = 1; // Sadece EN baskın 1 renk
        Mat labels = new Mat();
        TermCriteria criteria = new TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 10, 1.0);
        Mat centers = new Mat();

        // 4. Algoritmayı Çalıştır
        // Sonuçta 'centers' matrisi 1 satır, 3 sütun (B, G, R) olacak.
        Core.kmeans(samples, k, labels, criteria, 3, Core.KMEANS_PP_CENTERS, centers);

        // 5. Merkez Rengi Al
        double blue = centers.get(0, 0)[0];
        double green = centers.get(0, 1)[0];
        double red = centers.get(0, 2)[0];

        // Temizlik
        smallImage.release();
        samples.release();
        labels.release();
        centers.release();

        return new Scalar(blue, green, red);
    }

    @Override
    public String toString() {
        return String.format(
                "=== GÖRÜNTÜ ANALİZ RAPORU ===\n" +
                        "Boyutlar   : %d x %d piksel\n" +
                        "Kanal      : %d (%s)\n" +
                        "Parlaklık  : %.2f / 255.0\n" +
                        "En-Boy     : %.2f",
                width, height, channels, colorSpace, averageBrightness, getAspectRatio()
        );
    }
}