package com.example.s;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public class FaceNetModel {

    private Interpreter interpreter;

    private static final int INPUT_IMAGE_SIZE = 160;
    private static final int EMBEDDING_SIZE = 512;
    public FaceNetModel(Context context, String modelName) throws IOException {

        AssetFileDescriptor fileDescriptor =
                context.getAssets().openFd(modelName);

        FileInputStream inputStream =
                new FileInputStream(fileDescriptor.getFileDescriptor());

        FileChannel fileChannel = inputStream.getChannel();

        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();

        ByteBuffer modelBuffer =
                fileChannel.map(FileChannel.MapMode.READ_ONLY,
                        startOffset,
                        declaredLength);

        interpreter = new Interpreter(modelBuffer);
    }

    public float[] getFaceEmbedding(Bitmap bitmap) {

        Bitmap resized =
                Bitmap.createScaledBitmap(bitmap,
                        INPUT_IMAGE_SIZE,
                        INPUT_IMAGE_SIZE,
                        true);

        ByteBuffer inputBuffer =
                ByteBuffer.allocateDirect(
                        4 * INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE * 3);

        inputBuffer.order(ByteOrder.nativeOrder());

        int[] pixels =
                new int[INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE];

        resized.getPixels(
                pixels,
                0,
                INPUT_IMAGE_SIZE,
                0,
                0,
                INPUT_IMAGE_SIZE,
                INPUT_IMAGE_SIZE
        );

        int pixel = 0;

        for (int i = 0; i < INPUT_IMAGE_SIZE; i++) {
            for (int j = 0; j < INPUT_IMAGE_SIZE; j++) {

                int value = pixels[pixel++];

                inputBuffer.putFloat(((value >> 16) & 0xFF) / 255f);
                inputBuffer.putFloat(((value >> 8) & 0xFF) / 255f);
                inputBuffer.putFloat((value & 0xFF) / 255f);
            }
        }

        inputBuffer.rewind();   // 🔥 IMPORTANT FIX

        float[][] output = new float[1][EMBEDDING_SIZE];

        interpreter.run(inputBuffer, output);

        return output[0];
    }
}

