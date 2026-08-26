package com.veryrandomcreator.renthelp;

import static java.util.UUID.randomUUID;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;

import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

// fetch the latest blockchain hash every time an image is taken (in case it takes time to take all the photos)
public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recycler_view_inspections);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        refreshInspectionsList();

        // Refresh the list any time a fragment pops back to this screen
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                refreshInspectionsList();
            }
        });

        FloatingActionButton fabAddInspection = findViewById(
                R.id.fab_add_inspection);

        fabAddInspection.setOnClickListener(v -> {
            v.setEnabled(false);

            String newId = randomUUID().toString();

            // PERFORM A CHECK TO ENSURE THE KEY ISN'T ALREADY IN USE

            HardwareKeyManager.startSession(newId, new HardwareKeyManager.SessionCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;

                        Inspection newInspection = new Inspection(newId, "New Inspection", "");
                        InspectionStorageManager.saveInspectionData(getApplicationContext(), newInspection, new InspectionStorageManager.SaveCallback() {
                            @Override
                            public void onSuccess(String id) {
                                InspectionFragment fragment = new InspectionFragment();
                                Bundle args = new Bundle();
                                args.putString("inspectionId", id);
                                fragment.setArguments(args);

                                getSupportFragmentManager().beginTransaction().setReorderingAllowed(true)
                                        .add(R.id.fragmentContainerView, fragment)
                                        .addToBackStack(null)
                                        .commit();
                            }

                            @Override
                            public void onError(Exception e) {
                                e.printStackTrace();
                                Toast.makeText(MainActivity.this, "Storage Error: Failed to save inspection to device.", Toast.LENGTH_SHORT).show();
                            }
                        });
                        v.setEnabled(true);
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        v.setEnabled(true);
                        Toast.makeText(MainActivity.this,
                                "Network Error: Could not fetch blockchain timestamp. Please check your internet connection.",
                                Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    private void refreshInspectionsList() {
        try {
            List<Inspection> inspections = InspectionStorageManager.loadInspections(this);
            InspectionsAdapter adapter = new InspectionsAdapter(inspections, item -> {
                InspectionFragment fragment = new InspectionFragment();
                Bundle args = new Bundle();
                args.putString("inspectionId", item.getId());
                fragment.setArguments(args);

                getSupportFragmentManager().beginTransaction().setReorderingAllowed(true)
                        .add(R.id.fragmentContainerView, fragment)
                        .addToBackStack(null)
                        .commit();
            });
            recyclerView.setAdapter(adapter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}