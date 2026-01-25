package photoeditor.filters;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import photoeditor.utils.ParallelProcessor;

import java.util.ArrayList;
import java.util.List;

public class ArtisticFilters {

    /**
     * Pikselleştirme Efekti (CPU Parallel)
     * Java döngüleri ile yapıldığı için ParallelProcessor kullanmak performansı artırır.
     * @param source    İşlenecek resim
     * @param pixelSize Karelerin boyutu
     */

    public static void applyPixelate(Mat source, int pixelSize) {
        if (pixelSize < 2) return;

        int rows = source.rows();
        int cols = source.cols();

        ParallelProcessor.splitAndRun(rows, (startRow, endRow) -> {
            int alignedStartRow = (startRow / pixelSize) * pixelSize;
            if (alignedStartRow >= rows) return;

            for (int y = alignedStartRow; y < endRow; y += pixelSize) {
                for (int x = 0; x < cols; x += pixelSize) {

                    // Resim sınırlarını aşmamak için kontrol
                    int blockHeight = Math.min(pixelSize, rows - y);
                    int blockWidth = Math.min(pixelSize, cols - x);

                    Rect rect = new Rect(x, y, blockWidth, blockHeight);
                    Mat blockROI = source.submat(rect);
                    Scalar meanColor = Core.mean(blockROI);
                    Imgproc.rectangle(source, rect, meanColor, -1);
                    blockROI.release();
                }
            }
        });
    }

    /**
     * Sepya Efekti (Native Parallel)
     * @param source    İşlenecek resim
     */
    public static void applySepia(Mat source) {
        Mat sepiaKernel = new Mat(3, 3, CvType.CV_32F);
        // BGR sırasına göre Sepya katsayıları
        sepiaKernel.put(0, 0, 0.272, 0.534, 0.131);
        sepiaKernel.put(1, 0, 0.349, 0.686, 0.168);
        sepiaKernel.put(2, 0, 0.393, 0.769, 0.189);

        Mat destination = new Mat();
        // Native optimize dönüşüm
        Core.transform(source, destination, sepiaKernel);

        // Sonucu orijinal matrise geri kopyala (In-place modification)
        destination.copyTo(source);

        // Bellek temizliği
        destination.release();
        sepiaKernel.release();
    }

    /**
     * Vignette (Kenar Karartma)
     * Gauss Kernel çarpımı ile yapılır.
     * @param source    İşlenecek resim
     * @param intensity Vinyet için katsayı
     */
    public static void applyVignette(Mat source, double intensity) {
        int rows = source.rows();
        int cols = source.cols();

        double sigmaX = cols / (1.5 * intensity);
        double sigmaY = rows / (1.5 * intensity);

        Mat kernelX = Imgproc.getGaussianKernel(cols, sigmaX, CvType.CV_32F);
        Mat kernelY = Imgproc.getGaussianKernel(rows, sigmaY, CvType.CV_32F);

        Mat kernel = new Mat();
        Core.gemm(kernelY, kernelX.t(), 1, new Mat(), 0, kernel);

        Core.normalize(kernel, kernel, 0, 1, Core.NORM_MINMAX);

        List<Mat> maskChannels = new ArrayList<>();
        maskChannels.add(kernel);
        maskChannels.add(kernel);
        maskChannels.add(kernel);
        Mat colorMask = new Mat();
        Core.merge(maskChannels, colorMask);

        Mat sourceFloat = new Mat();
        source.convertTo(sourceFloat, CvType.CV_32F);

        Core.multiply(sourceFloat, colorMask, sourceFloat);

        sourceFloat.convertTo(source, CvType.CV_8UC3);

        // Temizlik
        kernelX.release();
        kernelY.release();
        kernel.release();
        colorMask.release();
        sourceFloat.release();
    }
}