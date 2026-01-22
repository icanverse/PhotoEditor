package photoeditor.utils;

import nu.pattern.OpenCV;

public class LibraryLoader {
    private static boolean isLoaded = false;

    public static void load() {
        if (!isLoaded) {
            try {
                // OpenPnP sayesinde DLL/SO dosya yolu aramadan yükler
                OpenCV.loadLocally();
                isLoaded = true;
                System.out.println("OpenCV başarıyla yüklendi.");
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("OpenCV yüklenemedi! Hata: " + e.getMessage());
            }
        }
    }
}