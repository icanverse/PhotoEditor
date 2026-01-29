package photoeditor.utils;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class PsdWriter {

    // Doğrudan dosyaya kaydetmek için
    public static void save(Mat image, String filePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            writeStream(image, fos);
        }
    }

    // Byte dizisi olarak almak için (API dönüşleri için ideal)
    public static byte[] toBytes(Mat image) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            writeStream(image, baos);
            return baos.toByteArray();
        }
    }

    // Ortak yazma mantığı (Core Logic)
    private static void writeStream(Mat image, OutputStream out) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);

        int width = image.cols();
        int height = image.rows();
        // OpenCV genelde BGR çalışır, kanal sayısını alalım
        int channels = image.channels();

        // --- 1. HEADER KISMI ---
        dos.writeBytes("8BPS");             // Signature
        dos.writeShort(1);                  // Version
        dos.writeBytes("\0\0\0\0\0\0");     // Reserved
        dos.writeShort(3);                  // Channels (RGB için her zaman 3)
        dos.writeInt(height);               // Rows
        dos.writeInt(width);                // Columns
        dos.writeShort(8);                  // Depth (8-bit)
        dos.writeShort(3);                  // Mode (3 = RGB)

        // --- 2. BOŞ VERİ BLOKLARI ---
        dos.writeInt(0); // Color Mode Data Length
        dos.writeInt(0); // Image Resources Length
        dos.writeInt(0); // Layer & Mask Info Length

        // --- 3. GÖRÜNTÜ VERİSİ ---
        dos.writeShort(0); // Compression (0 = Raw, Sıkıştırmasız)

        // OpenCV (BGR Interleaved) -> PSD (RGB Planar) Dönüşümü
        List<Mat> bgrChannels = new ArrayList<>();
        Core.split(image, bgrChannels);

        // PSD sırası: Red -> Green -> Blue
        // OpenCV split sırası: 0=Blue, 1=Green, 2=Red
        int[] writeOrder = {2, 1, 0};

        byte[] rowData = new byte[width]; // Her seferinde bir satır okuyup yazalım (RAM dostu)

        for (int channelIdx : writeOrder) {
            Mat channel = bgrChannels.get(channelIdx);
            for (int r = 0; r < height; r++) {
                channel.get(r, 0, rowData);
                dos.write(rowData);
            }
        }

        // Temizlik
        for (Mat m : bgrChannels) m.release();
    }
}