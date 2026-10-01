package com.ecostep.app.sensors.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.ecostep.app.sensors.tracking.MotionSample
import com.ecostep.app.sensors.tracking.MotionType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

class MotionSensorTracker(context: Context) {
    private val manager = context.getSystemService(SensorManager::class.java)
    val hasGyroscope: Boolean get() = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
    val hasAccelerometer: Boolean get() = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    fun samples(): Flow<MotionSample> = callbackFlow {
        val thread = HandlerThread("EcoStepSensors").apply { start() }
        val handler = Handler(thread.looper)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val type = when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> MotionType.ACCELEROMETER
                    Sensor.TYPE_GYROSCOPE -> MotionType.GYROSCOPE
                    else -> return
                }
                trySend(MotionSample(type, event.values[0], event.values[1], event.values[2]))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE).forEach { type ->
            manager.getDefaultSensor(type)?.let {
                manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME, handler)
            }
        }
        awaitClose {
            manager.unregisterListener(listener)
            thread.quitSafely()
        }
    }.buffer(256)
}
