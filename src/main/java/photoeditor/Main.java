package photoeditor;

import photoeditor.core.ImageProcessor;
import photoeditor.utils.ImageUtils;
import photoeditor.utils.ImageViewer;
import photoeditor.utils.LibraryLoader;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

public class Main {
    public static void main(String[] args) {
        LibraryLoader.load();

        Mat rawImage = Imgcodecs.imread("blonde.jpg");
        if(rawImage.empty()) return;

        byte[] inputBytes = ImageUtils.matToBytes(rawImage);
        ImageProcessor processor = new ImageProcessor(inputBytes);

        // --- TEST SENARYOSU ---
        long startTime = System.currentTimeMillis(); // Performans ölçümü için zaman tut

        processor
                .addSaturation(1.5)    // Biraz renkleri canlandır
                .rotate(30, 255, 0, 0);;    // 30 piksellik karelerle mozaik yap (Paralel çalışacak)

        long endTime = System.currentTimeMillis();
        System.out.println("İşlem Süresi: " + (endTime - startTime) + " ms");
        // ----------------------


        Mat finalImage = ImageUtils.bytesToMat(processor.process());
        ImageViewer.show(rawImage, "Orijinal");
        ImageViewer.show(finalImage, "Pikselleştirilmiş (Paralel İşlem)");
    }

}