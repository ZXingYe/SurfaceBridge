package io.zxingye.surfacebridge;

import android.graphics.Matrix;

import io.zxingye.surfacebridge.transform.Transform;

public class EglVertexCoord {

    private final Matrix toOpenGLCoordMatrix = new Matrix();

    private final Matrix transformMatrix = new Matrix();

    private final float[] rectangleVertex = new float[8];

    private int viewportWidth;
    private int viewportHeight;

    public void updateViewport(int width, int height) {
        if (width == viewportWidth && height == viewportHeight) {
            return;
        }
        viewportWidth = width;
        viewportHeight = height;
        toOpenGLCoordMatrix.reset();
        toOpenGLCoordMatrix.postTranslate(-width / 2f, height / 2f);
        toOpenGLCoordMatrix.postScale(2f / width, 2f / height);
    }

    public float[] getVertexCoord(int width, int height, Transform transform) {
        rectangleVertex[0] = 0;
        rectangleVertex[1] = height;
        rectangleVertex[2] = width;
        rectangleVertex[3] = height;
        rectangleVertex[4] = 0;
        rectangleVertex[5] = 0;
        rectangleVertex[6] = width;
        rectangleVertex[7] = 0;
        transformMatrix.reset();
        if (transform != null) {
            transform.transform(transformMatrix, width, height, viewportWidth, viewportHeight);
        }
        transformMatrix.postScale(1, -1);
        transformMatrix.postConcat(toOpenGLCoordMatrix);
        transformMatrix.mapPoints(rectangleVertex);
        return rectangleVertex;
    }
}
