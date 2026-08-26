package com.veryrandomcreator.renthelp;

import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.DialogFragment;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.Manifest;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraFragment extends DialogFragment {

    private PreviewView viewFinder;
    private ImageView imagePreview;
    private LinearLayout layoutCaptureControls;
    private ConstraintLayout layoutConfirmControls;

    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private File savedTempFile;

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    Toast.makeText(requireContext(), "Camera permission is required.", Toast.LENGTH_SHORT).show();
                    dismiss();
                }
            });

    public CameraFragment() {
        // Required empty public constructor
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewFinder = view.findViewById(R.id.view_finder);
        imagePreview = view.findViewById(R.id.image_preview);
        layoutCaptureControls = view.findViewById(R.id.layout_capture_controls);
        layoutConfirmControls = view.findViewById(R.id.layout_confirm_controls);

        FloatingActionButton fabCapture = view.findViewById(R.id.fab_capture);
        Button btnRetake = view.findViewById(R.id.btn_retake);
        Button btnUsePhoto = view.findViewById(R.id.btn_use_photo);
        ImageButton btnClose = view.findViewById(R.id.btn_close);
        cameraExecutor = Executors.newSingleThreadExecutor();
        fabCapture.setOnClickListener(v -> takePhoto());
        btnRetake.setOnClickListener(v -> resetToCamera());
        btnClose.setOnClickListener(v -> dismiss());
        btnUsePhoto.setOnClickListener(v -> {
            if (savedTempFile != null) {
                Bundle result = new Bundle();
                result.putString("tempPhotoUri", savedTempFile.getAbsolutePath());
                getParentFragmentManager().setFragmentResult("camera_request_key", result);
                dismiss();
            }
        });
        // Check permission and start camera
        requestPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider
                .getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                ResolutionSelector resolutionSelector = new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build();

                Preview preview = new Preview.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .build();

                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            } catch (Exception exc) {
                exc.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void takePhoto() {
        if (imageCapture == null)
            return;
        try {
            // Write to a strictly internal cache file
            savedTempFile = File.createTempFile("secure_capture_", ".jpg", requireContext().getCacheDir());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(savedTempFile)
                .build();
        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        requireActivity().runOnUiThread(() -> {
                            viewFinder.setVisibility(View.GONE);
                            layoutCaptureControls.setVisibility(View.GONE);

                            imagePreview.setVisibility(View.VISIBLE);
                            layoutConfirmControls.setVisibility(View.VISIBLE);

                            imagePreview.setImageURI(android.net.Uri.fromFile(savedTempFile));
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(requireContext(), "Capture failed: " + exception.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void resetToCamera() {
        imagePreview.setVisibility(View.GONE);
        layoutConfirmControls.setVisibility(View.GONE);

        viewFinder.setVisibility(View.VISIBLE);
        layoutCaptureControls.setVisibility(View.VISIBLE);

        if (savedTempFile != null && savedTempFile.exists()) {
            savedTempFile.delete();
            savedTempFile = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cameraExecutor.shutdown();
    }
}