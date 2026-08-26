package com.veryrandomcreator.renthelp;

import android.content.Context;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// TODO:
//  - UPDATE RECYCLERVIEW AFTER NEW ITEM HAS BEEN ADDED (ONCE FRAGMENT HAS BEEN POPPED BACK INTO STACK)
public class InspectionFragment extends Fragment {

    private final ActivityResultLauncher<Uri> openDirectoryLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri != null) {
                    // The user selected a directory
                    writeOutputToUri(uri);
                } else {
                    // The user cancelled the directory picker
                    hideProgress();
                }
            });

//    private byte[] signature;
//    private byte[] pdf;
//    private byte[] certificateChain;

    private InspectionPackage inspectionPackage;
    private String inspectionId;
    private Inspection inspection;
    private EditText labelEditText;
    private EditText descriptionEditText;
    private RecyclerView recyclerView;
    private FloatingActionButton fabAddPhoto;
    
    // UI elements for progress
    private LinearLayout progressOverlay;
    private TextView progressStatusText;
    private Handler mainHandler;

    public InspectionFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_inspection_photos_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        inspectionPackage = new InspectionPackage();
        mainHandler = new Handler(Looper.getMainLooper());
        
        progressOverlay = view.findViewById(R.id.layout_progress_overlay);
        progressStatusText = view.findViewById(R.id.text_view_progress_status);

        if (getArguments() != null) {
            inspectionId = getArguments().getString("inspectionId");
        }

        labelEditText = view.findViewById(R.id.edit_text_inspection_label);
        descriptionEditText = view.findViewById(R.id.edit_text_inspection_description);

        if (inspectionId != null) {
            try {
                inspection = InspectionStorageManager.loadInspection(requireContext(), inspectionId);
                if (inspection != null) {
                    labelEditText.setText(inspection.getLabel());
                    descriptionEditText.setText(inspection.getDescription());
                    
                    if (inspection.isReadOnly()) {
                        labelEditText.setEnabled(false);
                        descriptionEditText.setEnabled(false);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        recyclerView = view.findViewById(R.id.recycler_view_photos);
        fabAddPhoto = view.findViewById(R.id.fab_add_photo);
        FloatingActionButton fabInspectionOptions = view.findViewById(R.id.fab_inspection_options);
        
        if (inspection != null && inspection.isReadOnly()) {
            fabAddPhoto.setVisibility(View.GONE);
        }

        // Set up the RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        refreshPhotosList();

        // Listen for the result from CameraFragment
        getChildFragmentManager().setFragmentResultListener("camera_request_key", this, (requestKey, bundle) -> {
            String tempPhotoUri = bundle.getString("tempPhotoUri");
            if (tempPhotoUri != null) {
                PhotoFragment fragment = new PhotoFragment();
                Bundle args = new Bundle();
                args.putString("inspectionId", inspectionId);
                args.putString("tempPhotoUri", tempPhotoUri);
                fragment.setArguments(args);
                fragment.setOnDismissCallback(this::refreshPhotosList);
                fragment.show(getChildFragmentManager(), "photo_fragment");
            }
        });

        // Set up the FAB click listener to navigate to the CameraFragment for a new item
        fabAddPhoto.setOnClickListener(v -> {
            CameraFragment cameraFragment = new CameraFragment();
            cameraFragment.show(getChildFragmentManager(), "camera_fragment");
        });

        // Set up the Inspection options FAB click listener
        fabInspectionOptions.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(requireContext(), fabInspectionOptions);
            popup.getMenuInflater().inflate(R.menu.menu_inspection_options, popup.getMenu());

            if (inspection != null && inspection.isReadOnly()) {
                popup.getMenu().findItem(R.id.action_end_inspection).setVisible(false);
            } else {
                popup.getMenu().findItem(R.id.action_download_bundle).setVisible(false);
            }

            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_download_bundle) {
                    openDirectoryLauncher.launch(null);
                    return true;
                } else if (item.getItemId() == R.id.action_end_inspection) {
                    finalizeInspection();
                    return true;
                } else if (item.getItemId() == R.id.action_delete_inspection) {
                    inspection = null; // Prevent onPause from re-saving the inspection

                    FileStorageManager.deleteBundle(requireContext(), inspectionId);

                    InspectionStorageManager.deleteInspectionData(requireContext(), inspectionId, new InspectionStorageManager.SaveCallback() {
                        @Override
                        public void onSuccess(String id) {
                            requireActivity().getSupportFragmentManager().popBackStack();
                        }
                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(requireContext(), "Failed to delete", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return true;
                }
                return false;
            });

            popup.show();
        });
    }

    private void updateProgress(String status) {
        if (mainHandler != null && progressStatusText != null) {
            mainHandler.post(() -> progressStatusText.setText(status));
        }
    }

    private void hideProgress() {
        if (mainHandler != null && progressOverlay != null) {
            mainHandler.post(() -> progressOverlay.setVisibility(View.GONE));
        }
    }

    private void showProgress(String initialStatus) {
        if (progressOverlay != null && progressStatusText != null) {
            progressStatusText.setText(initialStatus);
            progressOverlay.setVisibility(View.VISIBLE);
        }
    }

    // only going to be called after signature, certificate chain, and pdf have been
    // set
    private void writeOutputToUri(Uri treeUri) {
        DocumentFile directory = DocumentFile.fromTreeUri(getContext(), treeUri);

        if (directory == null || !directory.canWrite()) {
            return;
        }

        try {
            byte[] pdfBytes = FileStorageManager.loadFile(getContext(), inspectionId, FileStorageManager.FILE_PDF);
            byte[] sigBytes = FileStorageManager.loadFile(getContext(), inspectionId, FileStorageManager.FILE_SIG);
            byte[] certBytes = FileStorageManager.loadFile(getContext(), inspectionId, FileStorageManager.FILE_CERT);
            byte[] otsBytes = FileStorageManager.loadFile(getContext(), inspectionId, FileStorageManager.FILE_OTS);
            if (pdfBytes != null) saveBytesToFile(getContext(), directory, "document.pdf", "application/pdf", pdfBytes);
            if (sigBytes != null) saveBytesToFile(getContext(), directory, "signature.sig", "application/octet-stream", sigBytes);
            if (certBytes != null) saveBytesToFile(getContext(), directory, "certificates.pem", "application/x-x509-ca-cert", certBytes);

            if (otsBytes != null) saveBytesToFile(getContext(), directory, "document.ots", "application/octet-stream", otsBytes);

            Toast.makeText(getContext(), "Bundle successfully exported!", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Failed to save files: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            hideProgress();
        }
    }

    private void saveBytesToFile(Context context, DocumentFile directory, String fileName, String mimeType, byte[] data)
            throws IOException {
        // Create the file in the selected directory
        DocumentFile file = directory.createFile(mimeType, fileName);

        if (file == null) {
            throw new IOException("Failed to create file: " + fileName);
        }

        // Open an OutputStream via the ContentResolver using the file's Uri
        try (OutputStream os = context.getContentResolver().openOutputStream(file.getUri())) {
            if (os != null) {
                os.write(data);
                os.flush();
            } else {
                throw new IOException("OutputStream was null for: " + fileName);
            }
        }
    }

    private void finalizeInspection() {
        // implement time limit logic here

        showProgress("Finalizing proof files");

        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            try {
                updateProgress("Generating PDF document...");
                PdfDocument pdfDocument = inspectionPackage.generatePdf(getContext());

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                pdfDocument.writeTo(outputStream);
                byte[] pdf = outputStream.toByteArray();
                pdfDocument.close(); // check

                updateProgress("Signing PDF document...");
                byte[] signature = HardwareKeyManager.signPdfDocument(inspectionId, pdf);

                updateProgress("Retrieving certificate chain...");
                byte[] certificateChain = HardwareKeyManager.getCertificateChainBytes(inspectionId);

                FileStorageManager.saveFile(requireContext(), inspectionId, FileStorageManager.FILE_PDF, pdf);
                FileStorageManager.saveFile(requireContext(), inspectionId, FileStorageManager.FILE_SIG, signature);
                FileStorageManager.saveFile(requireContext(), inspectionId, FileStorageManager.FILE_CERT, certificateChain);

                Data inputData = new Data.Builder().putString("inspectionId", inspectionId).build();
                Constraints constraints = new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build();

                OneTimeWorkRequest otsWorkRequest = new OneTimeWorkRequest.Builder(OTSWorker.class)
                        .setConstraints(constraints)
                        .setInputData(inputData)
                        .build();

                WorkManager.getInstance(requireContext()).enqueue(otsWorkRequest);

                // Launch directory picker. Progress will be hidden when selection finishes (in writeOutputToUri)
                // or if the user cancels (you might want to handle cancellation in the launcher to hide it).
//                mainHandler.post(() -> openDirectoryLauncher.launch(null));

                mainHandler.post(() -> {
                    hideProgress();
                    if (inspection != null) {
                        inspection.setReadOnly(true);
                        InspectionStorageManager.saveInspectionData(requireContext(), inspection, null);
                        labelEditText.setEnabled(false);
                        descriptionEditText.setEnabled(false);
                        fabAddPhoto.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), "Inspection Ended & Bundle Secured", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (GeneralSecurityException | IOException e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    hideProgress();
                    Toast.makeText(getContext(), "Error during PDF generation: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    hideProgress();
                    Toast.makeText(getContext(), "Unexpected error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void refreshPhotosList() {
        // Load actual data from secure storage
        try {
            inspectionPackage.setLabel(labelEditText.getText().toString());
            inspectionPackage.setDescription(descriptionEditText.getText().toString());
            inspectionPackage.setImages(PhotoStorageManager.loadInspectionImageData(requireContext(), inspectionId));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Failed to load photos.", Toast.LENGTH_SHORT).show();
        }

        InspectionPhotosAdapter adapter = new InspectionPhotosAdapter(inspectionPackage.getImages(), item -> {
            if (inspection != null && inspection.isReadOnly()) {
                Toast.makeText(requireContext(), "Inspection ended. Photos are read-only.", Toast.LENGTH_SHORT).show();
                return;
            }
            PhotoFragment fragment = new PhotoFragment();
            Bundle bundle = new Bundle();
            bundle.putString("itemId", item.getId());
            bundle.putString("inspectionId", inspectionId);
            fragment.setArguments(bundle);
            fragment.setOnDismissCallback(this::refreshPhotosList);
            fragment.show(getChildFragmentManager(), "photo_fragment");
        });
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (inspectionId != null && inspection != null && !inspection.isReadOnly()) {
            inspection.setLabel(labelEditText.getText().toString());
            inspection.setDescription(descriptionEditText.getText().toString());
            InspectionStorageManager.saveInspectionData(requireContext(), inspection, null);
        }
    }
}
