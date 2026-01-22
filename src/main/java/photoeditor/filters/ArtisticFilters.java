package photoeditor.filters;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import photoeditor.utils.ParallelProcessor;

public class ArtisticFilters {
    /**
     * @param source    İşlenecek resim
     * @param pixelSize Karelerin boyutu
     */
    public static void applyPixelate(Mat source, int pixelSize) {
        if (pixelSize < 2) return;

        int rows = source.rows();
        int cols = source.cols();

        // --- PARALEL İŞLEM MOTORUNU ÇALIŞTIR ---
        ParallelProcessor.splitAndRun(rows, (startRow, endRow) -> {

            int alignedStartRow = (startRow / pixelSize) * pixelSize;

            for (int y = alignedStartRow; y < endRow; y += pixelSize) {
                for (int x = 0; x < cols; x += pixelSize) {

                    int blockHeight = Math.min(pixelSize, rows - y);
                    int blockWidth = Math.min(pixelSize, cols - x);

                    // O bölgeyi (ROI - Region of Interest) seç
                    Rect rect = new Rect(x, y, blockWidth, blockHeight);

                    // Submat: Ana resmin o bölgesine bakan bir pencere açar (Kopyalama yapmaz, hafıza dostudur)
                    Mat blockROI = source.submat(rect);

                    // O bölgenin ortalama rengini hesapla
                    Scalar meanColor = Core.mean(blockROI);

                    // 4. O bölgeyi hesaplanan ortalama renkle doldur
                    // -1 parametresi "içini tamamen doldur" demektir.
                    Imgproc.rectangle(source, rect, meanColor, -1);

                    blockROI.release();
                }
            }
        });
    }
}