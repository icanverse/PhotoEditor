package photoeditor.core;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import photoeditor.MaskProcessors.Mask;
import photoeditor.filters.*; // ParallelAdaptiveFilters burada olmalı
import photoeditor.utils.ImageUtils;
import photoeditor.utils.NativeLibraryLoader;
import photoeditor.utils.PsdWriter;

import java.io.IOException;
import java.util.function.Consumer;

public class ImageProcessor implements AutoCloseable {

    private Mat currentImage;
    private ImageAnalysis cachedAnalysis;
    private final MetaDataAnalysis metaData;
    private final AdaptiveFilters adaptiveFilters;

    private static NativeLibraryLoader libraryLoader;

    public static void setNativeLoader(NativeLibraryLoader loader) {
        libraryLoader = loader;
    }

    private void initializeOpenCV() {
        if (libraryLoader != null) {
            libraryLoader.loadLibrary();
        }
    }

    public ImageProcessor(byte[] imageBytes) {
        initializeOpenCV();
        this.currentImage = ImageUtils.bytesToMat(imageBytes);
        this.cachedAnalysis = new ImageAnalysis(this.currentImage);
        this.metaData = new MetaDataAnalysis(imageBytes);
        this.adaptiveFilters = new AdaptiveFilters();
    }

    private void refreshAnalysis() {
        if (this.currentImage != null && !this.currentImage.empty()) {
            this.cachedAnalysis = new ImageAnalysis(this.currentImage);
        }
    }

    ///
    /// >>> Genel Metotlar
    ///

    // Normal Kayıt
    public boolean save(String path) {
        return save(path, 1.0); // İşlemi alttaki metoda devreder
    }

    // Ölçekli Kayıt
    public boolean save(String path, double scale) {
        if (this.currentImage == null || this.currentImage.empty()) {
            System.err.println("Kaydedilecek resim boş!");
            return false;
        }

        // Eğer ölçek 1.0 ise (veya çok yakınsa) boşuna işlem yapma
        if (Math.abs(scale - 1.0) < 0.001) {
            return saveInternal(path, this.currentImage);
        }

        int newWidth = (int) (this.currentImage.cols() * scale);
        int newHeight = (int) (this.currentImage.rows() * scale);
        Size newSize = new Size(newWidth, newHeight);

        Mat resizedImage = new Mat();

        // Küçültürken (scale < 1) INTER_AREA daha iyi sonuç verir (harelenmeyi önler).
        // Büyütürken (scale > 1) INTER_LINEAR veya INTER_CUBIC iyidir.
        int interpolation = (scale < 1.0) ? Imgproc.INTER_AREA : Imgproc.INTER_LINEAR;

        Imgproc.resize(this.currentImage, resizedImage, newSize, 0, 0, interpolation);

        try {
            return saveInternal(path, resizedImage);
        } finally {
            resizedImage.release(); // Geçici görseli temizleyerek Android'deki OutOfMemory hatasını önledik
        }
    }

    // İç Kayıt
    private boolean saveInternal(String path, Mat imageToSave) {
        // PSD Kontrolü (Önceki konuşmamızdan gelen PsdWriter)
        if (path.toLowerCase().endsWith(".psd")) {
            try {
                PsdWriter.save(imageToSave, path);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        // Standart OpenCV Kaydı (JPG, PNG vb.)
        return Imgcodecs.imwrite(path, imageToSave);
    }

    // PSD Çıktı üretir
    private boolean saveAsPsd(String path) {
        try {
            // Daha önce oluşturduğumuz PsdWriter sınıfını burada kullanıyoruz
            PsdWriter.save(this.currentImage, path);
            System.out.println("PSD başarıyla kaydedildi: " + path);
            return true;
        } catch (IOException e) {
            System.err.println("PSD kayıt hatası: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public Mat getResult() {
        return this.currentImage.clone();
    }

    public byte[] getResultAsBytes() {
        return getResultAsBytes(".jpg");
    }

    public byte[] getResultAsBytes(String extension) {
        if (this.currentImage == null || this.currentImage.empty()) {
            return new byte[0];
        }
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(extension, this.currentImage, buffer);
        byte[] bytes = buffer.toArray();
        buffer.release();
        return bytes;
    }

    ///
    /// >>> Bellek Yönetimi
    ///

    @Override
    public void close() {
        release();
    }

    public void release() {
        if (this.currentImage != null) {
            this.currentImage.release();
            this.currentImage = null;
        }
    }

    /**
     * YENİ: Güvenli Resim Değiştirme (Memory Management)
     * Yeni işlenmiş resmi atarken, eski resmin native belleğini temizler.
     */
    private void swapImage(Mat newImage) {
        if (this.currentImage != null && this.currentImage != newImage) {
            this.currentImage.release(); // Eski resmi RAM'den sil
        }
        this.currentImage = newImage;
        refreshAnalysis();
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
        originalState.release();
        refreshAnalysis();
        return this;
    }

    ///
    /// >>> GeometricFilter
    ///

    public ImageProcessor rotate(double angle) {
        return rotate(angle, 255, 255, 255);
    }

    public ImageProcessor rotate(double angle, int r, int g, int b) {
        Scalar color = new Scalar(b, g, r);
        this.currentImage = GeometricFilters.rotate(this.currentImage, angle, color);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor rotateRight() { return this.rotate(90); }
    public ImageProcessor rotateLeft() { return this.rotate(270); }

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

    public ImageProcessor addText(String text, int x, int y, double scale, int r, int g, int b, int fontFace) {
        Scalar color = new Scalar(b, g, r);
        DecorationFilters.addText(this.currentImage, text, x, y, scale, color, 2, fontFace);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addText(String text, int x, int y, double scale, int r, int g, int b) {
        return addText(text, x, y, scale, r, g, b, Imgproc.FONT_HERSHEY_DUPLEX);
    }

    public ImageProcessor addWatermark(String text, double scale, int r, int g, int b, int fontFace) {
        Scalar color = new Scalar(b, g, r);
        DecorationFilters.addCenteredText(this.currentImage, text, scale, color, 2, fontFace);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addSticker(String stickerPath, int x, int y, int width, int height) {
        return addSticker(stickerPath, x, y, width, height, 1.0);
    }

    public ImageProcessor addSticker(String stickerPath, int x, int y, int width, int height, double opacity) {
        Mat sticker = Imgcodecs.imread(stickerPath, Imgcodecs.IMREAD_UNCHANGED);
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

    public ImageProcessor applyBlur_forStream(double sigma, boolean highQuality) {
        ArtisticFilters.applyBlur_forStream(this.currentImage, sigma, highQuality);
        refreshAnalysis();
        return this;
    }

    public Mat applyBlur_Fast_toPreview(double sigma) {
        return ArtisticFilters.applyBlur_Fast_toPreview(this.currentImage, sigma);
    }

    public ImageProcessor applyMedianBlur(int kernelSize){
        ArtisticFilters.applyMedianBlur(this.currentImage, kernelSize);
        refreshAnalysis();
        return this;
    }

    ///
    /// >>> Adaptif & Atmosferik Filtreler
    ///

    public ImageProcessor applyCandleEffect() {
        Mat destination = new Mat();
        this.adaptiveFilters.applyCandleEffect(this.currentImage, destination);
        swapImage(destination);
        return this;
    }

    public ImageProcessor applyAtmosphereFilter(double intensity) {
        Mat destination = new Mat();
        this.adaptiveFilters.applyAtmosphereFilter(this.currentImage, destination, intensity);
        swapImage(destination);
        return this;
    }

    /// Referans fotoğraftan filtre uygula(Match Color)
    public ImageProcessor applyStyleFromImage(String referenceImagePath, double intensity) {

        // Referans resmi geçici olarak belleğe yükle
        Mat referenceImage = Imgcodecs.imread(referenceImagePath);
        if (referenceImage.empty()) {
            System.err.println("Referans resim yüklenemedi: " + referenceImagePath);
            return this;
        }

        try {
            // Referans resmin analizini yap (Baskın rengi bul)
            // Sadece analiz için geçici bir nesne oluşturuyoruz
            ImageAnalysis refAnalysis = new ImageAnalysis(referenceImage);
            Scalar moodColor = refAnalysis.getDominantColor(referenceImage);

            System.out.println("Referans Resimden Çekilen Renk: " + moodColor);

            // Bulunan rengi mevcut resme uygula
            Mat destination = new Mat();
            adaptiveFilters.applyCustomColorFilter(this.currentImage, destination, moodColor, intensity);

            // Sonucu kaydet
            swapImage(destination);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 5. TEMİZLİK: Referans resimle işimiz bitti, RAM'den hemen siliyoruz.
            referenceImage.release();
        }

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