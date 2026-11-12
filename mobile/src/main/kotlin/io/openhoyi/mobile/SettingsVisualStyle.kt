package io.openhoyi.mobile

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

/** M8 page-local presentation. No service, preference, control, or shared-theme ownership. */
internal class SettingsVisualStyle(val context:Context) {
    val palette=TaskProductVisualStyle(context)
    val evidence=JSONObject()
    fun page(activity:ThemedActivity,title:Int,subtitle:Int,maxContentDp:Int?=null,pageId:String,persistentScrollIndicator:Boolean=false):LinearLayout {
        val (root, body) = createPage(activity,title,subtitle,maxContentDp,pageId,persistentScrollIndicator,back=true,applyInsets=true)
        TaskPageFrame.install(activity,root,palette)
        activity.window.statusBarColor=palette.page;activity.window.navigationBarColor=palette.surface
        activity.setContentView(root)
        return body
    }
    fun fragmentPage(activity:ThemedActivity,title:Int,subtitle:Int,maxContentDp:Int?=null,pageId:String):Pair<LinearLayout,LinearLayout> =
        createPage(activity,title,subtitle,maxContentDp,pageId,false,back=false,applyInsets=false)

    private fun createPage(activity:ThemedActivity,title:Int,subtitle:Int,maxContentDp:Int?,pageId:String,
        persistentScrollIndicator:Boolean,back:Boolean,applyInsets:Boolean):Pair<LinearLayout,LinearLayout> {
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val root=palette.column("product/settings/$pageId/root").apply {
            setBackgroundColor(palette.page)
            if(applyInsets)setOnApplyWindowInsetsListener {view,insets->
                if(Build.VERSION.SDK_INT>=30) {
                    val area=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                    view.setPadding(area.left,area.top,area.right,area.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        val scroll=ScrollView(activity).apply {
            tag="product/settings/$pageId/scroll";isFillViewport=true;id=R.id.m8_settings_scroll
            if(persistentScrollIndicator)isScrollbarFadingEnabled=false
        }
        val container=object:FrameLayout(activity) {
            override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
                val actual=View.MeasureSpec.getSize(widthMeasureSpec)
                (getChildAt(0).layoutParams as LayoutParams).width=maxContentDp?.let {minOf(actual,palette.dp(it))} ?: actual
                super.onMeasure(widthMeasureSpec,heightMeasureSpec)
            }
        }
        val body=palette.column("product/settings/$pageId/body").apply {setPadding(palette.dp(16),palette.dp(16),palette.dp(16),palette.dp(16))}
        container.addView(body,FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_HORIZONTAL));scroll.addView(container)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val header=HoyiUi.header(activity,body,activity.getString(title),back=back)
        (header.layoutParams as LinearLayout.LayoutParams).bottomMargin=0
        palette.styleHeader(header);header.isBaselineAligned=false
        (header.getChildAt(0) as? android.widget.ImageView)?.apply {
            setImageDrawable(HomeVisualIcon.drawable(activity,"back",palette.accent));imageTintList=null
            (layoutParams as LinearLayout.LayoutParams).marginEnd=palette.dp(16)
            background=android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(palette.selected),null,palette.shape(android.graphics.Color.WHITE))
        }
        val words=(0 until header.childCount).map(header::getChildAt).filterIsInstance<LinearLayout>().first()
        (0 until words.childCount).map(words::getChildAt).filterIsInstance<TextView>().forEach {it.setPadding(0,0,0,0)}
        words.addView(palette.text(activity.getString(subtitle),TaskProductVisualStyle.auxiliary,secondary=true),
            LinearLayout.LayoutParams(-1,-2).apply {topMargin=palette.dp(8)})
        return root to body
    }
    fun group(parent:LinearLayout,title:String):LinearLayout {
        palette.add(parent,palette.text(title,TaskProductVisualStyle.section).apply {
            if(Build.VERSION.SDK_INT>=28)isAccessibilityHeading=true
        },24)
        return palette.column().apply {
            setPadding(palette.dp(16),palette.dp(16),palette.dp(16),palette.dp(16));background=palette.shape(palette.surface,true,16)
            palette.add(parent,this,8)
        }
    }
    fun divider(parent:LinearLayout) = palette.divider(parent,8,8)
    fun card(parent:LinearLayout,title:String,top:Int=16):LinearLayout = palette.column().apply {
        setPadding(palette.dp(16),palette.dp(16),palette.dp(16),palette.dp(16));background=palette.shape(palette.surface,true,16)
        palette.add(parent,this,top)
        palette.add(this,palette.text(title,TaskProductVisualStyle.section))
    }
    fun switch(view:Switch,minDp:Int=56) {
        palette.apply(view,TaskProductVisualStyle.body)
        view.minHeight=palette.dp(minDp);view.minimumHeight=view.minHeight;view.minimumWidth=palette.dp(48)
        view.switchPadding=palette.dp(16)
    }
    fun input(view:EditText) {
        palette.apply(view,TaskProductVisualStyle.body)
        view.minHeight=palette.dp(52);view.minimumHeight=view.minHeight
        view.setPadding(palette.dp(12),palette.dp(8),palette.dp(12),palette.dp(8))
        view.backgroundTintList=null
        view.background=android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled),palette.shape(palette.disabled,true))
            addState(intArrayOf(android.R.attr.state_focused),android.graphics.drawable.GradientDrawable().apply {
                setColor(palette.surface);cornerRadius=palette.dp(12).toFloat();setStroke(palette.dp(1),palette.accent)
            })
            addState(intArrayOf(),palette.shape(palette.surface,true))
        }
        view.setHintTextColor(palette.muted)
    }
    fun action(view:Button,outline:Boolean=false,icon:String?=null,atEnd:Boolean=false):Button {
        palette.button(view,outline=outline)
        icon?.let {HomeVisualIcon.buttonIcon(view,it,atEnd=atEnd)}
        return view
    }
    fun dialog(dialog:AlertDialog,body:SettingsDialogViewport?=null) {
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.window?.setBackgroundDrawable(palette.shape(palette.surface))
        val titleId=context.resources.getIdentifier("alertTitle","id","android")
        dialog.findViewById<TextView>(titleId)?.let {palette.apply(it,TaskProductVisualStyle.section)}
        dialog.findViewById<TextView>(android.R.id.message)?.let {palette.apply(it,TaskProductVisualStyle.body)}
        listOf(AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEUTRAL).forEach {which->
            dialog.getButton(which)?.let {palette.button(it,primary=which==AlertDialog.BUTTON_POSITIVE,outline=which!=AlertDialog.BUTTON_POSITIVE)}
        }
        val entry=JSONObject();val entries=evidence.optJSONArray("dialogs") ?: JSONArray().also {evidence.put("dialogs",it)}
        entries.put(entry);SettingsDialogLayoutObserver(dialog,body,entry).bind()
    }
    fun pair(first:View,second:View,id:String,fillFirst:Boolean=true)=SettingsPairLayout(context,palette,first,second,id,fillFirst).also {
        evidence.put(id,it.productLayoutEvidence)
    }
}

/** Permanent pair: complete natural strings determine wrapping, never fontScale/language thresholds. */
internal class SettingsPairLayout(context:Context,private val style:TaskProductVisualStyle,
    private val first:View,private val second:View,id:String,private val fillFirst:Boolean=true):ViewGroup(context) {
    val productLayoutEvidence=JSONObject()
    private var inline=false
    init {tag=id;addView(first);addView(second)}
    override fun generateDefaultLayoutParams()=LayoutParams(-2,-2)
    private fun force(view:View) {view.forceLayout();if(view is ViewGroup)(0 until view.childCount).forEach {force(view.getChildAt(it))}}
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val available=(MeasureSpec.getSize(widthMeasureSpec)-paddingLeft-paddingRight).coerceAtLeast(0)
        val free=MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED)
        force(first);force(second);first.measure(free,free);second.measure(free,free)
        val firstNatural=first.measuredWidth;val secondNatural=second.measuredWidth
        inline=firstNatural+style.dp(16)+secondNatural<=available
        force(first);force(second)
        first.measure(MeasureSpec.makeMeasureSpec(if(inline)if(fillFirst)available-style.dp(16)-secondNatural else firstNatural else available,MeasureSpec.EXACTLY),free)
        second.measure(MeasureSpec.makeMeasureSpec(if(inline)if(fillFirst)secondNatural else available-style.dp(16)-firstNatural else available,MeasureSpec.EXACTLY),free)
        val required=if(inline)maxOf(first.measuredHeight,second.measuredHeight) else first.measuredHeight+style.dp(8)+second.measuredHeight
        productLayoutEvidence.put("availableWidthPx",available).put("firstNaturalWidthPx",firstNatural).put("secondNaturalWidthPx",secondNatural)
            .put("inline",inline).put("naturalHeightPx",required+paddingTop+paddingBottom)
        setMeasuredDimension(resolveSize(available+paddingLeft+paddingRight,widthMeasureSpec),resolveSize(required+paddingTop+paddingBottom,heightMeasureSpec))
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int) {
        val contentHeight=height-paddingTop-paddingBottom
        fun place(view:View,start:Int,top:Int) {
            val left=if(layoutDirection==LAYOUT_DIRECTION_RTL)width-paddingRight-start-view.measuredWidth else paddingLeft+start
            view.layout(left,paddingTop+top,left+view.measuredWidth,paddingTop+top+view.measuredHeight)
        }
        place(first,0,if(inline)(contentHeight-first.measuredHeight)/2 else 0)
        place(second,if(inline)first.measuredWidth+style.dp(16) else 0,if(inline)(contentHeight-second.measuredHeight)/2 else first.measuredHeight+style.dp(8))
    }
}

internal class SettingsColumns(context:Context,private val style:TaskProductVisualStyle,val first:View,val second:View,
    private val evidence:JSONObject, private val firstFraction:Float=.4f,
    private val minimumFirstWidth:(()->Int)?=null, private val minimumSecondWidth:(()->Int)?=null):ViewGroup(context) {
    private var split=false
    init {addView(first);addView(second)}
    override fun generateDefaultLayoutParams()=LayoutParams(-1,-2)
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val width=MeasureSpec.getSize(widthMeasureSpec);val gap=style.dp(24)
        val firstRequired=minimumFirstWidth?.invoke() ?: style.dp(280)
        val secondRequired=minimumSecondWidth?.invoke() ?: style.dp(400)
        split=width>=firstRequired+secondRequired+gap
        val firstWidth=if(split)((width-gap)*firstFraction).toInt().coerceIn(firstRequired,width-gap-secondRequired) else width
        val secondWidth=if(split)width-gap-firstWidth else width
        val free=MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED)
        first.measure(MeasureSpec.makeMeasureSpec(firstWidth,MeasureSpec.EXACTLY),free)
        second.measure(MeasureSpec.makeMeasureSpec(secondWidth,MeasureSpec.EXACTLY),free)
        val natural=if(split)maxOf(first.measuredHeight,second.measuredHeight) else first.measuredHeight+gap+second.measuredHeight
        evidence.put("split",split).put("actualWidthPx",width).put("leftWidthPx",firstWidth).put("rightWidthPx",secondWidth).put("naturalHeightPx",natural)
        setMeasuredDimension(resolveSize(width,widthMeasureSpec),resolveSize(natural,heightMeasureSpec))
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int) {
        fun place(view:View,start:Int,top:Int) {
            val left=if(layoutDirection==LAYOUT_DIRECTION_RTL)width-start-view.measuredWidth else start
            view.layout(left,top,left+view.measuredWidth,top+view.measuredHeight)
        }
        place(first,0,0);place(second,if(split)first.measuredWidth+style.dp(24) else 0,if(split)0 else first.measuredHeight+style.dp(24))
    }
}

/** Original numeric editor/action objects are attached once; measurement only changes their rectangles. */
internal class SettingsTemperatureRow(context:Context,private val style:TaskProductVisualStyle,private val words:View,
    private val quantity:View,private val input:EditText,private val action:Button,private val evidence:JSONObject):ViewGroup(context) {
    private var inline=false
    init {setPadding(style.dp(16),style.dp(16),style.dp(16),style.dp(16));background=style.shape(style.surface,true);addView(words);addView(quantity);addView(action)}
    override fun generateDefaultLayoutParams()=LayoutParams(-2,-2)
    private fun force(view:View) {view.forceLayout();if(view is ViewGroup)(0 until view.childCount).forEach {force(view.getChildAt(it))}}
    fun minimumInlineWidthPx():Int {
        val shown=if(input.text.isNullOrEmpty())input.hint?.toString().orEmpty() else input.text.toString()
        input.layoutParams.width=maxOf(style.dp(96),ceil(input.paint.measureText(shown)).toInt()+input.compoundPaddingLeft+input.compoundPaddingRight)
        val free=MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED)
        listOf(words,quantity,action).forEach { force(it);it.measure(free,free) }
        return words.measuredWidth+quantity.measuredWidth+action.measuredWidth+style.dp(32)+paddingLeft+paddingRight
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val width=MeasureSpec.getSize(widthMeasureSpec);val available=(width-paddingLeft-paddingRight).coerceAtLeast(0)
        val free=MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED)
        val shown=if(input.text.isNullOrEmpty())input.hint?.toString().orEmpty() else input.text.toString()
        val inputNaturalWidth=maxOf(style.dp(96),ceil(input.paint.measureText(shown)).toInt()+input.compoundPaddingLeft+input.compoundPaddingRight)
        input.layoutParams.width=inputNaturalWidth
        listOf(words,quantity,action).forEach {force(it);it.measure(free,free)}
        val wordsWidth=words.measuredWidth;val actionWidth=action.measuredWidth;val quantityWidth=quantity.measuredWidth
        inline=wordsWidth+quantityWidth+actionWidth+style.dp(32)<=available
        // Keep the original editor horizontally scrollable, not wider than its permanent
        // quantity host. The measured remainder includes the real unit, 8dp gap and padding.
        val quantityDecorationWidth=(quantityWidth-input.measuredWidth).coerceAtLeast(0)
        val inputWidthLimit=(available-quantityDecorationWidth).coerceAtLeast(0)
        if(!inline)input.layoutParams.width=minOf(inputNaturalWidth,inputWidthLimit)
        listOf(words,quantity,action).forEach(::force)
        val remaining=if(inline)available-quantityWidth-actionWidth-style.dp(32) else available
        words.measure(MeasureSpec.makeMeasureSpec(remaining.coerceAtLeast(0),MeasureSpec.EXACTLY),free)
        quantity.measure(MeasureSpec.makeMeasureSpec(if(inline)quantityWidth else available,MeasureSpec.EXACTLY),free)
        action.measure(MeasureSpec.makeMeasureSpec(if(inline)actionWidth else available,MeasureSpec.EXACTLY),free)
        val content=if(inline)maxOf(words.measuredHeight,quantity.measuredHeight,action.measuredHeight) else words.measuredHeight+quantity.measuredHeight+action.measuredHeight+style.dp(16)
        evidence.put("availableWidthPx",available).put("labelNaturalWidthPx",wordsWidth).put("quantityNaturalWidthPx",quantityWidth)
            .put("inputNaturalWidthPx",inputNaturalWidth).put("quantityDecorationWidthPx",quantityDecorationWidth)
            .put("stackedInputWidthLimitPx",inputWidthLimit).put("inputResolvedWidthPx",input.measuredWidth)
            .put("quantityMinimumWidthPx",style.dp(96)+quantityDecorationWidth)
            .put("quantityMinimumWidthSatisfiable",available>=style.dp(96)+quantityDecorationWidth)
            .put("actionNaturalWidthPx",actionWidth).put("inputText",input.text.toString()).put("inline",inline).put("naturalHeightPx",content+paddingTop+paddingBottom)
        setMeasuredDimension(resolveSize(width,widthMeasureSpec),resolveSize(content+paddingTop+paddingBottom,heightMeasureSpec))
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int) {
        val content=height-paddingTop-paddingBottom
        var start=0;var top=0
        for(view in listOf(words,quantity,action)) {
            val left=if(layoutDirection==LAYOUT_DIRECTION_RTL)width-paddingRight-start-view.measuredWidth else paddingLeft+start
            val y=paddingTop+if(inline)(content-view.measuredHeight)/2 else top
            view.layout(left,y,left+view.measuredWidth,y+view.measuredHeight)
            if(inline)start+=view.measuredWidth+style.dp(16) else top+=view.measuredHeight+style.dp(8)
        }
    }
}

/** Only the form scrolls. Native title/message/buttons retain their original owner and listeners. */
internal class SettingsDialogViewport(context:Context):ScrollView(context) {
    var maximumHeightPx:Int?=null
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val maximum=maximumHeightPx
        val bounded=if(maximum==null)heightMeasureSpec else View.MeasureSpec.makeMeasureSpec(
            if(View.MeasureSpec.getMode(heightMeasureSpec)==View.MeasureSpec.UNSPECIFIED)maximum
            else minOf(maximum,View.MeasureSpec.getSize(heightMeasureSpec)),View.MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec,bounded)
    }
}

internal data class SettingsDialogHeightLayer(val decorationPx:Int,val minimumHeightPx:Int)

/** Inner-to-outer native wrappers. Minimum height is a max constraint, never extra padding. */
internal data class SettingsDialogNaturalChrome(
    val fixedPanelHeightPx:Int,
    val nativeButtonReservePx:Int,
    val bodyWrappers:List<SettingsDialogHeightLayer>,
    val parentAndDecorWrappers:List<SettingsDialogHeightLayer>,
)

internal data class SettingsDialogBodyBudget(
    val nonBodyNonButtonChromePx:Int,
    val nativeButtonReservePx:Int,
    val bodyMaximumHeightPx:Int,
    val minimumEditorBudgetSatisfiable:Boolean,
    val nativeConstraintsSatisfiable:Boolean,
)

/** Independent natural chrome only: body height, scroll and unused decor space are not inputs. */
internal fun settingsDialogBodyBudget(safeHeightPx:Int,chrome:SettingsDialogNaturalChrome?,
    minimumEditorHeightPx:Int):SettingsDialogBodyBudget? {
    if(chrome==null)return null
    val layers=chrome.bodyWrappers+chrome.parentAndDecorWrappers
    require(listOf(safeHeightPx,chrome.fixedPanelHeightPx,chrome.nativeButtonReservePx,minimumEditorHeightPx).all {it>=0})
    require(chrome.fixedPanelHeightPx>=chrome.nativeButtonReservePx)
    require(layers.all {it.decorationPx>=0 && it.minimumHeightPx>=0})
    fun wrap(height:Long,wrappers:List<SettingsDialogHeightLayer>)=wrappers.fold(height) {inner,layer->
        maxOf(inner+layer.decorationPx,layer.minimumHeightPx.toLong())
    }
    val fixed=chrome.fixedPanelHeightPx.toLong()+layers.sumOf {it.decorationPx.toLong()}
    val nativeRequired=wrap(chrome.fixedPanelHeightPx+wrap(0L,chrome.bodyWrappers),chrome.parentAndDecorWrappers)
    val minimumRequired=wrap(chrome.fixedPanelHeightPx+wrap(minimumEditorHeightPx.toLong(),chrome.bodyWrappers),
        chrome.parentAndDecorWrappers)
    return SettingsDialogBodyBudget((fixed-chrome.nativeButtonReservePx).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        chrome.nativeButtonReservePx,if(nativeRequired<=safeHeightPx)(safeHeightPx-fixed).coerceIn(0,Int.MAX_VALUE.toLong()).toInt() else 0,
        minimumRequired<=safeHeightPx,nativeRequired<=safeHeightPx)
}

/** M8 native-window budget; no input/focus/IME request or app/domain state mutation. */
private class SettingsDialogLayoutObserver(private val dialog:AlertDialog,private val body:SettingsDialogViewport?,private val evidence:JSONObject) {
    private var tree:android.view.ViewTreeObserver?=null
    private var callback:android.view.ViewTreeObserver.OnPreDrawListener?=null
    private var key:List<Any?>?=null
    private var nativeButtonReservePx:Int?=null
    private var topNaturalPx=0
    private var contentNaturalPx=0

    fun bind() {
        val decor=requireNotNull(dialog.window).decorView
        fun native(name:String)=dialog.findViewById<View>(decor.resources.getIdentifier(name,"id","android"))
        val panel=native("buttonPanel") as? ViewGroup
        val originalMinimum=panel?.minimumHeight ?: 0
        val attached=object:View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view:View)=Unit
            override fun onViewDetachedFromWindow(view:View) {
                callback?.let {observer->tree?.takeIf {it.isAlive}?.removeOnPreDrawListener(observer)}
                callback=null;tree=null;view.removeOnAttachStateChangeListener(this)
            }
        }
        fun rectangle(view:View?):Any {
            if(view==null)return JSONObject.NULL
            val origin=IntArray(2);view.getLocationOnScreen(origin)
            return JSONArray(listOf(origin[0],origin[1],origin[0]+view.width,origin[1]+view.height))
        }
        fun force(view:View) {view.forceLayout();if(view is ViewGroup)(0 until view.childCount).forEach {force(view.getChildAt(it))}}
        fun foregroundPadding(view:View):Rect=Rect().also {view.foreground?.getPadding(it)}
        fun minimum(view:View)=maxOf(view.minimumHeight,view.background?.minimumHeight ?: 0,
            if(view is FrameLayout)view.foreground?.minimumHeight ?: 0 else 0)
        fun children(group:ViewGroup)=(0 until group.childCount).map(group::getChildAt).filter {it.visibility!=View.GONE}
        fun margins(view:View):List<Int> {
            val lp=view.layoutParams as? ViewGroup.MarginLayoutParams
            return listOf(lp?.leftMargin ?: 0,lp?.topMargin ?: 0,lp?.rightMargin ?: 0,lp?.bottomMargin ?: 0)
        }
        fun verticalMargins(view:View)=margins(view).let {it[1]+it[3]}
        fun viewRecord(view:View)=JSONObject().put("viewIdentity",System.identityHashCode(view)).put("class",view.javaClass.name)
            .put("resourceId",if(view.id==View.NO_ID)JSONObject.NULL else runCatching {view.resources.getResourceName(view.id)}.getOrDefault(view.id.toString()))
            .put("parentIdentity",(view.parent as? View)?.let(System::identityHashCode) ?: JSONObject.NULL)
            .put("rectScreenPx",rectangle(view)).put("measuredWidthPx",view.measuredWidth).put("measuredHeightPx",view.measuredHeight)
            .put("paddingPx",JSONArray(listOf(view.paddingLeft,view.paddingTop,view.paddingRight,view.paddingBottom)))
            .put("marginsPx",JSONArray(margins(view))).put("minimumHeightPx",minimum(view)).put("visibility",view.visibility)
            .put("layoutWidth",view.layoutParams?.width ?: JSONObject.NULL).put("layoutHeight",view.layoutParams?.height ?: JSONObject.NULL)
            .put("foregroundPaddingPx",foregroundPadding(view).let {JSONArray(listOf(it.left,it.top,it.right,it.bottom))})
            .put("layoutDirection",view.layoutDirection).put("weight",(view.layoutParams as? LinearLayout.LayoutParams)?.weight ?: JSONObject.NULL)
        // Immutable values only. Excludes measured height, body, cap and scroll (the former feedback loop).
        fun signature(view:View,descend:Boolean=true,minimumOverride:Int?=null):List<Any?> {
            val lp=view.layoutParams
            val result=mutableListOf<Any?>(System.identityHashCode(view),view.javaClass.name,view.id,view.visibility,view.layoutDirection,
                view.paddingLeft,view.paddingTop,view.paddingRight,view.paddingBottom,minimumOverride ?: minimum(view),view.minimumWidth,
                view.background?.minimumHeight,view.foreground?.minimumHeight,foregroundPadding(view).toString(),
                lp?.width,lp?.height,margins(view),(lp as? LinearLayout.LayoutParams)?.weight,(lp as? LinearLayout.LayoutParams)?.gravity,
                (lp as? FrameLayout.LayoutParams)?.gravity)
            if(view is LinearLayout)result.addAll(listOf(view.orientation,view.gravity,view.showDividers,view.dividerDrawable?.intrinsicHeight))
            if(view is TextView)result.addAll(listOf(view.text?.toString(),view.hint?.toString(),view.textSize,view.typeface,
                view.paint.flags,view.textScaleX,view.paint.textSkewX,view.letterSpacing,view.fontFeatureSettings,view.fontVariationSettings,
                view.includeFontPadding,view.maxLines,view.minLines,view.lineSpacingExtra,view.lineSpacingMultiplier,view.breakStrategy,
                view.hyphenationFrequency,view.compoundPaddingLeft,view.compoundPaddingRight,view.compoundPaddingTop,view.compoundPaddingBottom))
            if(descend && view is ViewGroup)for(i in 0 until view.childCount)result.add(signature(view.getChildAt(i)))
            return result
        }
        fun dividers(group:LinearLayout,count:Int):Int {
            if(count==0)return 0
            val height=(group.dividerDrawable?.intrinsicHeight ?: 0).coerceAtLeast(0)
            val mode=group.showDividers
            return height*((if(mode and LinearLayout.SHOW_DIVIDER_BEGINNING!=0)1 else 0)+
                (if(mode and LinearLayout.SHOW_DIVIDER_MIDDLE!=0)count-1 else 0)+(if(mode and LinearLayout.SHOW_DIVIDER_END!=0)1 else 0))
        }
        fun layer(group:ViewGroup,child:View,extra:Int=0)=SettingsDialogHeightLayer(
            group.paddingTop+group.paddingBottom+verticalMargins(child)+extra,minimum(group))
        fun validHeight(view:View)=view.layoutParams?.height?.let {it==ViewGroup.LayoutParams.MATCH_PARENT || it==ViewGroup.LayoutParams.WRAP_CONTENT}==true
        fun widthSpec(parent:ViewGroup,child:View):Int {
            val m=margins(child)
            return ViewGroup.getChildMeasureSpec(View.MeasureSpec.makeMeasureSpec(parent.width,View.MeasureSpec.EXACTLY),
                parent.paddingLeft+parent.paddingRight+m[0]+m[2],child.layoutParams.width)
        }
        callback=android.view.ViewTreeObserver.OnPreDrawListener {
            val window=dialog.window
            if(window==null || !dialog.isShowing || !decor.isAttachedToWindow)true else {
                val positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                val negative=dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                val neutral=dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                val bar=panel?.takeIf {it.childCount==1}?.getChildAt(0)
                val supportedBar=bar?.javaClass?.name=="com.android.internal.widget.ButtonBarLayout" &&
                    panel?.visibility!=View.GONE && listOfNotNull(positive,negative,neutral).any {it.visibility==View.VISIBLE} &&
                    listOfNotNull(positive,negative,neutral).all {it.parent===bar}
                val parent=native("parentPanel") as? LinearLayout
                val top=native("topPanel")
                val content=native("contentPanel")
                val custom=native("customPanel") as? FrameLayout
                val wrappers=mutableListOf<Pair<ViewGroup,View>>() // body -> customPanel
                val outers=mutableListOf<Pair<ViewGroup,View>>() // parentPanel -> decor
                var reason:String?=null
                fun reject(message:String) {if(reason==null)reason=message}
                if(body!=null) {
                    if(parent?.javaClass?.name!="com.android.internal.widget.AlertDialogLayout" || parent.orientation!=LinearLayout.VERTICAL)
                        reject("unsupported_native_parent_panel")
                    if(custom==null || parent==null || custom.parent!==parent || custom.visibility==View.GONE)reject("missing_owned_custom_panel")
                    if(parent!=null) {
                        val expected=listOfNotNull(top,content,custom,panel).filter {it.visibility!=View.GONE}
                        if(children(parent)!=expected || expected.any {it.parent!==parent})reject("unexpected_native_panel_children_or_order")
                        if(expected.any {it.layoutParams.height!=ViewGroup.LayoutParams.WRAP_CONTENT || ((it.layoutParams as? LinearLayout.LayoutParams)?.weight ?: 0f)!=0f})
                            reject("unsupported_native_panel_height_constraint")
                    }
                    var child:View=body
                    while(child!==custom && reason==null) {
                        val group=child.parent as? FrameLayout
                        if(group==null || group.javaClass!=FrameLayout::class.java || children(group)!=listOf(child) || !validHeight(child)) {
                            reject("unsupported_body_wrapper_chain");break
                        }
                        wrappers.add(group to child);child=group
                    }
                    if(child!==custom)reject("body_not_owned_by_custom_panel")
                    if(parent!=null) {
                        child=parent
                        while(child!==decor && reason==null) {
                            val group=child.parent as? ViewGroup
                            val known=group!=null && (group.javaClass==FrameLayout::class.java || group.javaClass==LinearLayout::class.java ||
                                (group===decor && group.javaClass.name=="com.android.internal.policy.DecorView"))
                            val overlays=if(group===decor)listOf("statusBarBackground","navigationBarBackground").map {decor.resources.getIdentifier(it,"id","android")} else emptyList()
                            val actual=group?.let(::children)?.filterNot {it.id!=0 && it.id in overlays}
                            if(!known || actual!=listOf(child) || !validHeight(child) ||
                                (group is LinearLayout && (group.orientation!=LinearLayout.VERTICAL ||
                                    ((child.layoutParams as? LinearLayout.LayoutParams)?.weight ?: 0f)!=0f))) {
                                reject("unsupported_parent_to_decor_chain");break
                            }
                            outers.add(requireNotNull(group) to child);child=group
                        }
                        if(child!==decor)reject("parent_panel_not_owned_by_decor")
                    }
                    if((wrappers+outers).any {(group,_)->group is FrameLayout && foregroundPadding(group)!=Rect()})
                        reject("unsupported_frame_foreground_insets")
                    if((wrappers+outers).any {(group,child)->margins(child).any {it<0} || minimum(group)<0})
                        reject("unsupported_negative_native_decoration")
                    if(wrappers.any {it.first.width<=0} || outers.any {it.first.width<=0} || parent?.width==0)
                        reject("native_width_not_ready")
                }
                if(!supportedBar || panel==null || bar==null || panel.width<=0)reject("unsupported_native_button_panel")
                val structural=JSONObject().put("parentPanel",parent?.let(::viewRecord) ?: JSONObject.NULL)
                    .put("topPanel",top?.let(::viewRecord) ?: JSONObject.NULL).put("contentPanel",content?.let(::viewRecord) ?: JSONObject.NULL)
                    .put("customPanel",custom?.let(::viewRecord) ?: JSONObject.NULL).put("buttonPanel",panel?.let(::viewRecord) ?: JSONObject.NULL)
                    .put("bodyWrapperChain",JSONArray(wrappers.map {viewRecord(it.first)}))
                    .put("parentToDecorChain",JSONArray(outers.map {viewRecord(it.first)}))
                evidence.put("naturalChromeSchema",2).put("nativeStructure",structural)
                    .put("nativeStructureUncertifiedReason",reason ?: JSONObject.NULL)
                    .put("windowIdentity",System.identityHashCode(window)).put("decorIdentity",System.identityHashCode(decor))
                    .put("windowToken",decor.windowToken?.toString() ?: JSONObject.NULL)
                    .put("windowOwnerClass",dialog.context.javaClass.name).put("bodyIdentity",body?.let(System::identityHashCode) ?: JSONObject.NULL)
                    .put("nativeButtonPanelStructureSupported",supportedBar).put("buttonPanelClass",panel?.javaClass?.name ?: JSONObject.NULL)
                    .put("buttonPanelMinimumPx",panel?.minimumHeight ?: JSONObject.NULL).put("buttonPanelMeasuredHeightPx",panel?.measuredHeight ?: JSONObject.NULL)
                    .put("buttonPanelRectScreenPx",rectangle(panel)).put("buttonBarClass",bar?.javaClass?.name ?: JSONObject.NULL)
                if(reason==null && panel!=null && bar!=null) {
                    val measureKey=listOf(decor.resources.configuration.toString(),decor.layoutDirection,panel.width,
                        signature(panel,false,originalMinimum),signature(bar),top?.let {signature(it)},content?.let {signature(it)},parent?.width,
                        parent?.let {signature(it,false)},
                        wrappers.map {signature(it.first,false)},outers.map {signature(it.first,false)})
                    if(key!=measureKey) {
                        fun natural(view:View?,owner:ViewGroup?):Int {
                            if(view==null || view.visibility==View.GONE)return 0
                            if(owner==null)return 0
                            force(view);view.measure(widthSpec(owner,view),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                            return view.measuredHeight
                        }
                        val naturalBar=natural(bar,panel)
                        nativeButtonReservePx=maxOf(originalMinimum,panel.background?.minimumHeight ?: 0,
                            naturalBar+panel.paddingTop+panel.paddingBottom+verticalMargins(bar))
                        panel.minimumHeight=requireNotNull(nativeButtonReservePx)
                        topNaturalPx=natural(top,parent);contentNaturalPx=natural(content,parent)
                        evidence.put("nativeBarNaturalHeightPx",naturalBar).put("reservedButtonPanelMinimumPx",panel.minimumHeight)
                            .put("topPanelNaturalHeightPx",topNaturalPx).put("contentPanelNaturalHeightPx",contentNaturalPx)
                            .put("naturalMeasurementKey",measureKey.toString())
                            .put("naturalPanelWidthPx",parent?.width ?: JSONObject.NULL).put("naturalPanelLayoutDirection",parent?.layoutDirection ?: JSONObject.NULL)
                            .put("nativeBarBudgetSource","actual_width_UNSPEC_native_bar_plus_actual_parent_padding_and_margins")
                        key=measureKey
                        // Natural measurement replaces measured dimensions temporarily; restore normal native layout before drawing.
                        listOfNotNull(top,content,bar).forEach(::force)
                        var ancestor:View?=panel
                        while(ancestor!=null) {ancestor.forceLayout();ancestor=ancestor.parent as? View}
                        panel.requestLayout()
                        return@OnPreDrawListener false
                    }
                }
                val frame=Rect();decor.getWindowVisibleDisplayFrame(frame)
                val origin=IntArray(2);decor.getLocationOnScreen(origin)
                val safe=Rect(origin[0],origin[1],origin[0]+decor.width,origin[1]+decor.height)
                if(!safe.intersect(frame))safe.setEmpty()
                val naturalChrome=if(body!=null && reason==null && parent!=null && custom!=null && panel!=null && nativeButtonReservePx!=null) {
                    val fixed=topNaturalPx+contentNaturalPx+requireNotNull(nativeButtonReservePx)+
                        listOfNotNull(top,content,panel).filter {it.visibility!=View.GONE}.sumOf(::verticalMargins)
                    val bodyLayers=wrappers.map {(group,child)->layer(group,child)}
                    val outerLayers=listOf(layer(parent,custom,dividers(parent,children(parent).size)))+
                        outers.map {(group,child)->layer(group,child,if(group is LinearLayout)dividers(group,1) else 0)}
                    evidence.put("fixedPanelHeightPx",fixed).put("bodyWrapperConstraints",JSONArray(bodyLayers.map {
                        JSONObject().put("decorationPx",it.decorationPx).put("minimumHeightPx",it.minimumHeightPx)}))
                        .put("parentAndDecorConstraints",JSONArray(outerLayers.map {
                            JSONObject().put("decorationPx",it.decorationPx).put("minimumHeightPx",it.minimumHeightPx)}))
                    SettingsDialogNaturalChrome(fixed,requireNotNull(nativeButtonReservePx),bodyLayers,outerLayers)
                } else null
                val budget=settingsDialogBodyBudget(safe.height(),naturalChrome,(52*decor.resources.displayMetrics.density).toInt())
                val cap=budget?.bodyMaximumHeightPx
                evidence.put("softInputMode",window.attributes.softInputMode).put("availableFrameScreenPx",JSONArray(listOf(frame.left,frame.top,frame.right,frame.bottom)))
                    .put("frameSemantics","actual_getWindowVisibleDisplayFrame_screen_coordinates")
                    .put("decorRectScreenPx",rectangle(decor)).put("bodyViewportRectScreenPx",rectangle(body))
                    .put("bodyNaturalHeightPx",body?.getChildAt(0)?.measuredHeight ?: JSONObject.NULL)
                    .put("safeIntersectionScreenPx",JSONArray(listOf(safe.left,safe.top,safe.right,safe.bottom))).put("safeHeightPx",safe.height())
                    .put("actualButtonPanelHeightPx",panel?.height ?: JSONObject.NULL)
                    .put("nonBodyNonButtonChromePx",budget?.nonBodyNonButtonChromePx ?: JSONObject.NULL)
                    .put("nativeButtonReservePx",budget?.nativeButtonReservePx ?: JSONObject.NULL)
                    .put("bodyBudgetSource",if(budget==null)"UNCERTIFIED_NATIVE_STRUCTURE_NO_BUDGET_ASSUMED" else "independent_natural_panels_and_actual_wrapper_constraints")
                    .put("bodyMaximumHeightPx",cap ?: JSONObject.NULL)
                    .put("minimumEditorBudgetSatisfiable",budget?.minimumEditorBudgetSatisfiable ?: JSONObject.NULL)
                    .put("nativeConstraintsSatisfiable",budget?.nativeConstraintsSatisfiable ?: JSONObject.NULL)
                    .put("titleRectScreenPx",rectangle(native("alertTitle"))).put("positiveRectScreenPx",rectangle(positive)).put("negativeRectScreenPx",rectangle(negative))
                    .put("positiveMeasuredHeightPx",positive?.measuredHeight ?: JSONObject.NULL).put("negativeMeasuredHeightPx",negative?.measuredHeight ?: JSONObject.NULL)
                    .put("imeVisible",if(Build.VERSION.SDK_INT>=30)decor.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) ?: JSONObject.NULL else JSONObject.NULL)
                if(body!=null && body.maximumHeightPx!=cap) {
                    body.maximumHeightPx=cap;body.requestLayout();false
                } else true
            }
        }
        tree=decor.viewTreeObserver;tree!!.addOnPreDrawListener(requireNotNull(callback));decor.addOnAttachStateChangeListener(attached);decor.invalidate()
    }
}
