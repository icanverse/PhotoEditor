package photoeditor;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Core;

// SENİN SINIFLARIN (Paket isimleri son yapına göre ayarlandı)
import photoeditor.filters.AdaptiveFilters;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime) // Çıktıyı milisaniye (ms) olarak ver
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1, warmups = 0) // Hızlı sonuç için ayarlar minimize edildi
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
public class ImageProcessorBenchmark {

    // --- TEST PARAMETRELERİ ---
    // 2000px (4MP) ve 4000px (16MP) resimlerle test et
    @Param({"2000", "4000"})
    public int imageSize;

    private Mat sourceMat;
    private Mat destMat;

    // Test edilecek filtre motoru
    private AdaptiveFilters filters;

    // --- KURULUM (Her testten önce 1 kez çalışır) ---
    @Setup(Level.Trial)
    public void setup() {
        // OpenCV'yi yükle
        nu.pattern.OpenCV.loadLocally();

        // 1. Rastgele piksellerden oluşan kaynak resim yarat
        sourceMat = Mat.zeros(imageSize, imageSize, CvType.CV_8UC3);
        Core.randu(sourceMat, 0, 255);

        // 2. Hedef matrisi önceden ayır (Allocation süresini ölçüme katmamak için)
        destMat = new Mat(imageSize, imageSize, CvType.CV_8UC3);

        // 3. Filtre sınıfını başlat
        filters = new AdaptiveFilters();
    }

    // --- TEMİZLİK (Her testten sonra çalışır) ---
    @TearDown(Level.Trial)
    public void tearDown() {
        if (sourceMat != null) sourceMat.release();
        if (destMat != null) destMat.release();
    }

    // =======================================================
    // BENCHMARK SENARYOLARI
    // =======================================================

    // 1. SENARYO: Analizsiz, Sabit Filtre (Referans Hız)
    // Sadece ParallelProcessor ile boyama işlemi yapar.
    //@Benchmark
//    public void testCandleFilter() {
//        filters.applyCandleEffect(sourceMat, destMat);
//    }

    // 2. SENARYO: Analizli, Akıllı Filtre (Test Edilen)
    // Önce 50x50 resimde K-Means analizi yapar, rengi bulur, sonra boyar.
    // Beklenti: CandleFilter süresine çok yakın olması.
    @Benchmark
    public void testAtmosphereFilter() {
        // %25 yoğunlukla uygula
        filters.applyAtmosphereFilter(sourceMat, destMat, 0.25);
    }

    // --- MAIN ---
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ImageProcessorBenchmark.class.getSimpleName())
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}