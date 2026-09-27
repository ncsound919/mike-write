package com.example

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * Verifies the accessibility service is declared and its configuration is well-formed
 * as a real Android system would read it. The service cannot be bound without the user
 * enabling it in system settings, so this validates the declaration + config contract.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityServiceInstrumentedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val component
        get() = ComponentName(context.packageName, "com.example.access.MikeWriteAccessibilityService")

    @Test
    fun serviceIsRegisteredWithTheSystem() {
        val services = context.packageManager.queryIntentServices(
            Intent("android.accessibilityservice.AccessibilityService"),
            PackageManager.GET_META_DATA
        )
        val match = services.firstOrNull { it.serviceInfo.name == component.className }
        assertNotNull("MikeWriteAccessibilityService is not declared", match)
        assertEquals(
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            match!!.serviceInfo.permission
        )
        assertFalse("Accessibility service must not be exported", match.serviceInfo.exported)
    }

    @Test
    fun serviceConfigDeclaresTheRequiredAttributes() {
        val serviceInfo = context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)
        val resId = serviceInfo.metaData.getInt("android.accessibilityservice")
        assertTrue("Missing accessibility service meta-data", resId != 0)

        val parser = context.resources.getXml(resId)
        var type = parser.eventType
        while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
            type = parser.next()
        }
        assertEquals("accessibility-service", parser.name)
        val androidNs = "http://schemas.android.com/apk/res/android"
        assertFalse(parser.getAttributeValue(androidNs, "description").isNullOrBlank())
        assertEquals("true", parser.getAttributeValue(androidNs, "canRetrieveWindowContent"))
    }
}
