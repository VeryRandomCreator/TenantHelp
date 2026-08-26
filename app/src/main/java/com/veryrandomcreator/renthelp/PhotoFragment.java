package com.veryrandomcreator.renthelp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import android.net.Uri;

import java.util.List;

import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.widget.Toast;

/*
Todo:
 - Ensure image label is not left empty
 */
public class PhotoFragment extends BottomSheetDialogFragment {

    // Callback invoked after the sheet is dismissed so the parent can refresh its
    // list
    public interface OnDismissCallback {
        void onPhotoDismissed();
    }

    private OnDismissCallback dismissCallback;

    public void setOnDismissCallback(OnDismissCallback callback) {
        this.dismissCallback = callback;
    }

    private ImageView imageViewPhoto;
    private EditText imageNotesEdt;
    private EditText imageLabelEdt;
    private Button buttonTakePhoto;
    private ImageButton buttonMoreOptions;

    // Store the bitmap when we take a picture or load an existing one
    private Bitmap currentBitmap;
    private Uri tempImageUri;
    // True only when the user has taken a NEW photo in this session
    private boolean bitmapChanged = false;


    public PhotoFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_photo, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Prevent drag-to-dismiss: expand fully and make it not hideable
        View bottomSheetView = getDialog() != null && getDialog().getWindow() != null
                ? requireDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet)
                : null;
        if (bottomSheetView != null) {
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheetView);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setHideable(false);
            behavior.setDraggable(false);
        }

        imageViewPhoto = view.findViewById(R.id.image_view_photo);
        imageNotesEdt = view.findViewById(R.id.image_notes_edt);
        imageLabelEdt = view.findViewById(R.id.image_label_edt);
        buttonTakePhoto = view.findViewById(R.id.button_take_photo);
        buttonMoreOptions = view.findViewById(R.id.button_more_options);

        // Hide the "Take Photo" button since it's now handled by CameraFragment
        buttonTakePhoto.setVisibility(View.GONE);
        String inspectionId = null;
        if (getArguments() != null) {
            inspectionId = getArguments().getString("inspectionId");
        }
        final String fInspectionId = inspectionId;

        // If we are editing an existing item, load its data
        final boolean isEditing = getArguments() != null && getArguments().containsKey("itemId");
        final String itemId = isEditing ? getArguments().getString("itemId") : null;

        if (isEditing) {
            try {
                // Find the matching text data from storage
                List<InspectionImage> items = PhotoStorageManager.loadInspectionImageData(requireContext(), fInspectionId);
                for (InspectionImage item : items) {
                    if (item.getId().equals(itemId)) {
                        imageLabelEdt.setText(item.getLabel());
                        imageNotesEdt.setText(item.getNotes());
                        break;
                    }
                }

                // Load the image bitmap
                currentBitmap = PhotoStorageManager.loadPhotoBitmap(requireContext(), itemId, false);
                if (currentBitmap != null) {
                    imageViewPhoto.setImageBitmap(currentBitmap);
                } else {
                    System.out.println("THIS IS A PROBLEM " + itemId + ".png");
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(requireContext(), "Failed to load existing photo data.", Toast.LENGTH_SHORT).show();
            }
        } else if (getArguments() != null && getArguments().containsKey("tempPhotoUri")) {
            try {
                String uriString = getArguments().getString("tempPhotoUri");
                Bitmap rawBitmap = BitmapFactory.decodeFile(uriString);
                currentBitmap = rotateBitmapIfRequired(rawBitmap, uriString);
                imageViewPhoto.setImageBitmap(currentBitmap);
                bitmapChanged = true;
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(requireContext(), "Failed to load secure capture.", Toast.LENGTH_SHORT).show();
            }
        }

        // Set up the more_vert overflow menu
        buttonMoreOptions.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(requireContext(), buttonMoreOptions);
            popup.inflate(R.menu.menu_photo_options);

            // Hide delete if this is a new item
            Menu menu = popup.getMenu();
            MenuItem deleteItem = menu.findItem(R.id.action_delete);
            if (deleteItem != null) {
                deleteItem.setVisible(isEditing);
            }

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_save) {
                    savePhotoData(fInspectionId);
                    return true;
                } else if (id == R.id.action_delete && isEditing) {
                    deletePhotoData(fInspectionId, itemId);
                    return true;
                }
                return false;
            });

            popup.show();
        });
    }

    private void savePhotoData(String inspectionId) {
        String label = imageLabelEdt.getText().toString().trim();
        String notes = imageNotesEdt.getText().toString().trim();

        if (label.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter an image label.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentBitmap == null) {
            Toast.makeText(requireContext(), "Please take a photo first.", Toast.LENGTH_SHORT).show();
            return;
        }

        buttonMoreOptions.setEnabled(false);

        // If editing an existing item AND the image wasn't changed, only update metadata (fast)
        String editingId = (getArguments() != null) ? getArguments().getString("itemId") : null;
        boolean needsImageWrite = (editingId == null) || bitmapChanged;

        if (needsImageWrite) {
            Toast.makeText(requireContext(), "Saving image, this may take a moment...", Toast.LENGTH_SHORT).show();
        }

        PhotoStorageManager.SaveCallback saveCallback = new PhotoStorageManager.SaveCallback() {
            @Override
            public void onSuccess(String id) {
                if (!isAdded())
                    return;
                Toast.makeText(requireContext(), "Saved securely!", Toast.LENGTH_SHORT).show();
                dismissWithCallback();
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded())
                    return;
                e.printStackTrace();
                Toast.makeText(requireContext(), "Error saving data.", Toast.LENGTH_SHORT).show();
                buttonMoreOptions.setEnabled(true);
            }
        };

        if (editingId != null && !bitmapChanged) {
            // Text-only edit: skip the file write entirely
            PhotoStorageManager.updatePhotoMetadataOnly(requireContext(), inspectionId, editingId, label, notes, saveCallback);
        } else if (editingId != null) {
            // Existing item with a new photo taken
            PhotoStorageManager.updatePhotoData(requireContext(), inspectionId, editingId, label, notes, currentBitmap,
                    saveCallback);
        } else {
            // Brand new item
            PhotoStorageManager.savePhotoData(requireContext(), inspectionId, label, notes, currentBitmap, saveCallback);
        }
    }

    private void deletePhotoData(String inspectionId, String itemId) {
        buttonMoreOptions.setEnabled(false);

        PhotoStorageManager.deletePhotoData(requireContext(), inspectionId, itemId,
                new PhotoStorageManager.SaveCallback() {
                    @Override
                    public void onSuccess(String id) {
                        if (!isAdded())
                            return;
                        Toast.makeText(requireContext(), "Photo deleted.", Toast.LENGTH_SHORT).show();
                        dismissWithCallback();
                    }

                    @Override
                    public void onError(Exception e) {
                        if (!isAdded())
                            return;
                        e.printStackTrace();
                        Toast.makeText(requireContext(), "Error deleting data.", Toast.LENGTH_SHORT).show();
                        buttonMoreOptions.setEnabled(true);
                    }
                });
    }

    private void dismissWithCallback() {
        if (dismissCallback != null) {
            dismissCallback.onPhotoDismissed();
        }
        dismiss();
    }

    private Bitmap rotateBitmapIfRequired(Bitmap img, String selectedImage) throws java.io.IOException {
        android.media.ExifInterface ei = new android.media.ExifInterface(selectedImage);
        int orientation = ei.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case android.media.ExifInterface.ORIENTATION_ROTATE_90:
                return rotateImage(img, 90);
            case android.media.ExifInterface.ORIENTATION_ROTATE_180:
                return rotateImage(img, 180);
            case android.media.ExifInterface.ORIENTATION_ROTATE_270:
                return rotateImage(img, 270);
            default:
                return img;
        }
    }

    private Bitmap rotateImage(Bitmap img, int degree) {
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        if (img != rotatedImg) {
            img.recycle();
        }
        return rotatedImg;
    }
}
