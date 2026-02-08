package photoeditor.core;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import photoeditor.MaskProcessors.ApplierMaskEffect;
import photoeditor.MaskProcessors.Mask;
import photoeditor.MaskProcessors.SmartMaskFactory;
import photoeditor.filters.*; // ParallelAdaptiveFilters burada olmalı
import photoeditor.utils.ImageUtils;
import photoeditor.utils.NativeLibraryLoader;
import photoeditor.utils.PsdWriter;

import java.io.IOException;
import java.util.Stack;
import java.util.function.Consumer;

public class ImageProcessor implements AutoCloseable {

    private Mat currentImage;
    private ImageAnalysis cachedAnalysis;
    private final MetaDataAnalysis metaData;
    private final AdaptiveFilters adaptiveFilters;

    private static NativeLibraryLoader libraryLoader;
    private SmartMaskFactory maskFactory;

    private final Stack<Mat> undoStack = new Stack<>();
    private final Stack<Mat> redoStack = new Stack<>();
    private static final int MAX_STACK_SIZE = 7;

    public static void setNativeLoader(NativeLibraryLoader loader) {
        libraryLoader = loader;
    }

    private void initializeNativeEngines() {
        if (libraryLoader != null) {
            // Bu çağrı platforma göre hem OpenCV hem ONNX'i yüklemeli
            libraryLoader.loadLibrary();
        }
    }

    // Factory'yi sisteme tanıtmak için
    public ImageProcessor setMaskFactory(SmartMaskFactory factory) {
        this.maskFactory = factory;
        return this;
    }

    public ImageProcessor(byte[] imageBytes) {
        initializeNativeEngines();
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
        // Stack'leri temizle
        clearStack(undoStack);
        clearStack(redoStack);
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
    /// >>> Undo Redo
    ///

    /**
     * Mevcut durumu Undo yığınına kaydeder.
     * Herhangi bir filtre/işlem uygulanmadan HEMEN ÖNCE çağrılmalıdır.
     */
    private void saveStep() {
        // Redo yığınını temizle (Yeni bir işlem yapıldığında ileri alma mantığı bozulur)
        clearStack(redoStack);

        // Mevcut resmin bir kopyasını al ve Undo yığınına ekle
        undoStack.push(this.currentImage.clone());

        // Limit aşılırsa en eski kaydı sil (Bellek yönetimi)
        if (undoStack.size() > MAX_STACK_SIZE) {
            Mat oldest = undoStack.remove(0);
            if (oldest != null) oldest.release();
        }
    }

    private void clearStack(Stack<Mat> stack) {
        while (!stack.isEmpty()) {
            Mat m = stack.pop();
            if (m != null) m.release();
        }
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public ImageProcessor undo() {
        if (canUndo()) {
            // Mevcut hali Redo'ya at
            redoStack.push(this.currentImage.clone());

            // Undo'dan son halini çek ve değiştir
            swapImage(undoStack.pop());
        }
        return this;
    }

    public ImageProcessor redo() {
        if (canRedo()) {
            // Mevcut hali Undo'ya geri at
            undoStack.push(this.currentImage.clone());

            // Redo'dan çek ve değiştir
            swapImage(redoStack.pop());
        }
        return this;
    }

    ///
    /// >>> BasicFilter
    ///

    public ImageProcessor addBrightness(double value) {
        saveStep();
        this.currentImage = BasicFilters.adjustBrightnessContrast(this.currentImage, 1.0, value);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addContrast(double value) {
        saveStep();
        this.currentImage = BasicFilters.adjustBrightnessContrast(this.currentImage, value, 0);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addExposure(double value) {
        saveStep();
        this.currentImage = BasicFilters.adjustExposure(this.currentImage, value);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addSharpen(double amount) {
        saveStep();
        this.currentImage = BasicFilters.applySharpness(this.currentImage, amount);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addClarity(double sigma) {
        saveStep();
        this.currentImage = BasicFilters.adjustClarity(this.currentImage, sigma);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addSaturation(double value) {
        saveStep();
        this.currentImage = BasicFilters.adjustSaturation(this.currentImage, this.cachedAnalysis, value);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addGrayScale(double value) {
        saveStep();
        this.currentImage = BasicFilters.adjustGrayscale(this.currentImage, value);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addTemperature(double value) {
        saveStep();
        this.currentImage = BasicFilters.adjustTemperature(this.currentImage, value);
        refreshAnalysis();
        return this;
    }

    ///
    /// >>> ToneAdjustment
    ///

    public ImageProcessor addShadows(double value) {
        saveStep();
        this.currentImage = ToneAdjustment.applyShadows(this.currentImage, value);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addHighlights(double value) {
        saveStep();
        this.currentImage = ToneAdjustment.applyHighlights(this.currentImage, value);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addVibrance(double value) {
        saveStep();
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
        saveStep();
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
        saveStep();
        this.currentImage = GeometricFilters.rotate(this.currentImage, angle, color);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor rotateRight() { return this.rotate(90); }
    public ImageProcessor rotateLeft() { return this.rotate(270); }

    public ImageProcessor flipHorizontal() {
        saveStep();
        this.currentImage = GeometricFilters.flip(this.currentImage, true, false);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor scale(double factor) {
        saveStep();
        this.currentImage = GeometricFilters.scale(this.currentImage, factor);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor resize(int width, int height) {
        saveStep();
        this.currentImage = GeometricFilters.resize(this.currentImage, width, height);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor cropCenterSquare() {
        int minSide = Math.min(this.currentImage.width(), this.currentImage.height());
        int x = (this.currentImage.width() - minSide) / 2;
        int y = (this.currentImage.height() - minSide) / 2;
        saveStep();
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
        saveStep();
        refreshAnalysis();
        return this;
    }

    public ImageProcessor addText(String text, int x, int y, double scale, int r, int g, int b) {
        return addText(text, x, y, scale, r, g, b, Imgproc.FONT_HERSHEY_DUPLEX);
    }

    public ImageProcessor addWatermark(String text, double scale, int r, int g, int b, int fontFace) {
        Scalar color = new Scalar(b, g, r);
        saveStep();
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
            saveStep();
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
        saveStep();
        ArtisticFilters.applyPixelate(this.currentImage, pixelSize);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor applySepia() {
        saveStep();
        ArtisticFilters.applySepia(this.currentImage);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor applyVignette(double intensity) {
        saveStep();
        ArtisticFilters.applyVignette(this.currentImage, intensity);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor applyBlur(double sigma) {
        saveStep();
        ArtisticFilters.applyBlur(this.currentImage, sigma);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor applyBlur_forStream(double sigma, boolean highQuality) {
        saveStep();
        ArtisticFilters.applyBlur_forStream(this.currentImage, sigma, highQuality);
        refreshAnalysis();
        return this;
    }

    public Mat applyBlur_Fast_toPreview(double sigma) {
        return ArtisticFilters.applyBlur_Fast_toPreview(this.currentImage, sigma);
    }

    public ImageProcessor applyMedianBlur(int kernelSize){
        saveStep();
        ArtisticFilters.applyMedianBlur(this.currentImage, kernelSize);
        refreshAnalysis();
        return this;
    }

    public ImageProcessor applyMotionBlur(int kernelSize, double angle){
        saveStep();
        ArtisticFilters.applyMotionBlur(this.currentImage, kernelSize, angle);
        refreshAnalysis();
        return this;
    }

    ///
    /// >>> Model Tabanlı Efektler  (AI)
    ///

    public ImageProcessor addPortraitEffect(double blurSigma, int maskSoftness) {
        if (maskFactory == null) return this;
        saveStep();
        Mat result = ApplierMaskEffect.applyPortraitEffect(
                this.currentImage,
                this.maskFactory,
                blurSigma,
                maskSoftness
        );
        swapImage(result);
        return this;
    }

    public ImageProcessor addMotionBlur(int intensity, double angle, int maskSoftness) {
        if (maskFactory == null) return this;
        saveStep();
        Mat result = ApplierMaskEffect.applyMotionBlurEffect(
                this.currentImage,
                this.maskFactory,
                intensity,
                angle,
                maskSoftness
        );
        swapImage(result);
        return this;
    }

    public ImageProcessor addColorSplash(int maskSoftness) {
        if (this.maskFactory == null) {
            System.err.println("MaskFactory yüklü değil!");
            return this;
        }
        saveStep();
        // 1. Önce maskeyi istenen yumuşaklıkta üret
        Mask mask = this.maskFactory.createPersonMask(this.currentImage, maskSoftness);

        // 2. Yeni imzalı metodu çağır
        Mat result = ApplierMaskEffect.applyColorSplash(this.currentImage, mask);

        // 3. Senin yazdığın güvenli bellek yönetimi metoduyla resmi değiştir
        swapImage(result);

        return this;
    }

    public ImageProcessor changeBackground(Mat newBackground, int maskSoftness) {
        if (maskFactory == null || newBackground.empty()) return this;

        // Arka planı mevcut resim boyutuna getir
        Mat resizedBg = new Mat();
        Imgproc.resize(newBackground, resizedBg, this.currentImage.size());
        saveStep();
        Mask mask = this.maskFactory.createPersonMask(this.currentImage, maskSoftness);
        Mat result = ApplierMaskEffect.applyBackgroundReplacement(this.currentImage, resizedBg, mask);

        resizedBg.release();
        swapImage(result);
        return this;
    }

    ///
    /// >>> Adaptif & Atmosferik Filtreler
    ///

    public ImageProcessor applyCandleEffect(double intensity) {
        Mat destination = new Mat();
        saveStep();
        this.adaptiveFilters.applyCandleEffect(this.currentImage, destination, intensity);
        swapImage(destination);
        return this;
    }

    public ImageProcessor applyAtmosphereFilter(double intensity) {
        Mat destination = new Mat();
        saveStep();
        this.adaptiveFilters.applyAtmosphereFilter(this.currentImage, destination, intensity);
        swapImage(destination);
        return this;
    }

    public ImageProcessor applyMidnightEffect(double intensity) {
        Mat destination = new Mat();
        saveStep();
        this.adaptiveFilters.applyMidnightEffect(this.currentImage, destination, intensity);
        swapImage(destination);
        return this;
    }

    public ImageProcessor applyGoldenHour(double intensity) {
        Mat destination = new Mat();
        saveStep();
        this.adaptiveFilters.applyGoldenHour(this.currentImage, destination, intensity);
        swapImage(destination);
        return this;
    }

    public ImageProcessor applyDramaticBW(double intensity) {
        Mat destination = new Mat();
        saveStep();
        this.adaptiveFilters.applyDramaticBW(this.currentImage, destination, intensity);
        swapImage(destination);
        return this;
    }

    public ImageProcessor applyRetroSepia(double intensity) {
        Mat destination = new Mat();
        saveStep();
        this.adaptiveFilters.applyRetroSepia(this.currentImage, destination, intensity);
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
            saveStep();
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