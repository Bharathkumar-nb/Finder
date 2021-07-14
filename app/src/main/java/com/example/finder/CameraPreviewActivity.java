package com.example.finder;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.TextRecognizerOptions;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CameraPreviewActivity extends AppCompatActivity {

    private static final String[] REQUIRED_PERMISSIONS = new String[] {"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String TAG = "DEBUG LOG";
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private String message;
    private final TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private PreviewView cameraPreviewView;
    private ProcessCameraProvider cameraProvider;
    private Preview previewUsecase;
    private CameraSelector cameraSelector;
    private ImageAnalysis imageAnalysisUsecase;
    private long lastNotificationRingtime = System.currentTimeMillis();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Objects.requireNonNull(getSupportActionBar()).hide();
        setContentView(R.layout.activity_camera_preview);
        getLifecycle().addObserver(recognizer);
        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();
        cameraPreviewView = findViewById(R.id.cameraPreviewView);
        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
        message = intent.getStringExtra(MainActivity.EXTRA_MESSAGE);
        Log.d(TAG, "onCreate: " + message);
        if (allPermissionGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private boolean allPermissionGranted() {
        for(String permission: REQUIRED_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                this.finish();
            }
        }
    }

    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraProvider = cameraProviderFuture.get();
                    bindPreview();
                } catch (ExecutionException | InterruptedException e) {
                    // No errors need to be handled for this Future.
                    // This should never be reached.
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview() {
        if (cameraProvider == null) {
            Log.d(TAG, "bindPreview: cameraProvider == null");
        }
        if (previewUsecase != null) {
            cameraProvider.unbind(previewUsecase);
        }
        setupPreviewUsecase();

        setupImageAnalysisUsecase();

        cameraProvider.bindToLifecycle(this, cameraSelector, previewUsecase, imageAnalysisUsecase);
    }

    private void setupImageAnalysisUsecase() {
        if (imageAnalysisUsecase != null) {
            cameraProvider.unbind(imageAnalysisUsecase);
        }
        imageAnalysisUsecase =
                new ImageAnalysis.Builder()
                        .setTargetResolution(Objects.requireNonNull(getCameraXTargetResolution(this)))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
        imageAnalysisUsecase.setAnalyzer(executor, new TextAnalyzer());
    }

    private void setupPreviewUsecase() {
        Preview.Builder builder = new Preview.Builder();
        Size targetResolution = getCameraXTargetResolution(this);
        if (targetResolution != null) {
            builder.setTargetResolution(targetResolution);
        }
        previewUsecase = builder.build();
        previewUsecase.setSurfaceProvider(cameraPreviewView.getSurfaceProvider());
    }

    private Size getCameraXTargetResolution(CameraPreviewActivity context) {
        String prefKey = "crctas";
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            return android.util.Size.parseSize(sharedPreferences.getString(prefKey, null));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void goBack(View view) {
        this.finish();
    }

    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private class TextAnalyzer implements ImageAnalysis.Analyzer {

        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            @SuppressLint("UnsafeOptInUsageError") InputImage image =
                    InputImage.fromMediaImage(Objects.requireNonNull(imageProxy.getImage()),
                            imageProxy.getImageInfo().getRotationDegrees());

            recognizer.process(image)
                    .addOnSuccessListener(new OnSuccessListener<Text>() {
                        @Override
                        public void onSuccess(Text visionText) {
                            Log.d(TAG, "onSuccess: Text detected");
                            processTextBlock(visionText);
                        }
                    })
                    .addOnFailureListener(
                            new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    // Task failed with an exception
                                    Log.d(TAG, "onFailure: Failed to detect text");
                                }
                            })
                    .addOnCompleteListener(new OnCompleteListener<Text>() {
                        @Override
                        public void onComplete(@NonNull Task<Text> task) {
                            imageProxy.close();
                        }
                    });
        }

        private void processTextBlock(Text visionText) {
            String resultText = visionText.getText().toLowerCase();
            if (resultText.contains(message.toLowerCase())) {
                Log.d(TAG, "processTextBlock: " + System.currentTimeMillis());
                long currentTime = System.currentTimeMillis();
                // 3 second cooldown time
                if (currentTime - lastNotificationRingtime > (1000*3)) {
                    try {
                        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                        Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                        r.play();
                        lastNotificationRingtime = currentTime;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else {
                //showToast(resultText);
            }

            for (Text.TextBlock block : visionText.getTextBlocks()) {
                String blockText = block.getText();
                Point[] blockCornerPoints = block.getCornerPoints();
                Rect blockFrame = block.getBoundingBox();
                for (Text.Line line : block.getLines()) {
                    String lineText = line.getText();
                    Point[] lineCornerPoints = line.getCornerPoints();
                    Rect lineFrame = line.getBoundingBox();
                    for (Text.Element element : line.getElements()) {
                        String elementText = element.getText();
                        Point[] elementCornerPoints = element.getCornerPoints();
                        Rect elementFrame = element.getBoundingBox();
                    }
                }
            }
        }
    }
}