<h1 align="center">Live Motion Capture App Github Documentation</h3>
 
![Latest Version](https://img.shields.io/badge/latestVersion-1.0.0-yellow)
<br>
![Kotlin](https://img.shields.io/badge/Kotlin-000000?style=for-the-badge&logo=kotlin&logoColor=green)
<br>
![Supported Platforms](https://img.shields.io/badge/Supported_Platforms:-Native_Android-orange)


# Introduction

In this documentation, we explained the development steps of Live Motion Capture App.

This project gives users to chance of obtaining live joint and bone data from the phone cameras using HMS 3D Modelling Kit's Motion Capture feature.

# Input Specifications
Asynchronous API

320 x 320 ≤ input image resolution ≤ 1920 x 1080 (If the resolution is lower than the minimum, it will affect the detection precision. If the resolution is higher than the maximum, it will affect the detection frame rate.)

Video frame: NV21
Image: bitmap
Use the asynchronous API in scenarios like creating the preview on the camera screen.

# Output Specifications

Frame rate: greater than 30 fps on a phone with a mid-range or high-end chip.
Simultaneously outputted quaternions and 3D coordinates of 24 key skeleton points (as shown in the following figure) and the translation parameter of the root joint.

![image](https://user-images.githubusercontent.com/77769277/171853770-f08efcac-d2fa-4126-9679-fe37ef86562a.png)

The quaternions, 3D coordinates, and translation parameter of the root joint (manually specified as point 0) are located in the right-handed coordinate system. The quaternions are relative to the root joint. The 3D coordinates are the relative coordinates to the root joint. The translation parameter of the root joint is the absolute coordinates of the root joint in this coordinate system.

# Functions

- Uses both rear and front camera for capturing
- Captures live motion data from camera
- Animates into a 2D line character

# How to start?
  
# Register a developer account on HUAWEI Developers and configure.

1. Register in to [Huawei Developer Console] (https://developer.huawei.com/consumer/en/console) and Create and configure an app and enable 3D Modeling Kit in AppGallery Connect.
2. To use 3D Modeling Kit, you need to enable it in AppGallery Connect. For details, please refer to Enabling Services(https://developer.huawei.com/consumer/en/doc/distribution/app/agc-help-enabling-service-0000001146598793).


##   Adding the AppGallery Connect Configuration File of Your App
1. Sign in to AppGallery Connect and click My projects.
2. Find your project and click the app for which you want to integrate the HMS Core SDK.
3. Go to Project settings > General information. In the App information area, download the agconnect-services.json file.

 ##  Configuring the Maven Repository Address for the HMS Core SDK
1. Configuring the Maven Repository Address for the HMS Core SDK
2. Open the build.gradle file in the root directory of your Android Studio project.
3. Add the AppGallery Connect plugin and the Maven repository.

**Note** : A device with Huawei Mobile Services (HMS) installed is required
In the **project-level** build.gradle, include Huawei's Maven repository.

```groovy
buildscript { 
    repositories { 
        google() 
        jcenter() 
        // Configure the Maven repository address for the HMS Core SDK. 
        maven {url 'https://developer.huawei.com/repo/'} 
    } 
    dependencies { 
        ... 
        // Add the AppGallery Connect plugin configuration. You are advised to use the latest plugin version. 
        classpath 'com.huawei.agconnect:agcp:1.6.0.300' 
    } 
} 
 
allprojects { 
    repositories { 
        google() 
        jcenter() 
        // Configure the Maven repository address for the HMS Core SDK. 
        maven {url 'https://developer.huawei.com/repo/'} 
    } 
} 
```
4. Adding Build Dependencies
```groovy
dependencies { 
    ...
    implementation 'com.huawei.hms:modeling3d-motion-capture:1.3.0.300'
    implementation 'com.huawei.hms:modeling3d-motion-capture-model:1.3.0.300'
    implementation 'com.vmadalin:easypermissions-ktx:1.0.0'
    ...
}
```
## **Permissions**
```xml
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera.any" />
    <uses-feature android:name="android.hardware.camera.autofocus" />
```
## **Motion Capture**
```kotlin

abstract class HmsMotionProcessorBase<T> : HmsMotionImageProcessor {
    private var latestImage: ByteBuffer? = null
    private var latestImageMetaData: FrameMetadata? = null
    private var processingImage: ByteBuffer? = null
    private var processingMetaData: FrameMetadata? = null
    private var isStop = false
    override fun process(
        data: ByteBuffer?,
        frameMetadata: FrameMetadata?,
        graphicOverlay: BoneGLSurfaceView
    ) {
        latestImage = data
        latestImageMetaData = frameMetadata
        if (processingImage == null && processingMetaData == null) {
            processLatestImage(graphicOverlay)
        }
    }

    override fun process(bitmap: Bitmap?, graphicOverlay: BoneGLSurfaceView) {
        val frame = Modeling3dFrame.fromBitmap(bitmap)
        detectInVisionImage(
            null /* bitmap */, frame, null,
            graphicOverlay
        )
    }

    private fun processLatestImage(graphicOverlay: BoneGLSurfaceView) {
        processingImage = latestImage
        processingMetaData = latestImageMetaData
        latestImage = null
        latestImageMetaData = null
        if (processingImage != null && processingMetaData != null) {
            processImage(processingImage!!, processingMetaData!!, graphicOverlay)
        }
    }

    private fun processImage(
        data: ByteBuffer,
        frameMetadata: FrameMetadata,
        graphicOverlay: BoneGLSurfaceView
    ) {
        val quadrant = frameMetadata.rotation
        val property = Modeling3dFrame.Property.Creator().setFormatType(ImageFormat.NV21)
            .setWidth(frameMetadata.width)
            .setHeight(frameMetadata.height)
            .setQuadrant(quadrant)
            .create()
        detectInVisionImage(
            null,
            Modeling3dFrame.fromByteBuffer(data, property),
            frameMetadata,
            graphicOverlay
        )
    }

    private fun detectInVisionImage(
        originalCameraImage: Bitmap?,
        frame: Modeling3dFrame,
        metadata: FrameMetadata?,
        graphicOverlay: BoneGLSurfaceView
    ) {
        if (isStop) {
            return
        }
        detectInImage(frame)
            .addOnSuccessListener { results: T ->
                this@HmsMotionProcessorBase.onSuccess(
                    originalCameraImage, results,
                    metadata,
                    graphicOverlay
                )
                processLatestImage(graphicOverlay)
            }
            .addOnFailureListener { e: Exception -> this@HmsMotionProcessorBase.onFailure(e) }
    }

    override fun stop() {
        isStop = true
    }

    protected abstract fun detectInImage(frame: Modeling3dFrame): Task<T>
    protected abstract fun onSuccess(
        originalCameraImage: Bitmap?,
        results: T,
        frameMetadata: FrameMetadata?,
        graphicOverlay: BoneGLSurfaceView
    )

    protected abstract fun onFailure(e: Exception)
}
```
            
## **2D Line Character Animation**
We used OpenGL's scene ability.
To draw our character in the scene, we prepared a BoneGLSurfaceView which implements the GLSurfaceView.Renderer interface, which provides us necessary methods to build and animate our data.

```kotlin

class BoneGLSurfaceView : GLSurfaceView.Renderer {
    private var hasBoneData = false
    private var shaderProgram = 0
    private var uniformModelViewProjection = 0
    private var uniformColor = 0
    private var modelViewProjectionBuffer: FloatBuffer? = null
    private val vertexBuffer: FloatBuffer
    private val jointsPositions = FloatArray(24 * 3)
    private val leapedPositions = FloatArray(24 * 3)
    private val bonePairs = arrayOf(
        intArrayOf(0, 3),
        intArrayOf(3, 6),
        intArrayOf(6, 9),
        intArrayOf(9, 12),
        intArrayOf(12, 15),
        intArrayOf(0, 2),
        intArrayOf(2, 5),
        intArrayOf(5, 8),
        intArrayOf(8, 11),
        intArrayOf(9, 14),
        intArrayOf(14, 17),
        intArrayOf(17, 19),
        intArrayOf(19, 21),
        intArrayOf(21, 23),
        intArrayOf(0, 1),
        intArrayOf(1, 4),
        intArrayOf(4, 7),
        intArrayOf(7, 10),
        intArrayOf(9, 13),
        intArrayOf(13, 16),
        intArrayOf(16, 18),
        intArrayOf(18, 20),
        intArrayOf(20, 22)
    )
    private val bonePositions = FloatArray(bonePairs.size * 2 * 3)

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        GLES30.glClearColor(0.9f, 0.9f, 0.9f, 1.0f)
        val vertexShader = """
            #version 300 es 
            layout (location = 0) in vec4 vPosition;
            uniform mat4 uModelViewProj;
            void main() { 
            gl_Position = vPosition * uModelViewProj;
            }
            
            """.trimIndent()
        val fragmentShader = """
            #version 300 es 
            precision mediump float;
            out vec4 fragColor;
            uniform vec3 uColor;void main() { 
            fragColor = vec4(uColor, 0.0); 
            }
            
            """.trimIndent()
        shaderProgram = ShaderUtils.createProgram(vertexShader, fragmentShader)
        uniformModelViewProjection = GLES30.glGetUniformLocation(shaderProgram, "uModelViewProj")
        uniformColor = GLES30.glGetUniformLocation(shaderProgram, "uColor")
        GLES30.glLineWidth(5f)
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspectRatio = width.toFloat() / height
        val viewMatrix = FloatArray(16)
        val projMatrix = FloatArray(16)
        val modelMatrix = FloatArray(16)
        val modelViewMatrix = FloatArray(16)
        val modelViewProjection = FloatArray(16)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 4.5f, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.perspectiveM(projMatrix, 0, 25.0f, aspectRatio, 0.3f, 1000f)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.scaleM(modelMatrix, 0, 1f, 1f, 1f)
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(modelViewProjection, 0, projMatrix, 0, modelViewMatrix, 0)
        modelViewProjectionBuffer = ByteBuffer.allocateDirect(modelViewProjection.size * 4).order(
            ByteOrder.nativeOrder()
        ).asFloatBuffer()
        modelViewProjectionBuffer?.put(modelViewProjection)
        modelViewProjectionBuffer?.position(0)
    }

    override fun onDrawFrame(gl: GL10) {
        GLES30.glUseProgram(shaderProgram)
        GLES20.glClear(GL10.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (!hasBoneData) {
            return
        }
        GLES30.glUniformMatrix4fv(uniformModelViewProjection, 1, false, modelViewProjectionBuffer)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, vertexBuffer)
        GLES30.glEnableVertexAttribArray(0)
        updateVertexBuffer()
        GLES30.glUniform3f(uniformColor, 1.0f, 0.0f, 0.0f)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, MIDDLE_BONE_COUNT)
        GLES30.glUniform3f(uniformColor, 0.0f, 1.0f, 0.0f)
        GLES30.glDrawArrays(GLES30.GL_LINES, MIDDLE_BONE_COUNT, LEFT_BONE_COUNT)
        GLES30.glUniform3f(uniformColor, 0.0f, 0.0f, 1.0f)
        GLES30.glDrawArrays(GLES30.GL_LINES, MIDDLE_BONE_COUNT + LEFT_BONE_COUNT, RIGHT_BONE_COUNT)
    }

    fun setData(joints: List<List<Float>>?, trans: List<Float>) {
        if (joints == null) {
            hasBoneData = false
            return
        }
        hasBoneData = true
        var index = 0
        for (i in joints.indices) {
            for (j in joints[i].indices) {
                when (j) {
                    1 -> {
                        jointsPositions[index] = -joints[i][j] - trans[j]
                    }
                    0 -> {
                        jointsPositions[index] = joints[i][j] + trans[j]
                    }
                    else -> {
                        jointsPositions[index] = joints[i][j]
                    }
                }
                index++
            }
        }
    }

    private fun updateVertexBuffer() {
        // Interpolation
        for (i in jointsPositions.indices) {
            leapedPositions[i] = leap(leapedPositions[i], jointsPositions[i])
        }

        // Get the vertex array of the bone connection
        var index = 0
        for (bonePair in bonePairs) {
            bonePositions[index++] = leapedPositions[bonePair[0] * 3]
            bonePositions[index++] = leapedPositions[bonePair[0] * 3 + 1]
            bonePositions[index++] = leapedPositions[bonePair[0] * 3 + 2]
            bonePositions[index++] = leapedPositions[bonePair[1] * 3]
            bonePositions[index++] = leapedPositions[bonePair[1] * 3 + 1]
            bonePositions[index++] = leapedPositions[bonePair[1] * 3 + 2]
        }
        vertexBuffer.clear()
        vertexBuffer.put(bonePositions)
        vertexBuffer.position(0)
    }

    private fun leap(a: Float, b: Float): Float {
        return a + (b - a) * 0.1.toFloat()
    }

    companion object {
        // Different colors for the middle, left and right bones
        private const val MIDDLE_BONE_COUNT = 10
        private const val LEFT_BONE_COUNT = 18
        private const val RIGHT_BONE_COUNT = 18
    }

    init {
        vertexBuffer =
            ByteBuffer.allocateDirect(bonePositions.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer()
    }
}
```
## **Main Page**

![Screenshot_20220603_153647_com hms referenceapp hmsmotioncapturedemo](https://user-images.githubusercontent.com/77769277/171855183-68460b11-bb7a-41ed-88ad-255a2d6dd450.jpg)

## **3D Modelling Page**

![Screenshot_20220603_153709_com hms referenceapp hmsmotioncapturedemo](https://user-images.githubusercontent.com/77769277/171855193-48aabc78-d7b4-493a-89b3-e4f2f06ac880.jpg)
