package io.zxingye.library.surfacebridge.transform;

import android.graphics.Matrix;
import android.graphics.RectF;

public class ScaleToFitTransform implements Transform {

    public enum Type {
        FILL,
        INSIDE_START,
        INSIDE_CENTER,
        INSIDE_END,
        CROP_START,
        CROP_CENTER,
        CROP_END,
    }

    public enum Rotation {
        ROTATION_0,
        ROTATION_90,
        ROTATION_180,
        ROTATION_270;

        public static Rotation find(int rotation) {
            rotation = rotation % 360;
            if (rotation < 0) {
                rotation = rotation + 360;
            }
            switch (rotation) {
                case 90:
                    return ROTATION_90;
                case 180:
                    return ROTATION_180;
                case 270:
                    return ROTATION_270;
                default:
                    return ROTATION_0;
            }
        }
    }

    private final RectF src = new RectF();
    private final RectF dst = new RectF();
    private final Matrix rotationMatrix = new Matrix();

    private final Type scaleType;
    private final Rotation rotation;


    public ScaleToFitTransform(Type scaleType) {
        this(scaleType, Rotation.ROTATION_0);
    }

    public ScaleToFitTransform(Type scaleType, Rotation rotation) {
        this.scaleType = scaleType;
        this.rotation = rotation;
    }

    @Override
    public void transform(Matrix matrix,
                          int texWidth,
                          int texHeight,
                          int viewportWidth,
                          int viewportHeight) {
        setupRotationMatrix(rotationMatrix, texWidth, texHeight);
        switch (rotation) {
            case ROTATION_90:
            case ROTATION_270:
                setupScaleMatrix(matrix, texHeight, texWidth, viewportWidth, viewportHeight);
                break;
            default:
                setupScaleMatrix(matrix, texWidth, texHeight, viewportWidth, viewportHeight);
                break;
        }
        matrix.preConcat(rotationMatrix);
    }

    private void setupRotationMatrix(Matrix matrix,
                                     int texWidth,
                                     int texHeight) {
        switch (rotation) {
            case ROTATION_90:
                matrix.setTranslate(-texWidth / 2f, -texHeight / 2f);
                matrix.postRotate(90);
                matrix.postTranslate(texHeight / 2f, texWidth / 2f);
                break;
            case ROTATION_180:
                matrix.setTranslate(-texWidth / 2f, -texHeight / 2f);
                matrix.postRotate(180);
                matrix.postTranslate(texWidth / 2f, texHeight / 2f);
                break;
            case ROTATION_270:
                matrix.setTranslate(-texWidth / 2f, -texHeight / 2f);
                matrix.postRotate(270);
                matrix.postTranslate(texHeight / 2f, texWidth / 2f);
                break;
        }
    }

    private void setupScaleMatrix(Matrix matrix,
                                  int texWidth,
                                  int texHeight,
                                  int viewportWidth,
                                  int viewportHeight) {
        src.right = texWidth;
        src.bottom = texHeight;
        dst.right = viewportWidth;
        dst.bottom = viewportHeight;
        switch (scaleType) {
            case FILL:
                matrix.setRectToRect(src, dst, Matrix.ScaleToFit.FILL);
                return;
            case INSIDE_START:
                matrix.setRectToRect(src, dst, Matrix.ScaleToFit.START);
                return;
            case INSIDE_CENTER:
                matrix.setRectToRect(src, dst, Matrix.ScaleToFit.CENTER);
                return;
            case INSIDE_END:
                matrix.setRectToRect(src, dst, Matrix.ScaleToFit.END);
                return;

        }

        float cropScale = getCropScale(texWidth, texHeight, viewportWidth, viewportHeight);
        matrix.setScale(cropScale, cropScale);
        float translateX = 0f;
        float translateY = 0f;
        switch (scaleType) {
            case CROP_END:
                translateX = viewportWidth - (texWidth * cropScale);
                translateY = viewportHeight - (texHeight * cropScale);
                break;
            case CROP_START:
                // 不需要做任何事情
                break;
            case CROP_CENTER:
                translateX = (viewportWidth - (texWidth * cropScale)) / 2f;
                translateY = (viewportHeight - (texHeight * cropScale)) / 2f;
                break;
        }
        matrix.postTranslate(translateX, translateY);
    }

    private float getCropScale(int texWidth, int texHeight, int viewportWidth, int viewportHeight) {
        float aspectTexture = texWidth / (float) texHeight;
        float aspectViewport = viewportWidth / (float) viewportHeight;
        float scale;
        if (aspectTexture > aspectViewport) {
            scale = (float) viewportHeight / texHeight;
        } else {
            scale = (float) viewportWidth / texWidth;
        }
        return scale;
    }
}
