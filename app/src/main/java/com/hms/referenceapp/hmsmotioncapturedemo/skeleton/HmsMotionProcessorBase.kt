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
package com.hms.referenceapp.hmsmotioncapturedemo.skeleton

import android.graphics.Bitmap
import android.graphics.ImageFormat
import com.hms.referenceapp.hmsmotioncapturedemo.camera.BoneGLSurfaceView
import com.hms.referenceapp.hmsmotioncapturedemo.camera.FrameMetadata
import com.huawei.hmf.tasks.Task
import com.huawei.hms.motioncapturesdk.Modeling3dFrame
import java.nio.ByteBuffer

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