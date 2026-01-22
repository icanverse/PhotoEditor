package photoeditor.utils;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class ColorSpaceConverter {

    public static Mat bgrToHsv(Mat source) {
        Mat destination = new Mat();
        Imgproc.cvtColor(source, destination, Imgproc.COLOR_BGR2HSV);
        return destination;
    }

    public static Mat hsvToBgr(Mat source) {
        Mat destination = new Mat();
        Imgproc.cvtColor(source, destination, Imgproc.COLOR_HSV2BGR);
        return destination;
    }

    public static Mat bgrToLab(Mat source) {
        Mat destination = new Mat();
        Imgproc.cvtColor(source, destination, Imgproc.COLOR_BGR2Lab);
        return destination;
    }

    public static Mat labToBgr(Mat source) {
        Mat destination = new Mat();
        Imgproc.cvtColor(source, destination, Imgproc.COLOR_Lab2BGR);
        return destination;
    }

}