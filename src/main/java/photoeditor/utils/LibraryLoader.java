package photoeditor.utils;
public class LibraryLoader {
    private static boolean isLoaded = false;

    public static void load() {
        if (isLoaded) {
            return;
        }

        // 1. Önce Android kontrolü yapalım
        String vmName = System.getProperty("java.vm.name");
        boolean isAndroid = vmName != null && vmName.toLowerCase().contains("dalvik");

        if (isAndroid) {
            // Android ortamındayız.
            // Burada hiçbir şey yapmıyoruz çünkü Android tarafında
            // OpenCVLoader.initDebug() arkadaşının projesinde çağrılacak.
            System.out.println("Android ortamı algılandı, yerel yükleme atlanıyor.");
            isLoaded = true;
            return;
        }

        // 2. Desktop ortamındaysak Reflection ile OpenPnP'yi çağırmayı deneyelim
        try {
            // Sınıfı ismen arıyoruz. Eğer kütüphane projede yoksa (exclude edilmişse)
            // ClassNotFoundException düşer ve uygulama çökmez.
            Class<?> openCvClass = Class.forName("nu.pattern.OpenCV");

            // "loadLocally" metodunu bul ve çalıştır
            java.lang.reflect.Method loadMethod = openCvClass.getMethod("loadLocally");
            loadMethod.invoke(null);

            isLoaded = true;
            System.out.println("OpenCV (Desktop) başarıyla yüklendi.");

        } catch (ClassNotFoundException e) {
            // Bu hata arkadaşının bilgisayarında çıkacak ama sorun değil.
            // Çünkü o Android kullanıyor ve pnp kütüphanesini exclude etti.
            // Sadece log basıp geçebiliriz.
            System.out.println("OpenPnP kütüphanesi bulunamadı (Android'de bu normaldir).");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("OpenCV yüklenirken beklenmedik hata: " + e.getMessage());
        }
    }
}