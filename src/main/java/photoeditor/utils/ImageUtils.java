package photoeditor.utils;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

public class ImageUtils {

    // Byte dizisini Mat nesnesine çevirir (Decode)
    public static Mat bytesToMat(byte[] imageData) {
        return Imgcodecs.imdecode(new MatOfByte(imageData), Imgcodecs.IMREAD_UNCHANGED);
    }

    // Mat nesnesini Byte dizisine çevirir (Encode)
    public static byte[] matToBytes(Mat image) {
        MatOfByte matOfByte = new MatOfByte();
        // İşlenmiş resmi PNG formatında byte dizisine çevir
        Imgcodecs.imencode(".png", image, matOfByte);
        return matOfByte.toArray();
    }
}