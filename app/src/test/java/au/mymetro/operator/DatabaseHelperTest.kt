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
import androidx.test.core.app.ApplicationProvider
import au.mymetro.operator.BatteryStatus
import au.mymetro.operator.DatabaseHelper
import au.mymetro.operator.Position
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.P])
@RunWith(RobolectricTestRunner::class)
class DatabaseHelperTest {
    @Test
    fun test() {

        val databaseHelper = DatabaseHelper(ApplicationProvider.getApplicationContext())

        var position: Position? = Position("123456789012345", Location("gps"), BatteryStatus())

        Assert.assertNull(databaseHelper.selectPosition())

        databaseHelper.insertPosition(position!!)

        position = databaseHelper.selectPosition()

        Assert.assertNotNull(position)

        databaseHelper.deletePosition(position!!.id)

        Assert.assertNull(databaseHelper.selectPosition())

    }

}
