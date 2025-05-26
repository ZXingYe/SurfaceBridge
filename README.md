# SurfaceBridge

SurfaceBridge是一个用于将Surface图像数据快速提取出来的工具类，Surface数据源可以来自MediaCodec、Camera、VirtualDisplay等。
SurfaceBridge支持多种输出格式，比如RGB、YUV420、YUV444等等。当然，你也可以直接把SurfaceView、TextureView作为输出源。同时为了兼容各种不同的输出大小和比例，SurfaceBridge支持传入一个Matrix参数去控制图像的变换。
![](readme_src/img_introduction_1.png)

## 支持输出格式

### 帧格式
* RGBA
  * RGBA_8888, RGBX_8888, RGB_888, RGB_565
* BGRA
  * BGRA_8888, BGRX_8888
* YUV_420
  * NV12, NV21, YU12, YV12
* YUV_422
    * YUVY, YUYV, YVYU, UYVY, VYUY
* YUV_444
    * I444

### 颜色空间
* BT.601（default）
* BT.709
* BT.2020

## 示例工程

可以通过运行demo（App module）去学习SurfaceBridge的用法，demo展示了如何渲染来自不同数据源的数据，并且通过设置不同的ScaleType去变换图像。同时也展示了如何去获取不同的原始帧格式。

<img src='readme_src/img_demo_1.png' height='360'/> <img src='readme_src/img_demo_2.png' height='360'/> <img src='readme_src/img_demo_3.png' height='360'/>

## 引入依赖

### Gradle
```Gradle
repositories {
  ......
  mavenCentral()
}

dependencies {
  implementation 'io.github.zxingye:surfacebridge:1.0.0'
}
```

### Maven
```xml
<dependency>
  <groupId>io.github.zxingye</groupId>
  <artifactId>surfacebridge</artifactId>
  <version>1.0.0</version>
</dependency>
```

## 使用

### 设置数据源
请使用以前其中其中方式设置数据源，不可让多个数据源同时绑定一个SurfaceBridge。
数据源不限于以下几种，一般来说只要能给SurfaceTexture写数据的，就都支持。

```Java
SurfaceBridge bridge = new SurfaceBridge();
SurfaceBridge bridgeTexture = bridge.getInputSurfaceTexture();
Surface bridgeSurface = new Surface(bridgeTexture);

// For MediaPlay
mediaPlay.setSurface(bridgeSurface);
mediaPlay.prepare();
mediaPlay.start();

// For MediaCodec
mediaCodec.configure(format, bridgeSurface, null, 0);
mediaCodec.start();

// For MediaCodec
mediaCodec.configure(format, bridgeSurface, null, 0);
mediaCodec.start();

// For VirtualDisplay
virtualDisplay.setSurface(bridgeSurface);

// For Camera api
camera.setPreviewTexture(bridgeTexture);
camera.startPreview();

// For Camera2 api
CaptureRequest.Builder builder = camera.createCaptureRequest(...);
builder.addTarget(surface);

// For Custom draw
Canvas canvas = bridgeSurface.lockHardwareCanvas();
canvas.drawXXX(...);
bridgeSurface.unlockCanvasAndPost(canvas);

```

### 添加渲染输出（比如SurfaceView、TextureView等）

```Java
/**
 * 添加一个输出Surface，可以多次重复put同一个surface，内部会做参数覆盖
 * 支持指定输出的大小，如果大小是负值，表示不指定，这个时候内部会动态获取实际的surface大小
 *
 * @param surface     用于输出的surface，常来源于SurfaceView或TextureView等。
 * @param surfaceSize 指定输出的大小，不能为空，但是可以为负值。
 * @param transform   用于做输出变换，比如放大缩小、平移、旋转等操作，如果为null表示不做任何变换
 */
public void putOutputSurface(Surface surface, 
                             Size surfaceSize, 
                             Transform transform);

......
......
SurfaceBridge bridge = new SurfaceBridge();
bridge.putOutputSurface(outputSurface, suraceSize, new ScaleToFitTransform(scaleType))
```

### 添加帧输出监听

```Java
/**
 * 添加一个Listener用于监听帧输出，可以指定帧输出的格式、大小、变换
 *
 * @param format     输出的格式
 * @param outputSize 输出的大小，不能为null，但是可以为负值，表示输出原始大小。
 * @param transform  帧图像的变换，如果不为空会在输出帧之前进行图像变换，一般用于各种缩放适配。
 * @param listener   监听器，不同的格式可以公用一个监听器。
 */
public void addOnFrameListener(FrameFormat format,
                               Size outputSize,
                               Transform transform,
                               OnFrameListener listener);

......
......
SurfaceBridge bridge = new SurfaceBridge();
bridge.addOnFrameListener(FrameFormat.RGB_888, new Size(-1, -1), null, new OnFrameListener() {
  /**
   * 帧数据的回调方法，改方法的回调频率一般和数据源的更新频率一致。
   * 如果在onFrame中执行了耗时方法会导致帧率下降，内部会丢弃onFrame返回前收到的帧。
   *
   * @param frame      帧数据，切记不要存储它，内部会不断复用这个buffer会填充数据。
   * @param resolution 帧的分辨率
   * @param format     帧的格式
   */
  @Override
  public void onFrame(ByteBuffer frame, Size resolution, FrameFormat format) {
    // handle frame
  }
});
```

### 释放资源
```Java
surfaceBridge.removeOutputSurface(outputSurface);
surfaceBridge.removeOnFrameListener(listener);
surfaceBridge.release();
```

### 其他

#### 建议设置数据源的原始Surface大小以提高性能，如果欸有设置，那么SurfaceBridge内部每次渲染都会使用OpenGL动态获取Surface大小，但是如果你主动调用了`setDefaultInputBufferSize`，那么就会使用你设置的值。。
* 如果你的数据源来自相机，要么真正数据源的大小就是你的预览大小
* 如果你的数据源来自解码器或者播放器，那么数据源的大小就应该是视频的分辨率。
* 如果你的数据源来自于其他实现，尽量看看能不能获取到实际的数据源大小，
```Java
surfaceBridge.setDefaultInputBufferSize(realInputWidth, realInputHeight);
```

#### 设置默认背景色，当前默认为黑色
```Java
surfaceBridge.setBackgroundColor(color);
```

#### 设置YUV的颜色空间，当前默认为BT.601
```Java
surfaceBridge.setYUVColorSpace(EglYUVColorSpace);
```

### 未来
* 支持10Bit颜色
* 支持自定义着色器用于帧格式的转换
* 支持RGB到各种格式的同步转换方法。