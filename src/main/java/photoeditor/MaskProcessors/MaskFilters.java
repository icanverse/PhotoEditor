package photoeditor.MaskProcessors;

public class MaskFilters {

    /**
     * İki nokta arasında doğrusal bir geçiş (Linear Gradient) oluşturur.
     * @param width Resim genişliği
     * @param height Resim yüksekliği
     * @param startX Başlangıç X (Maskenin %100 olduğu yer)
     * @param startY Başlangıç Y
     * @param endX Bitiş X (Maskenin %0 olduğu yer)
     * @param endY Bitiş Y
     * @return Doldurulmuş Mask nesnesi
     */
    public static Mask createLinearGradient(int width, int height, int startX, int startY, int endX, int endY) {
        Mask mask = new Mask(width, height);

        // Vektör hesaplamaları (Start -> End vektörü)
        float dx = endX - startX;
        float dy = endY - startY;
        float lengthSquared = dx * dx + dy * dy;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // Mevcut pikselin başlangıç noktasına göre konumu
                float px = x - startX;
                float py = y - startY;

                // Nokta çarpımı (Dot Product) ile projeksiyon hesaplama
                // Bu formül pikselin çizgi üzerinde nerede olduğunu (0.0 ile 1.0 arası) bulur.
                float t = (px * dx + py * dy) / lengthSquared;

                // Değeri 0 ile 1 arasına sıkıştır (Clamp) ve ters çevir
                // Çünkü Start noktasında etki tam (1.0), End noktasında hiç yok (0.0) olmalı.
                float intensity = 1.0f - Math.max(0.0f, Math.min(1.0f, t));

                mask.setIntensity(x, y, intensity);
            }
        }
        return mask;
    }

    /**
     * Bir merkezden dışarı doğru azalan dairesel (Radial) maske oluşturur.
     * @param radius Maskenin etki yarıçapı
     */
    public static Mask createRadialGradient(int width, int height, int centerX, int centerY, float radius) {
        Mask mask = new Mask(width, height);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // Merkezden uzaklığı hesapla (Pisagor: a^2 + b^2 = c^2)
                float dist = (float) Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));

                // Merkeze yakınsa 1.0, uzaklaştıkça 0.0'a düşer
                float intensity = 1.0f - (dist / radius);

                // Değeri sınırla
                mask.setIntensity(x, y, Math.max(0.0f, intensity));
            }
        }
        return mask;
    }
}