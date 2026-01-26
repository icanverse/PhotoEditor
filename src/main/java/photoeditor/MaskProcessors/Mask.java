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
}