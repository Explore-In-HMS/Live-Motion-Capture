/*
 * Copyright 2021. Huawei Technologies Co., Ltd. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hms.referenceapp.hmsmotioncapturedemo.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.hardware.Camera.PreviewCallback
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.WindowManager
import androidx.annotation.RequiresPermission
import com.hms.referenceapp.hmsmotioncapturedemo.skeleton.HmsMotionImageProcessor
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

/**
 * Manages the camera and allows UI updates on top of it (e.g. overlaying extra Graphics or
 * displaying extra information). This receives preview frames from the camera at a specified rate,
 * sending those frames to child classes' detectors / classifiers as fast as it is able to process.
 */
@SuppressLint("MissingPermission")
class CameraSource {
    protected var activity: Activity
    var camera: Camera? = null
    var cameraFacing = CAMERA_FACING_BACK
        protected set

    /**
     * Rotation of the device, and thus the associated preview images captured from the device. See
     * Frame.Metadata#getRotation().
     */
    private var rotation = 0
    var previewSize: Size? = null
        private set
    private val requestedFps = 30.0f
    private var requestedPreviewWidth = 640
    private var requestedPreviewHeight = 480
    private val requestedAutoFocus = true

    // These instances need to be held onto to avoid GC of their underlying resources.  Even though
    // these aren't used outside of the method that creates them, they still must have hard
    // references maintained to them.
    private val dummySurfaceTexture: SurfaceTexture? = null
    private var graphicOverlay: GraphicOverlay? = null
    private lateinit var glSurfaceView: BoneGLSurfaceView

    // True if a SurfaceTexture is being used for the preview, false if a SurfaceHolder is being
    // used for the preview.  We want to be compatible back to Gingerbread, but SurfaceTexture
    // wasn't introduced until Honeycomb.  Since the interface cannot use a SurfaceTexture, if the
    // developer wants to display a preview we must use a SurfaceHolder.  If the developer doesn't
    // want to display a preview we use a SurfaceTexture if we are running at least Honeycomb.
    private var usingSurfaceTexture = false

    /**
     * Dedicated thread and associated runnable for calling into the detector with frames, as the
     * frames become available from the camera.
     */
    private var processingThread: Thread? = null
    private val processingRunnable: FrameProcessingRunnable
    private val processorLock = Any()
    private var frameProcessor: HmsMotionImageProcessor? = null

    /**
     * Map to convert between a byte array, received from the camera, and its associated byte buffer.
     * We use byte buffers internally because this is a more efficient way to call into native code
     * later (avoids a potential copy).
     *
     *
     * **Note:** uses IdentityHashMap here instead of HashMap because the behavior of an array's
     * equals, hashCode and toString methods is both useless and unexpected. IdentityHashMap enforces
     * identity ('==') check on the keys.
     */
    private val bytesToByteBuffer: MutableMap<ByteArray, ByteBuffer> = IdentityHashMap()

    constructor(activity: Activity, overlay: GraphicOverlay?) {
        this.activity = activity
        graphicOverlay = overlay
        graphicOverlay!!.clear()
        processingRunnable = FrameProcessingRunnable()
        if (Camera.getNumberOfCameras() == 1) {
            val cameraInfo = CameraInfo()
            Camera.getCameraInfo(0, cameraInfo)
            cameraFacing = cameraInfo.facing
        }
    }

    constructor(activity: Activity, overlay: BoneGLSurfaceView) {
        this.activity = activity
        glSurfaceView = overlay
        processingRunnable = FrameProcessingRunnable()
        if (Camera.getNumberOfCameras() == 1) {
            val cameraInfo = CameraInfo()
            Camera.getCameraInfo(0, cameraInfo)
            cameraFacing = cameraInfo.facing
        }
    }

    fun release() {
        synchronized(processorLock) {
            stop()
            processingRunnable.release()
            cleanScreen()
            if (frameProcessor != null) {
                frameProcessor!!.stop()
            }
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    @Synchronized
    @Throws(IOException::class)
    fun start(surfaceHolder: SurfaceHolder?): CameraSource {
        if (camera != null) {
            return this
        }
        camera = createCamera()
        usingSurfaceTexture = false
        camera!!.setPreviewDisplay(surfaceHolder)
        camera!!.startPreview()
        processingThread = Thread(processingRunnable)
        processingRunnable.setActive(true)
        processingThread!!.start()
        return this
    }

    @Synchronized
    fun stop() {
        processingRunnable.setActive(false)
        if (processingThread != null) {
            try {
                // Wait for the thread to complete to ensure that we can't have multiple threads
                // executing at the same time (i.e., which would happen if we called start too
                // quickly after stop).
                processingThread!!.join()
            } catch (e: InterruptedException) {
                Log.d(TAG, "Frame processing thread interrupted on release.")
            }
            processingThread = null
        }
        if (camera != null) {
            camera!!.stopPreview()
            camera!!.setPreviewCallbackWithBuffer(null)
            try {
                if (usingSurfaceTexture) {
                    camera!!.setPreviewTexture(null)
                } else {
                    camera!!.setPreviewDisplay(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear camera preview: $e")
            }
            camera!!.release()
            camera = null
        }

        // Release the reference to any image buffers, since these will no longer be in use.
        bytesToByteBuffer.clear()
    }

    @Synchronized
    fun setFacing(facing: Int) {
        require(!(facing != CAMERA_FACING_BACK && facing != CAMERA_FACING_FRONT)) { "Invalid camera: $facing" }
        cameraFacing = facing
    }

    @SuppressLint("InlinedApi")
    @Throws(IOException::class)
    private fun createCamera(): Camera {
        val requestedCameraId = getIdForRequestedCamera(cameraFacing)
        val camera = Camera.open(requestedCameraId)
        val sizePair = selectSizePair(camera, requestedPreviewWidth, requestedPreviewHeight)
        val pictureSize = sizePair!!.pictureSize()
        previewSize = sizePair.previewSize()
        val previewFpsRange = selectPreviewFpsRange(camera, requestedFps)
            ?: throw IOException("Could not find suitable preview frames per second range.")
        val parameters = camera.parameters
        if (pictureSize != null) {
            parameters.setPictureSize(pictureSize.width, pictureSize.height)
        }
        parameters.setPreviewSize(previewSize!!.width, previewSize!!.height)
        parameters.setPreviewFpsRange(
            previewFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
            previewFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]
        )
        parameters.previewFormat = ImageFormat.NV21
        setRotation(camera, parameters, requestedCameraId)
        if (requestedAutoFocus) {
            if (parameters
                    .supportedFocusModes
                    .contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)
            ) {
                parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
            } else {
                Log.i(TAG, "Camera auto focus is not supported on this device.")
            }
        }
        camera.parameters = parameters
        camera.setPreviewCallbackWithBuffer(CameraPreviewCallback())
        camera.addCallbackBuffer(createPreviewBuffer(previewSize))
        camera.addCallbackBuffer(createPreviewBuffer(previewSize))
        camera.addCallbackBuffer(createPreviewBuffer(previewSize))
        camera.addCallbackBuffer(createPreviewBuffer(previewSize))
        return camera
    }

    private class SizePair internal constructor(
        previewSize: Camera.Size,
        pictureSize: Camera.Size?
    ) {
        private val preview: Size
        private var picture: Size? = null
        fun previewSize(): Size {
            return preview
        }

        fun pictureSize(): Size? {
            return picture
        }

        init {
            preview = Size(previewSize.width, previewSize.height)
            if (pictureSize != null) {
                picture = Size(pictureSize.width, pictureSize.height)
            }
        }
    }

    private fun setRotation(camera: Camera, parameters: Camera.Parameters, cameraId: Int) {
        val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        var degrees = 0
        val rotation = windowManager.defaultDisplay.rotation
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
            else -> Log.e(TAG, "Bad rotation value: $rotation")
        }
        val cameraInfo = CameraInfo()
        Camera.getCameraInfo(cameraId, cameraInfo)
        val angle: Int
        val displayAngle: Int
        if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
            angle = (cameraInfo.orientation + degrees) % 360
            displayAngle = (360 - angle) % 360 // compensate for it being mirrored
        } else { // back-facing
            angle = (cameraInfo.orientation - degrees + 360) % 360
            displayAngle = angle
        }

        // This corresponds to the rotation constants.
        this.rotation = angle / 90
        camera.setDisplayOrientation(displayAngle)
        parameters.setRotation(angle)
    }

    @SuppressLint("InlinedApi")
    private fun createPreviewBuffer(previewSize: Size?): ByteArray {
        val bitsPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.NV21)
        val sizeInBits = previewSize!!.height.toLong() * previewSize.width * bitsPerPixel
        val bufferSize = Math.ceil(sizeInBits / 8.0).toInt() + 1

        // Creating the byte array this way and wrapping it, as opposed to using .allocate(),
        // should guarantee that there will be an array to work with.
        val byteArray = ByteArray(bufferSize)
        val buffer = ByteBuffer.wrap(byteArray)
        check(!(!buffer.hasArray() || buffer.array() != byteArray)) {
            // I don't think that this will ever happen.  But if it does, then we wouldn't be
            // passing the preview content to the underlying detector later.
            "Failed to create valid buffer for camera source."
        }
        bytesToByteBuffer[byteArray] = buffer
        return byteArray
    }
    // ==============================================================================================
    // Frame processing
    // ==============================================================================================
    /**
     * Called when the camera has a new preview frame.
     */
    private inner class CameraPreviewCallback : PreviewCallback {
        override fun onPreviewFrame(data: ByteArray, camera: Camera) {
            processingRunnable.setNextFrame(data, camera)
        }
    }

    fun setMachineLearningFrameProcessor(processor: HmsMotionImageProcessor?) {
        synchronized(processorLock) {
            cleanScreen()
            if (frameProcessor != null) {
                frameProcessor!!.stop()
            }
            frameProcessor = processor
        }
    }

    fun setRequestedPreviewWidth(requestedPreviewWidth: Int) {
        this.requestedPreviewWidth = requestedPreviewWidth
    }

    fun setRequestedPreviewHeight(requestedPreviewHeight: Int) {
        this.requestedPreviewHeight = requestedPreviewHeight
    }

    /**
     * This runnable controls access to the underlying receiver, calling it to process frames when
     * available from the camera. This is designed to run detection on frames as fast as possible
     * (i.e., without unnecessary context switching or waiting on the next frame).
     *
     *
     * While detection is running on a frame, new frames may be received from the camera. As these
     * frames come in, the most recent frame is held onto as pending. As soon as detection and its
     * associated processing is done for the previous frame, detection on the mostly recently received
     * frame will immediately start on the same thread.
     */
    private inner class FrameProcessingRunnable internal constructor() : Runnable {
        // This lock guards all of the member variables below.
        private val lock = Object()
        private var active = true

        // These pending variables hold the state associated with the new frame awaiting processing.
        private var pendingFrameData: ByteBuffer? = null

        /**
         * Releases the underlying receiver. This is only safe to do after the associated thread has
         * completed, which is managed in camera source's release method above.
         */
        @SuppressLint("Assert")
        fun release() {
        }

        fun setActive(active: Boolean) {
            synchronized(lock) {
                this.active = active
                lock.notifyAll()
            }
        }

        fun setNextFrame(data: ByteArray, camera: Camera) {
            synchronized(lock) {
                if (pendingFrameData != null) {
                    camera.addCallbackBuffer(pendingFrameData!!.array())
                    pendingFrameData = null
                }
                if (!bytesToByteBuffer.containsKey(data)) {
                    Log.d(
                        TAG,
                        "Skipping frame. Could not find ByteBuffer associated with the image " + "data from the camera."
                    )
                    return
                }
                pendingFrameData = bytesToByteBuffer[data]

                // Notify the processor thread if it is waiting on the next frame (see below).
                lock.notifyAll()
            }
        }

        /**
         * As long as the processing thread is active, this executes detection on frames continuously.
         * The next pending frame is either immediately available or hasn't been received yet. Once it
         * is available, we transfer the frame info to local variables and run detection on that frame.
         * It immediately loops back for the next frame without pausing.
         *
         *
         * If detection takes longer than the time in between new frames from the camera, this will
         * mean that this loop will run without ever waiting on a frame, avoiding any context switching
         * or frame acquisition time latency.
         *
         *
         * If you find that this is using more CPU than you'd like, you should probably decrease the
         * FPS setting above to allow for some idle time in between frames.
         */
        @SuppressLint("InlinedApi")
        override fun run() {
            var data: ByteBuffer?
            while (true) {
                synchronized(lock) {
                    while (active && pendingFrameData == null) {
                        try {
                            // Wait for the next frame to be received from the camera, since we
                            // don't have it yet.
                            lock.wait()
                        } catch (e: InterruptedException) {
                            Log.w(TAG, "Frame processing loop terminated.", e)
                            return
                        }
                    }
                    if (!active) {
                        // Exit the loop once this camera source is stopped or released.  We check
                        // this here, immediately after the wait() above, to handle the case where
                        // setActive(false) had been called, triggering the termination of this
                        // loop.
                        return
                    }

                    // Hold onto the frame data locally, so that we can use this for detection
                    // below.  We need to clear pendingFrameData to ensure that this buffer isn't
                    // recycled back to the camera before we are done using that data.
                    data = pendingFrameData
                    pendingFrameData = null
                }

                // The code below needs to run outside of synchronization, because this will allow
                // the camera to add pending frame(s) while we are running detection on the current
                // frame.
                try {
                    synchronized(processorLock) {
                        Log.d(TAG, "Process an image")
                        frameProcessor!!.process(
                            data, FrameMetadata.Builder()
                                .setWidth(previewSize!!.width)
                                .setHeight(previewSize!!.height)
                                .setRotation(rotation)
                                .setCameraFacing(cameraFacing)
                                .build(),
                            glSurfaceView
                        )
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Exception thrown from receiver.", t)
                } finally {
                    camera!!.addCallbackBuffer(data!!.array())
                }
            }
        }
    }

    class Size(val width: Int, val height: Int)

    /**
     * Cleans up graphicOverlay and child classes can do their cleanups as well .
     */
    private fun cleanScreen() {
        if (graphicOverlay != null) {
            graphicOverlay!!.clear()
        }
    }

    companion object {
        @SuppressLint("InlinedApi")
        val CAMERA_FACING_BACK = CameraInfo.CAMERA_FACING_BACK

        @SuppressLint("InlinedApi")
        val CAMERA_FACING_FRONT = CameraInfo.CAMERA_FACING_FRONT
        private const val TAG = "MIDemoApp:CameraSource"

        /**
         * The dummy surface texture must be assigned a chosen name. Since we never use an OpenGL context,
         * we can choose any ID we want here. The dummy surface texture is not a crazy hack - it is
         * actually how the camera team recommends using the camera without a preview.
         */
        private const val DUMMY_TEXTURE_NAME = 100

        /**
         * If the absolute difference between a preview size aspect ratio and a picture size aspect ratio
         * is less than this tolerance, they are considered to be the same aspect ratio.
         */
        private const val ASPECT_RATIO_TOLERANCE = 0.01f
        const val WIDTH_SIZE = 1280
        const val HEIGHT_SIZE = 720
        private fun getIdForRequestedCamera(facing: Int): Int {
            val cameraInfo = CameraInfo()
            for (i in 0 until Camera.getNumberOfCameras()) {
                Camera.getCameraInfo(i, cameraInfo)
                if (cameraInfo.facing == facing) {
                    return i
                }
            }
            return -1
        }

        private fun selectSizePair(
            camera: Camera,
            desiredWidth: Int,
            desiredHeight: Int
        ): SizePair? {
            val validPreviewSizes = generateValidPreviewSizeList(camera)
            var selectedPair: SizePair? = null
            var minDiff = Int.MAX_VALUE
            for (sizePair in validPreviewSizes) {
                val size = sizePair.previewSize()
                val diff =
                    Math.abs(size.width - desiredWidth) + Math.abs(size.height - desiredHeight)
                if (diff < minDiff) {
                    selectedPair = sizePair
                    minDiff = diff
                }
            }
            return selectedPair
        }

        private fun generateValidPreviewSizeList(camera: Camera): List<SizePair> {
            val parameters = camera.parameters
            val supportedPreviewSizes = parameters.supportedPreviewSizes
            val supportedPictureSizes = parameters.supportedPictureSizes
            val validPreviewSizes: MutableList<SizePair> = ArrayList()
            for (previewSize in supportedPreviewSizes) {
                val previewAspectRatio = previewSize.width.toFloat() / previewSize.height.toFloat()
                for (pictureSize in supportedPictureSizes) {
                    val pictureAspectRatio =
                        pictureSize.width.toFloat() / pictureSize.height.toFloat()
                    if (Math.abs(previewAspectRatio - pictureAspectRatio) < ASPECT_RATIO_TOLERANCE) {
                        validPreviewSizes.add(SizePair(previewSize, pictureSize))
                        break
                    }
                }
            }
            if (validPreviewSizes.size == 0) {
                Log.w(TAG, "No preview sizes have a corresponding same-aspect-ratio picture size")
                for (previewSize in supportedPreviewSizes) {
                    validPreviewSizes.add(SizePair(previewSize, null))
                }
            }
            return validPreviewSizes
        }

        /**
         * Selects the most suitable preview frames per second range, given the desired frames per second.
         *
         * @param camera            the camera to select a frames per second range from
         * @param desiredPreviewFps the desired frames per second for the camera preview frames
         * @return the selected preview frames per second range
         */
        @SuppressLint("InlinedApi")
        private fun selectPreviewFpsRange(camera: Camera, desiredPreviewFps: Float): IntArray? {
            val desiredPreviewFpsScaled = (desiredPreviewFps * 1000.0f).toInt()
            var selectedFpsRange: IntArray? = null
            var minDiff = Int.MAX_VALUE
            val previewFpsRangeList = camera.parameters.supportedPreviewFpsRange
            for (range in previewFpsRangeList) {
                val deltaMin =
                    desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]
                val deltaMax =
                    desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]
                val diff = Math.abs(deltaMin) + Math.abs(deltaMax)
                if (diff < minDiff) {
                    selectedFpsRange = range
                    minDiff = diff
                }
            }
            return selectedFpsRange
        }
    }
}