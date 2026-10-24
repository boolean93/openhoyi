package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class StrictVisualSampleTouchPolicyTest {
    @Test fun navigationIconsAndLabelsAreNotIndependentTouchTargets() {
        for (id in listOf("sample/nav/item/1/icon", "sample/nav/item/1/label",
            "sample/nav/selected/icon", "sample/nav/selected/label", "sample/nav/scale/icon")) {
            assertEquals(id, StrictVisualSampleTouchPolicy.Kind.NONE, StrictVisualSampleTouchPolicy.kind(id, false))
            assertNull(StrictVisualSampleTouchPolicy.violation(id, false, false, 24, 24, 48))
        }
    }

    @Test fun actionDescendantTextAndPrimarySuffixDoNotInventTouchTargets() {
        for (id in listOf("sample/share/actions/hint", "sample/inventory/actions/label", "sample/page/primary")) {
            assertEquals(StrictVisualSampleTouchPolicy.Kind.NONE, StrictVisualSampleTouchPolicy.kind(id, false))
        }
    }

    @Test fun exactNavigationContainersMustMeetBothDimensions() {
        for (id in listOf("sample/nav/item/0", "sample/nav/item/3", "sample/nav/selected", "sample/nav/scale", "sample/nav/settings")) {
            assertEquals(StrictVisualSampleTouchPolicy.Kind.NAVIGATION, StrictVisualSampleTouchPolicy.kind(id, false))
            assertNull(StrictVisualSampleTouchPolicy.violation(id, false, true, 48, 48, 48))
            assertNotNull(StrictVisualSampleTouchPolicy.violation(id, false, true, 64, 47, 48))
            assertNotNull(StrictVisualSampleTouchPolicy.violation(id, false, true, 47, 64, 48))
        }
    }

    @Test fun navigationSemanticMustBelongToAContainer() {
        assertNotNull(StrictVisualSampleTouchPolicy.violation("sample/nav/item/1", false, false, 64, 64, 48))
    }

    @Test fun registeredActionIdentityRemainsStrictEvenWithoutClickCallbacks() {
        assertEquals(StrictVisualSampleTouchPolicy.Kind.ACTION,
            StrictVisualSampleTouchPolicy.kind("sample/preparation/deviceEntry", true))
        assertNull(StrictVisualSampleTouchPolicy.violation("sample/preparation/deviceEntry", true, false, 48, 48, 48))
        assertNotNull(StrictVisualSampleTouchPolicy.violation("sample/preparation/deviceEntry", true, false, 48, 47, 48))
    }

    @Test fun disabledDisplayActionIsNotExemptFromTouchSize() {
        assertNotNull(StrictVisualSampleTouchPolicy.violation("sample/share/fileShareDisabled", true, false, 47, 56, 48))
    }

    @Test fun navigationPrefixIsNotAnExactSemanticIdentity() {
        for (id in listOf("sample/nav/item", "sample/nav/item/11", "sample/nav/item/1/icon/primary")) {
            assertEquals(StrictVisualSampleTouchPolicy.Kind.NONE, StrictVisualSampleTouchPolicy.kind(id, false))
        }
    }
}
