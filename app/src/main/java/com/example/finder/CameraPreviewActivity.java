package com.example.finder;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CameraPreviewActivity extends AppCompatActivity {

    private static final String[] REQUIRED_PERMISSIONS = new String[] {"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private String message;
    private int count = 0;
    private TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private ImageCapture imageCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Objects.requireNonNull(getSupportActionBar()).hide();
        setContentView(R.layout.activity_camera_preview);
        getLifecycle().addObserver(recognizer);
        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();
        message = intent.getStringExtra(MainActivity.EXTRA_MESSAGE);
        Log.d("yoyo", "onCreate: " + message);
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
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    bindPreview(cameraProvider);
                } catch (ExecutionException | InterruptedException e) {
                    // No errors need to be handled for this Future.
                    // This should never be reached.
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        PreviewView cameraPreviewView = findViewById(R.id.cameraPreviewView);
        preview.setSurfaceProvider(cameraPreviewView.getSurfaceProvider());
        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
        imageAnalysis.setAnalyzer(executor, new TextAnalyzer());
        ImageCapture.Builder builder = new ImageCapture.Builder();
        imageCapture = builder
                .setTargetRotation(this.getWindowManager().getDefaultDisplay().getRotation())
                .build();
        Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview, imageCapture);
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
            try (@SuppressLint("UnsafeOptInUsageError") Image mediaImage = imageProxy.getImage()) {
                if (mediaImage != null) {
                    if (count++ < 30) {
                        Log.d("yoyo", "recognizeText: " + imageProxy.getImageInfo().toString());
                    }
                    InputImage image =
                            InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                    if (count % 10 == 0) {
                        //saveImage(imageProxy);
                    }
                    Task<Text> result =
                            recognizer.process(image)
                                    .addOnSuccessListener(new OnSuccessListener<Text>() {
                                        @Override
                                        public void onSuccess(Text visionText) {
                                            //processTextBlock(visionText);
                                        }
                                    })
                                    .addOnFailureListener(
                                            new OnFailureListener() {
                                                @Override
                                                public void onFailure(@NonNull Exception e) {
                                                    // Task failed with an exception
                                                    if (count % 5 == 0 ||
                                                        count == 1 || count == 0 || count == 2) {
                                                        Log.d("yoyo", "onFailure: Stack " + count, e);
                                                        //Log.d("yoyo", "onFailure: Stack");

                                                    }
                                                    if (count % 10 == 0) {
                                                        //saveImage(imageProxy);
                                                    }
                                                }
                                            })
                                    .addOnCompleteListener(new OnCompleteListener<Text>() {
                                        @Override
                                        public void onComplete(@NonNull Task<Text> task) {
                                            //imageProxy.close();
                                            mediaImage.close();
                                            if (count < 30) {
                                                Log.d("yoyo", "onComplete: close()" + count);
                                            }
                                        }
                                    });
                }
                if (count > 1000) {
                    count = 0;
                }
            }
        }

        private void processTextBlock(Text visionText) {
            String resultText = visionText.getText().toLowerCase();
            if (count < 30) {
                Log.d("yoyo", "processTextBlock: " + resultText);
            }
            if (resultText.contains(message.toLowerCase())) {
                try {
                    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                    Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                    r.play();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                showToast(resultText);
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

    private void saveImage(ImageProxy imageProxy) {
        if (imageCapture == null) {
            Log.d("yoyo", "saveImage: imageCapture null");
        }
        Log.d("yoyo", "saveImage: Saving image" + count);
        SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        File file = new File(getBatchDirectoryName(), mDateFormat.format(new Date())+ ".jpg");

        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(file).build();
        imageCapture.takePicture(outputFileOptions, executor, new ImageCapture.OnImageSavedCallback () {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(CameraPreviewActivity.this, "Image Saved successfully", Toast.LENGTH_SHORT).show();
                        Log.d("yoyo", "run: Image saved");
                    }
                });
            }

            @Override
            public void onError(@NonNull ImageCaptureException error) {
                Log.d("yoyo", "onError: Image save", error);
            }
        });
    }

    public String getBatchDirectoryName() {
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.d("yoyo", "getBatchDirectoryName: dir fail");
        }

        return dir.getAbsolutePath();
    }
}