package com.example.sensoranalyzer

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.sensoranalyzer.ui.theme.SensorAnalyzerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.net.Socket

class MainActivity : ComponentActivity(), SensorEventListener {

    companion object {
        private const val TAG = "SensorCollector"
    }
    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null

    private var socket: Socket? = null
    private var writer: PrintWriter? = null

    private val serverHost = "192.168.8.134" // <-- Your PC IP
    private val serverPort = 5010

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Log.d(TAG, "App started. Initializing sensors and TCP connection.")

        // Initialize sensors
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // Register listeners
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            Log.d(TAG, "Accelerometer registered")
        }
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            Log.d(TAG, "Gyroscope registered")
        }

        // Connect to TCP server in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Connecting to TCP server $serverHost:$serverPort...")
                socket = Socket(serverHost, serverPort)
                writer = PrintWriter(socket!!.getOutputStream(), true)
                Log.d(TAG, "Connected to TCP server successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to TCP server", e)
            }
        }

        setContent {
            SensorAnalyzerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Sensor Data Collector",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        val json = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                """{"ax":${event.values[0]},"ay":${event.values[1]},"az":${event.values[2]}}"""
            }
            Sensor.TYPE_GYROSCOPE -> {
                """{"gx":${event.values[0]},"gy":${event.values[1]},"gz":${event.values[2]}}"""
            }
            else -> return
        }

        Log.d(TAG, "Sensor event: $json")

        // Send data in background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                writer?.println(json)
                Log.d(TAG, "Sent JSON to TCP server")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send JSON", e)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Sensor accuracy changed: $sensor -> $accuracy")
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        writer?.close()
        socket?.close()
        Log.d(TAG, "Sensors unregistered and TCP connection closed")
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name! Sensor streaming active.",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SensorAnalyzerTheme {
        Greeting("Sensor Data Collector")
    }
}
