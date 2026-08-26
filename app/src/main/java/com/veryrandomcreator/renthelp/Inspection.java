package com.veryrandomcreator.renthelp;

public class Inspection {
    private String id;
    private String label;
    private String description;
    private boolean isReadOnly;

    public Inspection(String id, String label, String description, boolean isReadOnly) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.isReadOnly = isReadOnly;
    }

    public Inspection(String id, String label, String description) {
        this(id, label, description, false);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isReadOnly() {
        return isReadOnly;
    }

    public void setReadOnly(boolean readOnly) {
        isReadOnly = readOnly;
    }
}
