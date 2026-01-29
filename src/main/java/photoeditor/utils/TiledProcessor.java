package photoeditor.utils;

import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Görüntü işleme operasyonlarını "Tiling" (Parçalama) yöntemiyle paralel çalıştırır.
 * Kenar artefaktlarını (çizgileri) önlemek için Ghost Border (Padding) stratejisini uygular.
 */
public class TiledProcessor {

    // İşlemci çekirdek sayısı kadar thread açar
    private static final int CORES = Runtime.getRuntime().availableProcessors();
    private static final ExecutorService pool = Executors.newFixedThreadPool(CORES);

    /**
     * İşlenecek parçayı temsil eden arayüz.
     * Kullanıcı bu metoda sadece "giriş matrisi -> çıkış matrisi" mantığını yazar.
     */
    public interface TileOperation {
        void process(Mat inputChunk, Mat outputChunk);
    }

    /**
     * Resmi yatay şeritlere bölerek paralel işler.
     * @param source      Kaynak Matris
     * @param destination Hedef Matris (Sonuç buraya yazılır)
     * @param padding     Kenar payı (Kernel yarıçapı kadar olmalı. Örn: Blur sigma=5 ise padding=13)
     * @param operation   Yapılacak işlem (Lambda expression)
     */
    public static void apply(Mat source, Mat destination, int padding, TileOperation operation) {
        // Hedef matris kaynakla aynı boyutta ve tipte değilse oluştur
        if (destination.empty() || source.size().width != destination.size().width || source.size().height != destination.size().height) {
            destination.create(source.size(), source.type());
        }

        int totalHeight = source.rows();
        // Eğer resim çok küçükse tek seferde yap geç (Thread maliyetine değmez)
        if (totalHeight < 500) {
            operation.process(source, destination);
            return;
        }

        int chunkHeight = totalHeight / CORES;
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < CORES; i++) {
            // 1. Bu thread'in sorumlu olduğu ASIL (Temiz) alan
            final int startRow = i * chunkHeight;
            final int endRow = (i == CORES - 1) ? totalHeight : (startRow + chunkHeight);

            futures.add(pool.submit(() -> {
                try {
                    // Padding hesapla (Üst ve alt sınırlara taşmamaya dikkat et)
                    int padTop = Math.max(0, startRow - padding);
                    int padBottom = Math.min(totalHeight, endRow + padding);

                    // Okuma yapacağımız Genişletilmiş Alan (Source üzerinden)
                    // submat(rowStart, rowEnd, colStart, colEnd)
                    Mat srcTile = source.submat(padTop, padBottom, 0, source.cols());

                    // İşlem sonucu için geçici matris
                    Mat processedTile = new Mat();

                    // Kullanıcının İşlemini Çalıştır
                    // (Blur, Sharpen vs. burada yapılır)
                    operation.process(srcTile, processedTile);

                    // Kırpma (Cropping) - Fazlalık padding'i atıyoruz
                    // startRow ile padTop arasındaki fark, üstten ne kadar kesmemiz gerektiğini söyler
                    int localRowStart = startRow - padTop;
                    int localRowEnd = localRowStart + (endRow - startRow);

                    // Geçerli (temiz) alanı kesip alıyoruz
                    Mat validArea = processedTile.submat(localRowStart, localRowEnd, 0, processedTile.cols());

                    // Hedef matrise doğru konuma yapıştır
                    Mat destRoi = destination.submat(startRow, endRow, 0, destination.cols());
                    validArea.copyTo(destRoi);

                    // Bellek Temizliği (Java GC'yi bekleme, OpenCV bellek yiyor)
                    srcTile.release();
                    processedTile.release();
                    validArea.release();
                    destRoi.release();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));
        }

        // Tüm parçaların bitmesini bekle
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                throw new RuntimeException("Tiled Processing Hatası: " + e.getMessage());
            }
        }
    }
}