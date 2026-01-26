package photoeditor.MaskProcessors;

import org.opencv.core.Mat;

public class MaskBlender {

    /**
     * Orijinal resim ile filtrelenmiş resmi maske verisine göre birleştirir.
     * Sonuç, 'filtered' (hedef) matrisinin üzerine yazılır.
     *
     * @param original Değişmemiş, ham resim (Backing Store)
     * @param filtered Üzerine efekt uygulanmış resim (Destination)
     * @param mask     Maske verisi (0.0 - 1.0)
     */
    public static void blend(Mat original, Mat filtered, Mask mask) {
        int rows = original.rows();
        int cols = original.cols();
        int channels = original.channels();

        // Boyut kontrolü
        if (mask.getWidth() != cols || mask.getHeight() != rows) {
            System.err.println("HATA: Maske boyutu resimle uyuşmuyor! İşlem iptal edildi.");
            return;
        }

        // OpenCV Mat verisini byte dizilerine dök (Hızlı Erişim için)
        int totalBytes = (int) (original.total() * channels);
        byte[] orgBuff = new byte[totalBytes];
        byte[] filtBuff = new byte[totalBytes];
        // Sonucu direkt filtered üzerine yazacağımız için ekstra bir diziye gerek yok ama okuma kolaylığı için kullanabiliriz

        original.get(0, 0, orgBuff);
        filtered.get(0, 0, filtBuff);

        // Piksel piksel gez ve karıştır
        for (int i = 0; i < totalBytes; i += channels) {
            // Koordinat hesapla (Düz diziden x,y bulma)
            int pixelIndex = i / channels;
            int x = pixelIndex % cols;
            int y = pixelIndex / cols;

            float maskVal = mask.getIntensity(x, y);

            // Optimizasyon: Maske tam beyazsa (1.0) zaten filtrelidir, dokunma.
            // Sadece maskenin 1.0 olmadığı yerlerde orijinal veriyi geri getirmemiz lazım.

            if (maskVal < 0.99f) {
                for (int c = 0; c < channels; c++) {
                    double valOrg = orgBuff[i + c] & 0xFF;     // Orijinal Piksel
                    double valFilt = filtBuff[i + c] & 0xFF;   // Filtreli Piksel

                    // Formül: Org * (1-m) + Filt * m
                    double blended = valOrg * (1.0 - maskVal) + valFilt * maskVal;

                    // Sonucu filtreli buffer'a yaz (filtered resim güncellenir)
                    filtBuff[i + c] = (byte) blended;
                }
            }
        }

        // Güncellenmiş veriyi Mat nesnesine geri yükle
        filtered.put(0, 0, filtBuff);
    }
}