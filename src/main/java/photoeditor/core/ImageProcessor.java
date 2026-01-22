package photoeditor.core;

import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import photoeditor.filters.ArtisticFilters;
import photoeditor.filters.BasicFilters;
import photoeditor.filters.GeometricFilters;
import photoeditor.utils.ImageUtils;
import photoeditor.utils.LibraryLoader;

public class ImageProcessor {

    private Mat currentImage;
    private ImageAnalysis cachedAnalysis;       // Analiz nesnesini burada önbellek (cache) olarak tutabiliriz
    private final MetaDataAnalysis metaData;

    public ImageProcessor(byte[] imageBytes) {
        LibraryLoader.load();
        this.currentImage = ImageUtils.bytesToMat(imageBytes);  // byte -> matris
        this.cachedAnalysis = new ImageAnalysis(this.currentImage); // analiz
        this.metaData = new MetaDataAnalysis(imageBytes);   // metadata verisini yükler
    }

    private void refreshAnalysis() {
        this.cachedAnalysis = new ImageAnalysis(this.currentImage);
    }

    public ImageProcessor addBrightness(double value) {
        this.currentImage = BasicFilters.adjustBrightnessContrast(this.currentImage, 1.0, value);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addContrast(double value) {
        this.currentImage = BasicFilters.adjustBrightnessContrast(this.currentImage, value, 0);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addSaturation(double value) {
        this.currentImage = BasicFilters.adjustSaturation(this.currentImage, this.cachedAnalysis, value);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor makeGrayscale() {
        this.currentImage = BasicFilters.toGrayscale(this.currentImage, this.cachedAnalysis);
        refreshAnalysis(); // Artık kanal bilgisi değişti mi diye kontrol etmek için tazeledik
        return this;
    }

    public ImageProcessor rotate(double angle) {
        // Varsayılan olarak beyaz (255, 255, 255) gönderiyoruz
        return rotate(angle, 255, 255, 255);
    }

    public ImageProcessor rotate(double angle, int r, int g, int b) {
        // OpenCV Scalar BGR (Blue, Green, Red) bekler! Sıralamaya dikkat.
        Scalar color = new Scalar(b, g, r);

        this.currentImage = GeometricFilters.rotate(this.currentImage, angle, color);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor rotateRight() {
        return this.rotate(90);
    }

    public ImageProcessor rotateLeft() {
        return this.rotate(270);
    }

    public ImageProcessor flipHorizontal() {
        this.currentImage = GeometricFilters.flip(this.currentImage, true, false);
        // Boyut değişmedi ama yine de tazelemek iyidir
        refreshAnalysis();
        return this;
    }

    // Resmi belirli bir oranda küçült/büyüt (Örn: 0.5)
    public ImageProcessor scale(double factor) {
        this.currentImage = GeometricFilters.scale(this.currentImage, factor);
        refreshAnalysis(); // Boyut kesinlikle değişti
        return this;
    }

    // Resmi belirli piksel boyutuna getir
    public ImageProcessor resize(int width, int height) {
        this.currentImage = GeometricFilters.resize(this.currentImage, width, height);
        refreshAnalysis();
        return this;
    }

    // Basit Merkezden Kırpma (Instagram karesi gibi)
    public ImageProcessor cropCenterSquare() {
        int minSide = Math.min(this.currentImage.width(), this.currentImage.height());
        int x = (this.currentImage.width() - minSide) / 2;
        int y = (this.currentImage.height() - minSide) / 2;

        this.currentImage = GeometricFilters.crop(this.currentImage, x, y, minSide, minSide);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor applyPixelate(int pixelSize) {
        // BasicFilters gibi yeni resim döndürmüyor, mevcut (currentImage) üzerinde değişiklik yapıyor.
        ArtisticFilters.applyPixelate(this.currentImage, pixelSize);

        // Resim ciddi oranda değiştiği için analizi tazelemek iyi olur.
        refreshAnalysis();
        return this;
    }

    public ImageAnalysis analyze() {
        return this.cachedAnalysis;
    }

    public MetaDataAnalysis getMetaData() {
        return this.metaData;
    }

    public byte[] process() {
        return ImageUtils.matToBytes(this.currentImage);
    }
}