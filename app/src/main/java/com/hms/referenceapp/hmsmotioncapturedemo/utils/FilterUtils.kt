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
package com.hms.referenceapp.hmsmotioncapturedemo.utils

import com.huawei.hms.motioncapturesdk.Modeling3dMotionCaptureSkeleton
import java.util.*

object FilterUtils {
    fun filterDataQuaternions(fromData: Modeling3dMotionCaptureSkeleton): List<List<Float>> {
        val quaternions: MutableList<List<Float>> = ArrayList()
        for (n in fromData.jointQuaternions.indices) {
            val rotParcelList: MutableList<Float> = ArrayList()
            rotParcelList.add(fromData.jointQuaternions[n].pointW)
            rotParcelList.add(fromData.jointQuaternions[n].pointX)
            rotParcelList.add(fromData.jointQuaternions[n].pointY)
            rotParcelList.add(fromData.jointQuaternions[n].pointZ)
            quaternions.add(rotParcelList)
        }
        return quaternions
    }

    fun filterDataJointShift(fromData: Modeling3dMotionCaptureSkeleton): List<Float> {
        return if (fromData.jointShift != null) {
            fromData.jointShift
        } else ArrayList()
    }

    fun filterDataJoints3ds(fromData: Modeling3dMotionCaptureSkeleton): List<List<Float>> {
        val joints3ds: MutableList<List<Float>> = ArrayList()
        for (j in fromData.joints.indices) {
            val jointParcelList: MutableList<Float> = ArrayList()
            jointParcelList.add(fromData.joints[j].pointX)
            jointParcelList.add(fromData.joints[j].pointY)
            jointParcelList.add(fromData.joints[j].pointZ)
            joints3ds.add(jointParcelList)
        }
        return joints3ds
    }
}