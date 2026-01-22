package photoeditor.filters;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import photoeditor.core.ImageAnalysis; // Analiz sınıfını import ettik
import photoeditor.utils.ColorSpaceConverter;

import java.util.ArrayList;
import java.util.List;

public class BasicFilters {

    /// Parlaklık ve Kontrast
    public static Mat adjustBrightnessContrast(Mat source, double alpha, double beta) {
        Mat destination = new Mat();
        source.convertTo(destination, -1, alpha, beta);
        return destination;
    }

    /// SB Çevirme ::: Tek Kanallı Görsele Uygulanamaz
    public static Mat toGrayscale(Mat source, ImageAnalysis analysis) {
        if (analysis != null && analysis.getChannels() == 1) {
            System.out.println(">> Bilgi: Resim zaten Siyah-Beyaz, işlem atlandı.");
            return source;
        }

        Mat destination = new Mat();
        Imgproc.cvtColor(source, destination, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(destination, destination, Imgproc.COLOR_GRAY2BGR); // Formatı koru
        return destination;
    }

    /// Doygunluk ::: SB Görsele Uygulanamaz
    public static Mat adjustSaturation(Mat source, ImageAnalysis analysis, double value) {
        if (analysis != null && analysis.getChannels() == 1) {
            System.out.println(">> Uyarı: Siyah-Beyaz resmin doygunluğu değiştirilemez!");
            return source;
        }

        Mat workingImage = ColorSpaceConverter.bgrToHsv(source);

        List<Mat> channels = new ArrayList<>();
        Core.split(workingImage, channels);

        channels.get(1).convertTo(channels.get(1), -1, value, 0);

        Core.merge(channels, workingImage);

        return ColorSpaceConverter.hsvToBgr(workingImage);
    }
}