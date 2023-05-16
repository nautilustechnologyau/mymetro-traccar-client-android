/*
 * Copyright 2023 Nautilus Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.mymetro.operator

import android.location.Location
import android.os.Build
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import au.mymetro.operator.ProtocolFormatter.formatRequest

@Config(sdk = [Build.VERSION_CODES.P])
@RunWith(RobolectricTestRunner::class)
class ProtocolFormatterTest {

    @Test
    fun testFormatRequest() {
        val position = Position("123456789012345", Location("gps"), BatteryStatus())
        val url = formatRequest("http://localhost:5055", position)
        Assert.assertEquals("http://localhost:5055?id=123456789012345&timestamp=619315200&lat=0.0&lon=0.0&speed=0.0&bearing=0.0&altitude=0.0&accuracy=0.0&batt=0.0", url)
    }

    @Test
    fun testFormatPathPortRequest() {
        val position = Position("123456789012345", Location("gps"), BatteryStatus())
        val url = formatRequest("http://localhost:8888/path", position)
        Assert.assertEquals("http://localhost:8888/path?id=123456789012345&timestamp=619315200&lat=0.0&lon=0.0&speed=0.0&bearing=0.0&altitude=0.0&accuracy=0.0&batt=0.0", url)
    }

    @Test
    fun testFormatAlarmRequest() {
        val position = Position("123456789012345", Location("gps"), BatteryStatus())
        val url = formatRequest("http://localhost:5055/path", position, "alert message")
        Assert.assertEquals("http://localhost:5055/path?id=123456789012345&timestamp=619315200&lat=0.0&lon=0.0&speed=0.0&bearing=0.0&altitude=0.0&accuracy=0.0&batt=0.0&alarm=alert%20message", url)
    }

}
