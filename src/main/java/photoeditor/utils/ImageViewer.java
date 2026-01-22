package photoeditor.utils;

import org.opencv.core.Mat;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

public class ImageViewer {

    public static void show(Mat mat, String title) {
        // Matris boş mu kontrolü
        if (mat == null || mat.empty()) {
            System.out.println("Hata: Görüntülenecek resim boş! (" + title + ")");
            return;
        }

        try {
            // Mevcut ImageUtils sınıfını kullanarak dönüşüm yapıyoruz
            byte[] imageData = ImageUtils.matToBytes(mat);
            ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
            BufferedImage bufferedImage = ImageIO.read(bais);

            // Pencereyi (Frame) oluştur
            JFrame frame = new JFrame(title);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            // Resmi ekle
            ImageIcon icon = new ImageIcon(bufferedImage);
            JLabel label = new JLabel(icon);

            frame.add(label);
            frame.pack(); // Pencereyi resim boyutuna oturt
            frame.setLocationRelativeTo(null); // Pencereyi ekranın ortasında aç
            frame.setVisible(true);

        } catch (Exception e) {
            System.err.println("Görüntüleme hatası: " + e.getMessage());
        }
    }
}