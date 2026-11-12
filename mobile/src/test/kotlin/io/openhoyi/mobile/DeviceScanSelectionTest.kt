package io.openhoyi.mobile

import io.openhoyi.session.DeviceRole
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class DeviceScanSelectionTest {
    @Test fun generalScanRestoresCoffeeAfterScaleChange() {
        val selection=DeviceScanSelection()
        selection.showRole(DeviceRole.BOOKOO)
        assertFalse(selection.includes(DeviceRole.COFFEE))
        selection.showAll()
        assertTrue(selection.includes(DeviceRole.COFFEE))
        assertTrue(selection.includes(DeviceRole.BOOKOO))
    }
    @Test fun scopedChangeFiltersBothCachedAndLaterResults() {
        val selection=DeviceScanSelection()
        val arrivals=listOf(DeviceRole.BOOKOO,DeviceRole.COFFEE,DeviceRole.BOOKOO)
        for(role in DeviceRole.entries) {
            selection.showRole(role)
            repeat(3) { assertEquals(arrivals.filter {it==role},arrivals.filter(selection::includes)) }
        }
        selection.showAll()
        assertEquals(arrivals,arrivals.filter(selection::includes))
    }
    @Test fun newerRoleSelectionRemainsInForceWhenOldScanResultsArrive() {
        val selection=DeviceScanSelection()
        selection.showAll()
        selection.showRole(DeviceRole.COFFEE)
        assertFalse(selection.includes(DeviceRole.BOOKOO))
        assertTrue(selection.includes(DeviceRole.COFFEE))
        selection.showRole(DeviceRole.BOOKOO)
        assertFalse(selection.includes(DeviceRole.COFFEE))
    }
    @Test fun permissionAndBluetoothCallbacksResumeRatherThanResetSelection() {
        val source=File("src/main/kotlin/io/openhoyi/mobile/AppSettingsFragment.kt").readText()
        assertEquals(2,Regex("deviceControls\\?\\.resumeScan\\(\\)").findAll(source).count())
        assertFalse(source.contains("deviceControls?.scan()"))
        val controls=File("src/main/kotlin/io/openhoyi/mobile/SettingsDeviceControls.kt").readText()
        val resume=controls.substringAfter("fun resumeScan() {").substringBefore("fun render(")
        assertFalse(resume.contains("showAll"))
        assertFalse(resume.contains("showRole"))
        assertTrue(resume.contains("if(!allowed())"))
        assertTrue(resume.contains("if(Build.VERSION.SDK_INT<31)"))
        assertTrue(resume.contains("if(!enabled){error(host.getString(R.string.device_scan_enable_location));return}"))
        assertTrue(resume.indexOf("if(!enabled)")<resume.indexOf("ensureServiceForScan"))
    }
    @Test fun locationHintIsAccurateShortAndLocalized() {
        fun hint(folder:String):String {
            val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$folder/device_scan.xml"))
            return doc.getElementsByTagName("string").item(0).textContent
        }
        assertEquals("开启定位",hint("values"))
        assertEquals("Turn on Location",hint("values-en"))
    }
}
