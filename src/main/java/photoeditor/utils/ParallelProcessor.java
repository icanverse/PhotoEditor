package photoeditor.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ParallelProcessor {

    // Bilgisayarın çekirdek sayısını bulur
    private static final int CORES = Runtime.getRuntime().availableProcessors();

    // İş parçacığı havuzu (Thread Pool)
    // Bir kere oluşturulur, program kapanana kadar hazırolda bekler.
    private static final ExecutorService pool = Executors.newFixedThreadPool(CORES);

    /**
     * @param totalHeight Resmin yüksekliği (source.rows())
     * @param task        Yapılacak işlem (Lambda fonksiyonu)
     */

    public static void splitAndRun(int totalHeight, ImageTask task) {
        /// Resim küçükse thread açmaya değmez
        if (totalHeight < 100) {
            task.execute(0, totalHeight);
            return;
        }

        int chunkHeight = totalHeight / CORES;

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < CORES; i++) {
            final int startRow = i * chunkHeight;
            final int endRow = (i == CORES - 1) ? totalHeight : (startRow + chunkHeight);

            // Havuza işi gönder
            futures.add(pool.submit(() -> {
                task.execute(startRow, endRow);
            }));
        }

        // Senkronluk için bekle
        for (Future<?> future : futures) {
            try {
                future.get(); // İş bitene kadar burada bekler
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Paralel işlem sırasında hata oluştu: " + e.getMessage());
            }
        }
    }
}