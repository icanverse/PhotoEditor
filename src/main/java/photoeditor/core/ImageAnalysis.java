package photoeditor.core;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class ImageAnalysis {

    private final int width;
    private final int height;
    private final int channels; // 3: Renkli ; 1: SB
    private final double averageBrightness; // 0-255 arası ortalama ışık
    private final String colorSpace;

    public ImageAnalysis(Mat image) {
        if (image.empty()) {
            throw new IllegalArgumentException("Analiz edilecek resim boş!");
        }

        this.width = image.width();
        this.height = image.height();
        this.channels = image.channels();

        // Renk Uzayı Tahmini
        if (this.channels == 1) {
            this.colorSpace = "Grayscale (Siyah-Beyaz)";
        } else if (this.channels == 3) {
            this.colorSpace = "Renkli (BGR/RGB)";
        } else {
            this.colorSpace = "Bilinmiyor (" + this.channels + " kanal)";
        }

        // Ortalama Renk ve Parlaklık Hesaplama
        // Core.mean tüm piksellerin ortalamasını alır (Mavi, Yeşil, Kırmızı)
        Scalar meanColor = Core.mean(image);

        // Basit parlaklık formülü: (R+G+B) / 3
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