package photoeditor.core;

import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import photoeditor.MaskProcessors.Mask;
import photoeditor.filters.*;
import photoeditor.utils.ImageUtils;
import photoeditor.utils.LibraryLoader;

import java.util.function.Consumer;

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

    ///
    /// >>> BasicFilter
    ///

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

    public ImageProcessor addExposure(double value) {
        this.currentImage = BasicFilters.adjustExposure(this.currentImage, value);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addSharpen(double amount) {
        this.currentImage = BasicFilters.sharpen(this.currentImage, amount);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addClarity(double sigma) {
        this.currentImage = BasicFilters.adjustClarity(this.currentImage, sigma);
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
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addTemperature(double value) {
        this.currentImage = BasicFilters.adjustTemperature(this.currentImage, value);
        refreshAnalysis();
        return this;
    }

    ///
    /// >>> ToneAdjustment
    ///
    public ImageProcessor addShadows(double value) {
        this.currentImage = ToneAdjustment.applyShadows(this.currentImage, value);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addHighlights(double value) {
        this.currentImage = ToneAdjustment.applyHighlights(this.currentImage, value);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addVibrance(double value) {
        this.currentImage = ToneAdjustment.applyVibrance(this.currentImage, value);
        refreshAnalysis();
        return this;
    }

    ///
    /// >>> MaskFilters
    ///

    public ImageProcessor applyMaskedFilter(Mask mask, Consumer<ImageProcessor> filterOperation) {
        Mat originalState = this.currentImage.clone();

        filterOperation.accept(this);

        photoeditor.MaskProcessors.MaskBlender.blend(originalState, this.currentImage, mask);

        originalState.release(); // Kopyayı hafızadan sil
        refreshAnalysis();       // Histogram/Analiz verisini güncelle
        return this;
    }

    ///
    /// >>> GeometricFilter
    ///

    public ImageProcessor rotate(double angle) {
        // Varsayılan olarak beyaz (255, 255, 255) gönderiyoruz
        return rotate(angle, 255, 255, 255);
    }

    public ImageProcessor rotate(double angle, int r, int g, int b) {
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
        refreshAnalysis();
        return this;
    }

    public ImageProcessor scale(double factor) {
        this.currentImage = GeometricFilters.scale(this.currentImage, factor);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor resize(int width, int height) {
        this.currentImage = GeometricFilters.resize(this.currentImage, width, height);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor cropCenterSquare() {
        int minSide = Math.min(this.currentImage.width(), this.currentImage.height());
        int x = (this.currentImage.width() - minSide) / 2;
        int y = (this.currentImage.height() - minSide) / 2;

        this.currentImage = GeometricFilters.crop(this.currentImage, x, y, minSide, minSide);
        refreshAnalysis();
        return this;
    }

    ///
    /// >>> DecorationFilter
    ///

    // Genel Metot
    public ImageProcessor addText(String text, int x, int y, double scale, int r, int g, int b, int fontFace) {
        Scalar color = new Scalar(b, g, r);
        DecorationFilters.addText(this.currentImage, text, x, y, scale, color, 2, fontFace);
        refreshAnalysis();
        return this;
    }

    // Varsayılan Fontlu Metot
    public ImageProcessor addText(String text, int x, int y, double scale, int r, int g, int b) {
        return addText(text, x, y, scale, r, g, b, Imgproc.FONT_HERSHEY_DUPLEX);
    }

    // Ortalanmış Metot
    public ImageProcessor addWatermark(String text, double scale, int r, int g, int b, int fontFace) {
        Scalar color = new Scalar(b, g, r);
        DecorationFilters.addCenteredText(this.currentImage, text, scale, color, 2, fontFace);
        refreshAnalysis();
        return this;
    }

    // Çıkartma Ekleme Metot
    public ImageProcessor addSticker(String stickerPath, int x, int y, int width, int height) {
        return addSticker(stickerPath, x, y, width, height, 1.0);
    }

    // Çıkartma Ekleme Metot (++ @param Opeacity)
    public ImageProcessor addSticker(String stickerPath, int x, int y, int width, int height, double opacity) {
        Mat sticker = org.opencv.imgcodecs.Imgcodecs.imread(stickerPath, org.opencv.imgcodecs.Imgcodecs.IMREAD_UNCHANGED);

        if (!sticker.empty()) {
            DecorationFilters.addSticker(this.currentImage, sticker, x, y, width, height, opacity);
        } else {
            System.err.println("Sticker bulunamadı: " + stickerPath);
        }

        refreshAnalysis();
        return this;
    }

    ///
    /// >>> ArtisticFilter
    ///

    public ImageProcessor applyPixelate(int pixelSize) {
        ArtisticFilters.applyPixelate(this.currentImage, pixelSize);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor applySepia() {
        ArtisticFilters.applySepia(this.currentImage);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor applyVignette(double intensity) {
        ArtisticFilters.applyVignette(this.currentImage, intensity);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor applyBlur(double sigma) {
        ArtisticFilters.applyBlur(this.currentImage, sigma);
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