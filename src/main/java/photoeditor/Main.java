package photoeditor;

import nu.pattern.OpenCV;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import photoeditor.MaskProcessors.*;
import photoeditor.filters.ArtisticFilters;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;

public class Main {

    // DOSYA YOLLARI
    private static final String MODEL_PATH = "src/main/resources/model_raw.onnx";
    private static final String INPUT_IMAGE_PATH = "src/test/resources/face.jpg";
    private static final String BG_IMAGE_PATH = "src/test/resources/marine.jpg";

    public static void main(String[] args) {
        // 1. Başlangıç
        OpenCV.loadLocally();
        System.out.println("🚀 Photo Editor Başlatılıyor...");

        if (!new File(MODEL_PATH).exists() || !new File(INPUT_IMAGE_PATH).exists()) {
            System.err.println("❌ Dosyalar eksik! Lütfen resim ve model dosyalarını kontrol et.");
            return;
        }

        try {
            // 2. Kaynakları Yükle
            Mat originalImage = Imgcodecs.imread(INPUT_IMAGE_PATH);
            Mat newBackground = Imgcodecs.imread(BG_IMAGE_PATH);

            if (newBackground.empty()) {
                System.out.println("⚠️ Arka plan resmi bulunamadı, değişim modunda siyah kullanılacak.");
                newBackground = new Mat(originalImage.size(), originalImage.type());
            } else {
                Imgproc.resize(newBackground, newBackground, originalImage.size());
            }

            // 3. Yapay Zeka Fabrikasını Hazırla
            SmartMaskFactory factory = new SmartMaskFactory(MODEL_PATH);
            System.out.println("🧠 Yapay Zeka Hazır. Efektler uygulanıyor...");

            // --- EFEKTLER ---

            // 1. Portre Modu: Saç telleri için YÜKSEK yumuşaklık (25)
            System.out.print("📸 1. Portre Modu... ");
            // Parametreler: (Resim, Fabrika, BlurŞiddeti, MaskeYumuşaklığı)
            Mat portraitResult = ApplierMaskEffect.applyPortraitEffect(originalImage, factory, 30.0, 25);
            System.out.println("✅");

            // 2. Hareket Modu: Biraz daha belirgin kenarlar için ORTA yumuşaklık (15)
            System.out.print("🏎️ 2. Hareket Modu... ");
            // Parametreler: (Resim, Fabrika, Hız, Açı, MaskeYumuşaklığı)
            Mat motionResult = ApplierMaskEffect.applyMotionBlurEffect(originalImage, factory, 80, 0, 15);
            System.out.println("✅");

            // 3. Color Splash: Renk taşmasını önlemek için DÜŞÜK yumuşaklık (5)
            System.out.print("🎨 3. Color Splash... ");
            Mask splashMask = factory.createPersonMask(originalImage, 5); // Maskeyi burada üretiyoruz
            Mat colorSplashResult = applyColorSplashManual(originalImage, splashMask);
            System.out.println("✅");

            // 4. Arka Plan Değişimi: Montaj için STANDART yumuşaklık (11)
            System.out.print("🏖️ 4. Arka Plan Değişimi... ");
            Mask replaceMask = factory.createPersonMask(originalImage, 11); // Maskeyi burada üretiyoruz
            Mat replacementResult = applyBackgroundReplacementManual(originalImage, newBackground, replaceMask);
            System.out.println("✅");

            // ==================================================================================
            // MASKEYİ GÖRSELLEŞTİRME
            // ==================================================================================
            System.out.print("🎭 Maske görüntüsü hazırlanıyor... ");
            // Görselleştirme için softness: 0 (En Keskin) kullanıyoruz ki yapay zeka sınırları net görülsün
            Mask rawMaskObject = factory.createPersonMask(originalImage, 0);
            Mat maskImageForDisplay = convertMaskToMat(rawMaskObject);
            System.out.println("✅");
            // ==================================================================================


            // SONUÇLARI GÖSTER
            SwingUtilities.invokeLater(() -> showResultsWindow(
                    originalImage,
                    maskImageForDisplay,
                    portraitResult,
                    motionResult,
                    colorSplashResult,
                    replacementResult
            ));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- YARDIMCI METOTLAR ---

    // Maske nesnesini gri tonlamalı resme çevirir
    private static Mat convertMaskToMat(Mask mask) {
        int h = mask.getHeight();
        int w = mask.getWidth();
        Mat mat = new Mat(h, w, CvType.CV_8UC1);
        byte[] data = new byte[w * h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float intensity = mask.getIntensity(x, y);
                data[y * w + x] = (byte) (intensity * 255);
            }
        }
        mat.put(0, 0, data);
        return mat;
    }

    // GÜNCELLENDİ: Artık parametre olarak 'Factory' değil, hazır 'Mask' alıyor.
    private static Mat applyColorSplashManual(Mat input, Mask mask) {
        Mat result = input.clone();
        Mat grayBg = new Mat();
        Imgproc.cvtColor(input, grayBg, Imgproc.COLOR_BGR2GRAY);
        Mat grayBg3Channel = new Mat();
        Imgproc.cvtColor(grayBg, grayBg3Channel, Imgproc.COLOR_GRAY2BGR);

        // Dışarıdan gelen maskeyi kullan
        MaskBlender.blend(grayBg3Channel, result, mask);

        grayBg.release(); grayBg3Channel.release();
        return result;
    }

    // GÜNCELLENDİ: Artık parametre olarak 'Factory' değil, hazır 'Mask' alıyor.
    private static Mat applyBackgroundReplacementManual(Mat input, Mat newBg, Mask mask) {
        Mat result = input.clone();

        // Dışarıdan gelen maskeyi kullan
        MaskBlender.blend(newBg, result, mask);

        return result;
    }

    // --- GUI KODLARI ---

    private static void showResultsWindow(Mat orig, Mat maskImg, Mat portrait, Mat motion, Mat splash, Mat replace) {
        JFrame frame = new JFrame("Photo Editor - AI Showcase");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setLayout(new GridLayout(1, 6));

        frame.add(createPanel(orig, "1. Orijinal"));
        frame.add(createPanel(maskImg, "2. Yapay Zeka Maskesi"));
        frame.add(createPanel(portrait, "3. Portre (Bokeh)"));
        frame.add(createPanel(motion, "4. Hareket (Motion)"));
        frame.add(createPanel(splash, "5. Color Splash"));
        frame.add(createPanel(replace, "6. Arka Plan Değişimi"));

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static JPanel createPanel(Mat mat, String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JLabel(new ImageIcon(Mat2BufferedImage(mat))));
        JLabel lbl = new JLabel(title, SwingConstants.CENTER);
        lbl.setFont(new Font("Arial", Font.BOLD, 14));
        lbl.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        p.add(lbl, BorderLayout.SOUTH);
        p.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
        return p;
    }

    public static BufferedImage Mat2BufferedImage(Mat m) {
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (m.channels() > 1) type = BufferedImage.TYPE_3BYTE_BGR;
        int bufferSize = m.channels() * m.cols() * m.rows();
        byte[] b = new byte[bufferSize];
        m.get(0, 0, b);
        BufferedImage image = new BufferedImage(m.cols(), m.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);
        return image;
    }
}