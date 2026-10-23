package com.javierreansyah.pinecone.data.publication.conversion;

import com.artifex.mupdf.fitz.Device;
import com.artifex.mupdf.fitz.Rect;
import com.artifex.mupdf.fitz.StrokeState;

import java.util.ArrayList;
import java.util.List;

/**
 * Collect path bounds without using MuPDF's unsafe structured-text vector JNI callback.
 * Callback signatures, including unused arguments, are defined by the MuPDF Device API.
 */
final class PdfGraphicsDevice extends Device {
    final List<Rect> bounds = new ArrayList<>();
    // The Java JNI wrapper rejects a null stroke even for filled paths. A zero-width
    // stroke provides the unexpanded bounds while satisfying that native contract.
    private final StrokeState fillBoundsStroke = new StrokeState(
            StrokeState.LINE_CAP_BUTT, StrokeState.LINE_JOIN_MITER, 0f, 10f);

    @Override
    public void destroy() {
        fillBoundsStroke.destroy();
        super.destroy();
    }

    @Override
    public void close() {
    }

    @Override
    public void fillPath(com.artifex.mupdf.fitz.Path path, boolean evenOdd, com.artifex.mupdf.fitz.Matrix ctm, com.artifex.mupdf.fitz.ColorSpace cs, float[] color, float alpha, int cp) {
        if (alpha > 0) bounds.add(path.getBounds(fillBoundsStroke, ctm));
    }

    @Override
    public void strokePath(com.artifex.mupdf.fitz.Path path, com.artifex.mupdf.fitz.StrokeState stroke, com.artifex.mupdf.fitz.Matrix ctm, com.artifex.mupdf.fitz.ColorSpace cs, float[] color, float alpha, int cp) {
        if (alpha > 0) bounds.add(path.getBounds(stroke, ctm));
    }

    @Override
    public void clipPath(com.artifex.mupdf.fitz.Path arg0, boolean arg1, com.artifex.mupdf.fitz.Matrix arg2) {
    }

    @Override
    public void clipStrokePath(com.artifex.mupdf.fitz.Path arg0, com.artifex.mupdf.fitz.StrokeState arg1, com.artifex.mupdf.fitz.Matrix arg2) {
    }

    @Override
    public void fillText(com.artifex.mupdf.fitz.Text arg0, com.artifex.mupdf.fitz.Matrix arg1, com.artifex.mupdf.fitz.ColorSpace arg2, float[] arg3, float arg4, int arg5) {
    }

    @Override
    public void strokeText(com.artifex.mupdf.fitz.Text arg0, com.artifex.mupdf.fitz.StrokeState arg1, com.artifex.mupdf.fitz.Matrix arg2, com.artifex.mupdf.fitz.ColorSpace arg3, float[] arg4, float arg5, int arg6) {
    }

    @Override
    public void clipText(com.artifex.mupdf.fitz.Text arg0, com.artifex.mupdf.fitz.Matrix arg1) {
    }

    @Override
    public void clipStrokeText(com.artifex.mupdf.fitz.Text arg0, com.artifex.mupdf.fitz.StrokeState arg1, com.artifex.mupdf.fitz.Matrix arg2) {
    }

    @Override
    public void ignoreText(com.artifex.mupdf.fitz.Text arg0, com.artifex.mupdf.fitz.Matrix arg1) {
    }

    @Override
    public void fillShade(com.artifex.mupdf.fitz.Shade shade, com.artifex.mupdf.fitz.Matrix ctm, float alpha, int cp) {
        if (alpha > 0) bounds.add(shade.getBounds(ctm));
    }

    @Override
    public void fillImage(com.artifex.mupdf.fitz.Image arg0, com.artifex.mupdf.fitz.Matrix arg1, float arg2, int arg3) {
    }

    @Override
    public void fillImageMask(com.artifex.mupdf.fitz.Image arg0, com.artifex.mupdf.fitz.Matrix arg1, com.artifex.mupdf.fitz.ColorSpace arg2, float[] arg3, float arg4, int arg5) {
    }

    @Override
    public void clipImageMask(com.artifex.mupdf.fitz.Image arg0, com.artifex.mupdf.fitz.Matrix arg1) {
    }

    @Override
    public void popClip() {
    }

    @Override
    public void beginMask(com.artifex.mupdf.fitz.Rect arg0, boolean arg1, com.artifex.mupdf.fitz.ColorSpace arg2, float[] arg3, int arg4) {
    }

    @Override
    public void endMask() {
    }

    @Override
    public void beginGroup(com.artifex.mupdf.fitz.Rect arg0, com.artifex.mupdf.fitz.ColorSpace arg1, boolean arg2, boolean arg3, int arg4, float arg5) {
    }

    @Override
    public void endGroup() {
    }

    @Override
    public int beginTile(com.artifex.mupdf.fitz.Rect arg0, com.artifex.mupdf.fitz.Rect arg1, float arg2, float arg3, com.artifex.mupdf.fitz.Matrix arg4, int arg5, int arg6) {
        return 0;
    }

    @Override
    public void endTile() {
    }

    @Override
    public void renderFlags(int arg0, int arg1) {
    }

    @Override
    public void setDefaultColorSpaces(com.artifex.mupdf.fitz.DefaultColorSpaces arg0) {
    }

    @Override
    public void beginLayer(java.lang.String arg0) {
    }

    @Override
    public void endLayer() {
    }

    @Override
    public void beginStructure(int arg0, java.lang.String arg1, int arg2) {
    }

    @Override
    public void endStructure() {
    }

    @Override
    public void beginMetatext(int arg0, java.lang.String arg1) {
    }

    @Override
    public void endMetatext() {
    }
}
