package photoeditor.utils;

@FunctionalInterface
public interface ImageTask {
    /**
     * @param startRow İşlemin başlayacağı Y kordinatı
     * @param endRow   İşlemin biteceği Y kordinatı
     */
    void execute(int startRow, int endRow);
}