package photoeditor.MaskProcessors;

public class Mask {
    private float[][] intensity;
    private int width, height;

    public Mask(int width, int height) {
        this.width = width;
        this.height = height;
        this.intensity = new float[width][height];
    }

    public void setIntensity(int x, int y, float value) {
        if (isValid(x, y)) {
            intensity[x][y] = Math.max(0.0f, Math.min(1.0f, value));
        }
    }

    public float getIntensity(int x, int y) {
        if (isValid(x, y)) {
            return intensity[x][y];
        }
        return 0.0f;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    // Sınır kontrolü (Hata almamak için)
    private boolean isValid(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    /**
     * Maske üzerine fırça darbesi ekler.
     * @param centerX Fırçanın dokunduğu X koordinatı
     * @param centerY Fırçanın dokunduğu Y koordinatı
     * @param radius Fırçanın büyüklüğü (yarıçap)
     * @param hardness Fırçanın sertliği (0.0 = Çok yumuşak, 1.0 = Keskin)
     */
    public void addBrushStroke(int centerX, int centerY, float radius, float hardness) {
        // İşlem yükünü azaltmak için sadece fırçanın etki alanını tarıyoruz
        int minX = Math.max(0, (int) (centerX - radius));
        int maxX = Math.min(width, (int) (centerX + radius));
        int minY = Math.max(0, (int) (centerY - radius));
        int maxY = Math.min(height, (int) (centerY + radius));

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                // Merkezden uzaklığı hesapla
                double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));

                // Eğer piksel fırça dairesinin içindeyse
                if (distance < radius) {
                    float currentIntensity = 0;

                    if (hardness >= 0.99f) {
                        currentIntensity = 1.0f; // Tam sert fırça
                    } else {
                        // Merkezden dışarı doğru azalan etki (Soft Brush)
                        float normalizedDist = (float) (distance / radius);
                        currentIntensity = 1.0f - normalizedDist;

                        // Hardness faktörünü uygula (İsteğe bağlı basit yaklaşım)
                        currentIntensity = Math.min(1.0f, currentIntensity + hardness * 0.5f);
                    }

                    // Değeri 0 ile 1 arasına sıkıştır
                    currentIntensity = Math.max(0.0f, Math.min(1.0f, currentIntensity));

                    // ÖNEMLİ: Mevcut maske değeriyle karşılaştırıp büyük olanı al (Üst üste boyama)
                    if (isValid(x, y)) {
                        float oldVal = intensity[x][y];
                        intensity[x][y] = Math.max(oldVal, currentIntensity);
                    }
                }
            }
        }
    }

    // Mevcut Mask.java sınıfının içine ekle:

    /**
     * Maskeden belirli bir alanı siler (Eksiye inme / Silgi).
     */
    public void removeBrushStroke(int centerX, int centerY, float radius, float hardness) {
        int minX = Math.max(0, (int) (centerX - radius));
        int maxX = Math.min(width, (int) (centerX + radius));
        int minY = Math.max(0, (int) (centerY - radius));
        int maxY = Math.min(height, (int) (centerY + radius));

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));
                if (distance < radius) {
                    float currentIntensity = 0;
                    if (hardness >= 0.99f) {
                        currentIntensity = 1.0f;
                    } else {
                        float normalizedDist = (float) (distance / radius);
                        currentIntensity = 1.0f - normalizedDist;
                        currentIntensity = Math.min(1.0f, currentIntensity + hardness * 0.5f);
                    }

                    // MANTIK DEĞİŞİKLİĞİ: Burada "max" yerine çıkarma işlemi yapıyoruz
                    if (isValid(x, y)) {
                        float oldVal = intensity[x][y];
                        // Mevcut değerden fırça etkisini çıkar
                        float newVal = oldVal - currentIntensity;
                        // 0'ın altına düşmesini engelle
                        intensity[x][y] = Math.max(0.0f, newVal);
                    }
                }
            }
        }
    }

    /**
     * OpenCV Mat nesnesini (Grayscale) bu Mask sınıfına dönüştürür.
     * AI modellerinden gelen çıktıyı buraya aktarmak için gereklidir.
     */
    public void setFromMat(org.opencv.core.Mat mat) {
        if (mat.rows() != height || mat.cols() != width) {
            // Gerekirse resize yapılabilir ama şimdilik uyarı verelim
            System.err.println("Boyut uyuşmazlığı! Matris maskeye sığdırılamadı.");
            return;
        }

        // Matris verisini byte olarak al (CV_8UC1 varsayıyoruz)
        byte[] data = new byte[width * height];
        mat.get(0, 0, data);

        for (int i = 0; i < data.length; i++) {
            int x = i % width;
            int y = i / width;
            // 0-255 arası değeri 0.0-1.0 arasına çek
            float val = (data[i] & 0xFF) / 255.0f;
            intensity[x][y] = val;
        }
    }
}