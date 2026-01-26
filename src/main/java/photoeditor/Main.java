package photoeditor;

import photoeditor.core.ImageProcessor;
import photoeditor.utils.NativeLibraryLoader;
import nu.pattern.OpenCV; // Sadece BURADA import ediyoruz!

public class Main {
    public static void main(String[] args) {

        // ÖNCE: ImageProcessor'a yükleme işini nasıl yapacağını öğretiyoruz
        ImageProcessor.setNativeLoader(new NativeLibraryLoader() {
            @Override
            public void loadLibrary() {
                // Masaüstü için yükleme komutu buraya gizlendi
                OpenCV.loadLocally();
                System.out.println("Desktop OpenCV yüklendi!");
            }
        });

    }
}