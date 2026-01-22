package photoeditor.core;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;

public class MetaDataAnalysis {

    private String cameraMake = "Bilinmiyor";
    private String cameraModel = "Bilinmiyor";
    private Date dateTaken = null;
    private Double latitude = null;  // Enlem
    private Double longitude = null; // Boylam
    private String error = null;

    public MetaDataAnalysis(byte[] imageBytes) {
        try (InputStream stream = new ByteArrayInputStream(imageBytes)) {
            // Kütüphane tüm metadata ağacını okur
            Metadata metadata = ImageMetadataReader.readMetadata(stream);

            // 1. KAMERA BİLGİLERİ (Marka/Model)
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null) {
                String make = ifd0.getString(ExifIFD0Directory.TAG_MAKE);
                String model = ifd0.getString(ExifIFD0Directory.TAG_MODEL);
                if (make != null) this.cameraMake = make;
                if (model != null) this.cameraModel = model;
            }

            // 2. TARİH BİLGİSİ
            ExifSubIFDDirectory subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (subIfd != null) {
                // EXIF tarihini Java Date nesnesine çevirir
                this.dateTaken = subIfd.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
            }

            // 3. GPS KONUMU
            GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDir != null) {
                GeoLocation geoLocation = gpsDir.getGeoLocation();
                if (geoLocation != null) {
                    this.latitude = geoLocation.getLatitude();
                    this.longitude = geoLocation.getLongitude();
                }
            }

        } catch (Exception e) {
            this.error = "Metadata okunamadı: " + e.getMessage();
            System.err.println(this.error);
        }
    }

    // --- GETTER METODLARI ---

    public String getCameraInfo() {
        return cameraMake + " " + cameraModel;
    }

    public Date getDateTaken() {
        return dateTaken;
    }

    public String getGpsLocation() {
        if (latitude != null && longitude != null) {
            return latitude + ", " + longitude;
        }
        return "Konum verisi yok";
    }

    // Google Maps linki oluşturur (Opsiyonel Güzellik)
    public String getGoogleMapsLink() {
        if (latitude != null && longitude != null) {
            return "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;
        }
        return null;
    }

    @Override
    public String toString() {
        return "=== METADATA RAPORU ===\n" +
                "Kamera  : " + getCameraInfo() + "\n" +
                "Tarih   : " + (dateTaken != null ? dateTaken.toString() : "Tarih Yok") + "\n" +
                "Konum   : " + getGpsLocation();
    }
}