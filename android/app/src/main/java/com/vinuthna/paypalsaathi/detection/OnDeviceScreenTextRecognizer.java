package com.vinuthna.paypalsaathi.detection;

import android.graphics.Bitmap;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;

import java.util.concurrent.Executor;

public final class OnDeviceScreenTextRecognizer implements AutoCloseable {
    public interface Callback {
        void onSuccess(String safeScreenHints);
        void onFailure();
    }

    private final TextRecognizer latinRecognizer =
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final TextRecognizer devanagariRecognizer =
            TextRecognition.getClient(
                    new DevanagariTextRecognizerOptions.Builder().build());

    public void recognize(
            Bitmap bitmap,
            Executor completionExecutor,
            Callback callback
    ) {
        if (bitmap == null || bitmap.isRecycled() || callback == null) {
            if (callback != null) {
                callback.onFailure();
            }
            return;
        }
        InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
        Task<Text> latin = latinRecognizer.process(inputImage);
        Task<Text> devanagari = devanagariRecognizer.process(inputImage);
        Tasks.whenAllComplete(latin, devanagari)
                .addOnCompleteListener(completionExecutor, ignored -> {
                    if (!latin.isSuccessful() && !devanagari.isSuccessful()) {
                        callback.onFailure();
                        return;
                    }
                    StringBuilder recognized = new StringBuilder();
                    appendResult(recognized, latin);
                    appendResult(recognized, devanagari);
                    String safeHints =
                            ScreenDataSanitizer.extractVisualScreenHints(
                                    recognized.toString());
                    if (safeHints.isEmpty()) {
                        callback.onFailure();
                    } else {
                        callback.onSuccess(safeHints);
                    }
                });
    }

    private static void appendResult(StringBuilder output, Task<Text> task) {
        if (!task.isSuccessful() || task.getResult() == null
                || output.length() >= 4_000) {
            return;
        }
        for (Text.TextBlock block : task.getResult().getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                if (output.length() >= 4_000) {
                    return;
                }
                output.append(' ').append(line.getText());
            }
        }
    }

    @Override
    public void close() {
        latinRecognizer.close();
        devanagariRecognizer.close();
    }
}
