package com.veryrandomcreator.renthelp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfDocument.*;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

public class InspectionPackage {
    public static final int PDF_PADDING = 10;
    public static final int PDF_PAGE_HEIGHT = 842;
    public static final int PDF_PAGE_WIDTH = 595;

    public static final Rect MAX_IMAGE_DIM = new Rect(0, 0, PDF_PAGE_WIDTH - PDF_PADDING * 2, PDF_PAGE_HEIGHT / 4 * 3 - PDF_PADDING * 2);

    private List<InspectionImage> images = new ArrayList<>();
    private String label;
    private String description;

    public void setImages(List<InspectionImage> images) {
        this.images = images;
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

    public List<InspectionImage> getImages() {
        return images;
    }

    // generates a pdf such that two there are two images per page
    public PdfDocument generatePdf(Context context) throws GeneralSecurityException, IOException {
        PdfDocument document = new PdfDocument();
        PageInfo pageInfo = null;
        Page page = null;
        Canvas canvas = null;

        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.BLACK);
        titlePaint.setTextSize(18f);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        TextPaint notesPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        notesPaint.setColor(Color.DKGRAY);
        notesPaint.setTextSize(14f);
        notesPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

        TextPaint hashPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        hashPaint.setColor(Color.GRAY);
        hashPaint.setTextSize(10f);
        hashPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));

        for (int i = 0; i < images.size(); i++) {
            InspectionImage inspectionImage = images.get(i);
            pageInfo = new PageInfo.Builder(PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, i).create();
            page = document.startPage(pageInfo);
            canvas = page.getCanvas();

            Bitmap image = PhotoStorageManager.loadPhotoBitmap(context, inspectionImage.getId(), true);
            Rect dest = new Rect(0, 0, image.getWidth(), image.getHeight());
            scaleToFit(MAX_IMAGE_DIM, dest, new Point(PDF_PADDING, PDF_PADDING));
            canvas.drawBitmap(image, null, dest, null);

            int textWidthSpace = PDF_PAGE_WIDTH - (PDF_PADDING * 2);

            canvas.save();
            canvas.translate((float) PDF_PADDING, dest.bottom + PDF_PADDING * 2);

            canvas.drawText(images.get(i).getLabel(), 0, 0, titlePaint);

            canvas.translate(0, PDF_PADDING); // Move down 25 pixels below title
            if (inspectionImage.getNotes() != null && !inspectionImage.getNotes().isEmpty()) {
                StaticLayout notesLayout = StaticLayout.Builder.obtain(inspectionImage.getNotes(), 0, inspectionImage.getNotes().length(),
                                notesPaint, textWidthSpace)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.1f)
                        .build();
                notesLayout.draw(canvas);
                canvas.translate(0, notesLayout.getHeight() + 15);
            }

            canvas.restore();

            document.finishPage(page);
        }
        return document;
    }

    // This method scales dest to fit in parent while maintaining aspect ratio of dest
    private void scaleToFit(final Rect parent, Rect dest, Point translation) {
        float scaleX = parent.right / (float) dest.right;
        float scaleY = parent.bottom / (float) dest.bottom;
        float scale = Math.min(scaleX, scaleY);
        dest.right = (int) (dest.right * scale);
        dest.bottom = (int) (dest.bottom * scale);

        dest.top += translation.y;
        dest.bottom += translation.y;

        dest.left += translation.x;
        dest.right += translation.x;
    }

    private void scaleToFit(final Rect parent, Rect dest) {
        scaleToFit(parent, dest, new Point(0, 0));
    }
}
