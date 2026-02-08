package photoeditor.MaskProcessors;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.FloatBuffer;
import java.util.Collections;

public class SmartMaskFactory {
    private final OrtEnvironment env;
    private final OrtSession session;
    // Modelin giriş boyutu (DeepLabV3 için standart)
    private static final int MODEL_SIZE = 320;

    // SmartMaskFactory constructor'ına güvenlik ekle
    public SmartMaskFactory(String modelPath) throws Exception {
        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            throw new FileNotFoundException("Model dosyası bulunamadı: " + modelPath);
        }
        this.env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(modelPath, new OrtSession.SessionOptions());
    }

    public Mask createPersonMask(Mat originalImage, int softness) {
        try {
            // 1. HAZIRLIK: Resmi model boyutuna (320x320) getir
            Mat resized = new Mat();
            Imgproc.resize(originalImage, resized, new Size(MODEL_SIZE, MODEL_SIZE));
            Imgproc.cvtColor(resized, resized, Imgproc.COLOR_BGR2RGB);

            // 2. NORMALİZASYON: ImageNet standartları
            float[] floatData = normalizeImage(resized);
            long[] shape = {1, 3, MODEL_SIZE, MODEL_SIZE};
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatData), shape);

            // 3. AI ÇALIŞTIRMA (Inference)
            String inputName = session.getInputNames().iterator().next();
            OrtSession.Result result = session.run(Collections.singletonMap(inputName, inputTensor));
            String outputName = session.getOutputNames().iterator().next();
            float[][][][] outputData = (float[][][][]) result.get(outputName).get().getValue();

            // 4. ARGMAX: Küçük maskeyi oluştur (Sınıf 15 = İnsan)
            Mask maskSmall = new Mask(MODEL_SIZE, MODEL_SIZE);
            for (int y = 0; y < MODEL_SIZE; y++) {
                for (int x = 0; x < MODEL_SIZE; x++) {
                    int bestClass = 0;
                    float maxScore = -Float.MAX_VALUE;
                    for (int c = 0; c < 21; c++) {
                        float score = outputData[0][c][y][x];
                        if (score > maxScore) {
                            maxScore = score;
                            bestClass = c;
                        }
                    }
                    if (bestClass == 15) maskSmall.setIntensity(x, y, 1.0f);
                }
            }

            // 5. BÜYÜTME: Küçük maskeyi orijinal resim boyutuna getir
            Mat smallMat = convertMaskToMat(maskSmall);
            Mat largeMat = new Mat();
            Imgproc.resize(smallMat, largeMat, originalImage.size(), 0, 0, Imgproc.INTER_LINEAR);

            // 6. HİBRİT ADIM: AI maskesini Mask nesnesine çevir ve Refiner'a gönder
            Mask rawAiMask = new Mask(originalImage.width(), originalImage.height());
            rawAiMask.setFromMat(largeMat);

            // Burası sihrin gerçekleştiği yer: Sobel ile kenarları hizalıyoruz
            Mask refinedMask = MaskRefiner.refineWithEdges(rawAiMask, originalImage);

            // 7. YUMUŞATMA (Feathering): Kullanıcının istediği softness değerini uygula
            if (softness > 0) {
                if (softness % 2 == 0) softness++; // Kernel tek sayı olmalı

                // Maskeyi tekrar Mat'a çevirip blur uyguluyoruz
                Mat refinedMat = convertMaskToMat(refinedMask);
                Imgproc.GaussianBlur(refinedMat, refinedMat, new Size(softness, softness), 0);

                // Sonucu tekrar Mask nesnesine yükle
                refinedMask.setFromMat(refinedMat);
                refinedMat.release();
            }

            // 8. TEMİZLİK
            smallMat.release();
            largeMat.release();
            resized.release();
            inputTensor.close();
            result.close();

            return refinedMask;

        } catch (Exception e) {
            e.printStackTrace();
            return new Mask(originalImage.width(), originalImage.height());
        }
    }


    // --- BAĞIMLILIKLAR (Bunların da sınıf içinde olduğundan emin ol) ---
    // Google ImageNet Normalizasyonu (Modelin gözlüğü)
    private float[] normalizeImage(Mat img) {
        int width = img.width();
        int height = img.height();
        float[] data = new float[1 * 3 * width * height];
        byte[] imgData = new byte[width * height * 3];
        img.get(0, 0, imgData);

        float[] mean = {0.485f, 0.456f, 0.406f};
        float[] std = {0.229f, 0.224f, 0.225f};

        for (int i = 0; i < width * height; i++) {
            for (int c = 0; c < 3; c++) {
                float val = (imgData[i * 3 + c] & 0xFF) / 255.0f;
                // (Değer - Ortalama) / StandartSapma
                data[c * width * height + i] = (val - mean[c]) / std[c];
            }
        }
        return data;
    }

    // Yardımcı: Mask -> Mat (Resize işlemi için)
    private Mat convertMaskToMat(Mask mask) {
        Mat m = new Mat(mask.getHeight(), mask.getWidth(), CvType.CV_8UC1);
        byte[] data = new byte[mask.getWidth() * mask.getHeight()];
        for(int i=0; i<data.length; i++) {
            int x = i % mask.getWidth();
            int y = i / mask.getWidth();
            data[i] = (byte)(mask.getIntensity(x, y) * 255);
        }
        m.put(0,0, data);
        return m;
    }
}