package com.veryrandomcreator.renthelp;

public class InspectionImage {
    private String id;
    private String label;
    private String notes;

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getNotes() {
        return notes;
    }

    public InspectionImage(String id, String label, String notes) {
        this.id = id;
        this.label = label;
        this.notes = notes;
    }
}
