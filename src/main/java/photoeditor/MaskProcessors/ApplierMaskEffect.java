package photoeditor.MaskProcessors;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import photoeditor.filters.ArtisticFilters;
import photoeditor.utils.TiledProcessor;

public class ApplierMaskEffect {

    /**
     * Verilen resme yapay zeka destekli portre modu (Arka plan bulanıklığı) uygular.
     *
     * @param inputImage  İşlenecek orijinal net resim.
     * @param factory     Hazır durumdaki SmartMaskFactory (Model yüklü olmalı).
     * @return            Portre efekti uygulanmış YENİ bir Mat nesnesidir.
     */
    public static Mat applyPortraitEffect(Mat inputImage, SmartMaskFactory factory, double blurSigma, int maskSoftness) {
        Mat resultImage = inputImage.clone();
        Mat blurredBackground = inputImage.clone();

        ArtisticFilters.applyBlur(blurredBackground, blurSigma);

        // Fabrikaya softness parametresini gönderiyoruz
        Mask personMask = factory.createPersonMask(inputImage, maskSoftness);

        MaskBlender.blend(blurredBackground, resultImage, personMask);
        blurredBackground.release();
        return resultImage;
    }

    /**
     * Arka planı siyah-beyaz yapar, kişiyi renkli bırakır.
     * @param input Orijinal resim
     * @param mask  Önceden üretilmiş (ve yumuşatılmış) maske nesnesi
     */
    public static Mat applyColorSplash(Mat input, Mask mask) {
        // 1. Sonuç için orijinalin kopyasını oluştur
        Mat result = input.clone();

        // 2. Arka plan katmanı: Siyah-Beyaz
        Mat grayBg = new Mat();
        org.opencv.imgproc.Imgproc.cvtColor(input, grayBg, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY);

        // Gri resmi tekrar 3 kanallı BGR'ye çevir (Kanal uyumu için)
        Mat grayBg3Channel = new Mat();
        org.opencv.imgproc.Imgproc.cvtColor(grayBg, grayBg3Channel, org.opencv.imgproc.Imgproc.COLOR_GRAY2BGR);

        // 3. Birleştirme (Maske 0 ise Gri, 1 ise Renkli)
        photoeditor.MaskProcessors.MaskBlender.blend(grayBg3Channel, result, mask);

        // 4. Bellek Temizliği
        grayBg.release();
        grayBg3Channel.release();

        return result;
    }


    public static Mat applyBackgroundReplacement(Mat input, Mat newBg, Mask mask) {
        // 1. Sonuç için orijinalin kopyasını oluştur (İnsan kısmı buradan gelecek)
        Mat result = input.clone();

        // 2. Birleştirme (Blending) işlemi
        // Maske 0 (Siyah) ise -> newBg (Yeni Arka Plan)
        // Maske 1 (Beyaz) ise -> result (Orijinal İnsan)
        photoeditor.MaskProcessors.MaskBlender.blend(newBg, result, mask);

        return result;
    }

    public static Mat applyMotionBlurEffect(Mat inputImage, SmartMaskFactory factory, int intensity, double angle, int maskSoftness) {
        Mat resultImage = inputImage.clone();
        Mat motionBlurredBg = inputImage.clone();

        ArtisticFilters.applyMotionBlur(motionBlurredBg, intensity, angle);

        // Fabrikaya softness parametresini gönderiyoruz
        Mask personMask = factory.createPersonMask(inputImage, maskSoftness);

        MaskBlender.blend(motionBlurredBg, resultImage, personMask);
        motionBlurredBg.release();
        return resultImage;
    }
}
