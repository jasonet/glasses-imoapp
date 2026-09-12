package com.jacb.inmocards

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig

class CardApplication : Application(), CameraXConfig.Provider {
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    override fun getCameraXConfig(): CameraXConfig {
        val air2BackCamera = CameraSelector.Builder()
            .addCameraFilter { cameraInfos ->
                cameraInfos.filter { Camera2CameraInfo.from(it).cameraId == "0" }
            }
            .build()
        return CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            .setAvailableCamerasLimiter(air2BackCamera)
            .build()
    }
}
