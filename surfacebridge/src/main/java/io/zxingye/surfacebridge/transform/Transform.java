package io.zxingye.surfacebridge.transform;

import android.graphics.Matrix;

public interface Transform {
    void transform(Matrix matrix,
                   int texWidth,
                   int texHeight,
                   int viewportWidth,
                   int viewportHeight);
}
