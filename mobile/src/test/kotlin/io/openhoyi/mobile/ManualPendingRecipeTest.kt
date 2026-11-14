package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ManualPendingRecipeTest {
    @Test fun manualRecordUsesCurrentSelectionNotRecordedIdentity() {
        assertEquals("A", ManualPendingRecipe.name(true,"manual","A",true))
        assertEquals("Slot B", ManualPendingRecipe.name(true,"manual","Slot B",true))
        assertEquals("New selection", ManualPendingRecipe.name(true,"manual","New selection",true))
    }
    @Test fun noFallbackForDeletedMissingOrInvalidRecipe() {
        assertNull(ManualPendingRecipe.name(true,"manual",null,false))
        assertNull(ManualPendingRecipe.name(true,"manual","Deleted",false))
        assertNull(ManualPendingRecipe.name(true,"manual","",true))
    }
    @Test fun ordinaryAndPreviewScreensRemainUnchanged() {
        assertNull(ManualPendingRecipe.name(false,"manual","A",true))
        assertNull(ManualPendingRecipe.name(true,"A","B",true))
        assertNull(ManualPendingRecipe.name(true,null,"B",true))
    }
    @Test fun newShotHidesPendingWhileLongNameIsNotRewritten() {
        val name="很长的用户自定义曲线名称".repeat(10)
        assertEquals(name,ManualPendingRecipe.name(true,"manual",name,true))
        assertNull(ManualPendingRecipe.name(true,"new-shot",name,true))
    }
}
