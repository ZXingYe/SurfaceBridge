package io.zxingye.library.surfaceextractor;

import android.util.Size;

import java.nio.ByteBuffer;

public interface OnFrameListener {
    /**
     * 帧数据的回调方法，改方法的回调频率一般和数据源的更新频率一致。
     * 如果在onFrame中执行了耗时方法会导致帧率下降，内部会丢弃onFrame返回前收到的帧。
     *
     * @param frame      帧数据，切记不要存储它，内部会不断复用这个buffer会填充数据。
     * @param resolution 帧的分辨率
     * @param format     帧的格式
     */
    void onFrame(ByteBuffer frame, Size resolution, FrameFormat format);
}
