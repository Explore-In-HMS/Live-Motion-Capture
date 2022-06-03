/**
 * Copyright 2021. Huawei Technologies Co., Ltd. All rights reserved.
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hms.referenceapp.hmsmotioncapturedemo.skeleton

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.hms.referenceapp.hmsmotioncapturedemo.camera.BoneGLSurfaceView
import com.hms.referenceapp.hmsmotioncapturedemo.camera.FrameMetadata
import com.hms.referenceapp.hmsmotioncapturedemo.utils.FilterUtils
import com.huawei.hmf.tasks.Task
import com.huawei.hms.motioncapturesdk.*
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

class LocalSkeletonProcessor(context: Context) :
    HmsMotionProcessorBase<List<Modeling3dMotionCaptureSkeleton>>() {
    val detector: Modeling3dMotionCaptureEngine
    private var mFrameMetadata: FrameMetadata? = null
    private var latestImage: ByteBuffer? = null
    var joints3d: MutableList<List<List<Float>>> = ArrayList()
    var quaternion: MutableList<List<List<Float>>> = ArrayList()
    var shift: MutableList<List<Float>> = ArrayList()
    var mContext: Context
    override fun stop() {
        super.stop()
        try {
            detector.stop()
        } catch (e: IOException) {
            Log.e(TAG, "Exception thrown while trying to close cloud image labeler!", e)
        }
    }

    public override fun detectInImage(frame: Modeling3dFrame): Task<List<Modeling3dMotionCaptureSkeleton>> {
        latestImage = frame.byteBuffer
        return detector.asyncAnalyseFrame(frame)
    }

    override fun onSuccess(
        originalCameraImage: Bitmap?,
        skeletons: List<Modeling3dMotionCaptureSkeleton>,
        frameMetadata: FrameMetadata?,
        graphicOverlay: BoneGLSurfaceView
    ) {
        mFrameMetadata = frameMetadata
        Log.d(TAG, "skeleton detection success $skeletons")
        if (skeletons.size > 0) {
            for (i in skeletons.indices) {
                joints3d.add(FilterUtils.filterDataJoints3ds(skeletons[i]))
                quaternion.add(FilterUtils.filterDataQuaternions(skeletons[i]))
                shift.add(FilterUtils.filterDataJointShift(skeletons[i]))
            }
            graphicOverlay.setData(joints3d[joints3d.size - 1], shift[shift.size - 1])
        }
        //        if (skeletons == null || skeletons.isEmpty()) {
//            graphicOverlay.setData(null, null);
//        }
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "skeleton detection failed $e")
    }

    companion object {
        private const val TAG = "Skeleton"
        const val UPDATE_SCORES_VIEW = 1010
    }

    /**
     * Construction method
     *
     * @param context Context
     */
    init {
        val setting = Modeling3dMotionCaptureEngineSetting.Factory()
            .setAnalyzeType(
                Modeling3dMotionCaptureEngineSetting.TYPE_3DSKELETON_QUATERNION
                        or Modeling3dMotionCaptureEngineSetting.TYPE_3DSKELETON
            )
            .create()
        detector =
            Modeling3dMotionCaptureEngineFactory.getInstance().getMotionCaptureEngine(setting)
        mContext = context
    }
}