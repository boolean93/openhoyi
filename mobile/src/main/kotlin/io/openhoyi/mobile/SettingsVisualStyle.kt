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
        view.thumbTintList=ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf(android.R.attr.state_checked),intArrayOf()),
            intArrayOf(palette.onDisabled,palette.accent,palette.muted))
        view.trackTintList=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(palette.selected,palette.disabled))
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
    private val evidence:JSONObject):ViewGroup(context) {
    private var split=false
    init {addView(first);addView(second)}
    override fun generateDefaultLayoutParams()=LayoutParams(-1,-2)
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val width=MeasureSpec.getSize(widthMeasureSpec);val gap=style.dp(24)
        split=width>=style.dp(280+400+24)
        val firstWidth=if(split)((width-gap)*.4f).toInt() else width
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

/** M8 native-window budget; no input/focus/IME request or app/domain state mutation. */
private class SettingsDialogLayoutObserver(private val dialog:AlertDialog,private val body:SettingsDialogViewport?,private val evidence:JSONObject) {
    private var tree:android.view.ViewTreeObserver?=null
    private var callback:android.view.ViewTreeObserver.OnPreDrawListener?=null
    private var key:List<Any?>?=null
    fun bind() {
        val decor=requireNotNull(dialog.window).decorView
        val panel=dialog.findViewById<ViewGroup>(decor.resources.getIdentifier("buttonPanel","id","android"))
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
        callback=android.view.ViewTreeObserver.OnPreDrawListener {
            val window=dialog.window
            if(window==null || !dialog.isShowing || !decor.isAttachedToWindow)true else {
                val positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                val negative=dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                val bar=panel?.takeIf {it.childCount==1}?.getChildAt(0)
                val supported=bar?.javaClass?.name=="com.android.internal.widget.ButtonBarLayout" &&
                    listOfNotNull(positive,negative).all {it.parent===bar}
                evidence.put("nativeButtonPanelStructureSupported",supported)
                    .put("buttonPanelClass",panel?.javaClass?.name ?: JSONObject.NULL)
                    .put("buttonPanelMinimumPx",panel?.minimumHeight ?: JSONObject.NULL)
                    .put("buttonPanelMeasuredHeightPx",panel?.measuredHeight ?: JSONObject.NULL)
                    .put("buttonPanelRectScreenPx",rectangle(panel)).put("buttonBarClass",bar?.javaClass?.name ?: JSONObject.NULL)
                if(supported && panel!=null && bar!=null && panel.width>0) {
                    val measureKey=listOf(panel.width,decor.resources.configuration.fontScale,positive?.text?.toString(),negative?.text?.toString(),
                        positive?.textSize,negative?.textSize,positive?.typeface,negative?.typeface,positive?.minimumHeight,negative?.minimumHeight)
                    if(key!=measureKey) {
                        val params=bar.layoutParams
                        val margins=params as? ViewGroup.MarginLayoutParams
                        val horizontal=panel.paddingLeft+panel.paddingRight+(margins?.leftMargin ?: 0)+(margins?.rightMargin ?: 0)
                        val vertical=panel.paddingTop+panel.paddingBottom+(margins?.topMargin ?: 0)+(margins?.bottomMargin ?: 0)
                        val widthSpec=ViewGroup.getChildMeasureSpec(View.MeasureSpec.makeMeasureSpec(panel.width,View.MeasureSpec.EXACTLY),horizontal,params.width)
                        force(bar);bar.measure(widthSpec,View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                        val natural=bar.measuredHeight
                        panel.minimumHeight=maxOf(originalMinimum,natural+vertical)
                        evidence.put("nativeBarNaturalHeightPx",natural).put("reservedButtonPanelMinimumPx",panel.minimumHeight)
                            .put("nativeBarBudgetSource","actual_width_UNSPEC_native_bar_plus_actual_parent_padding_and_margins")
                        key=measureKey
                        force(bar);var ancestor:View?=panel
                        while(ancestor!=null) {ancestor.forceLayout();ancestor=ancestor.parent as? View}
                        panel.requestLayout()
                        return@OnPreDrawListener false
                    }
                }
                val frame=Rect();decor.getWindowVisibleDisplayFrame(frame)
                val chrome=body?.let {(decor.height-it.height).coerceAtLeast(0)}
                val cap=chrome?.let {(frame.height()-it).coerceAtLeast(0)}
                evidence.put("softInputMode",window.attributes.softInputMode).put("availableFrameScreenPx",JSONArray(listOf(frame.left,frame.top,frame.right,frame.bottom)))
                    .put("frameSemantics","actual_getWindowVisibleDisplayFrame_screen_coordinates")
                    .put("decorRectScreenPx",rectangle(decor)).put("bodyViewportRectScreenPx",rectangle(body))
                    .put("bodyNaturalHeightPx",body?.getChildAt(0)?.measuredHeight ?: JSONObject.NULL).put("actualChromePx",chrome ?: JSONObject.NULL)
                    .put("bodyMaximumHeightPx",cap ?: JSONObject.NULL)
                    .put("minimumEditorBudgetSatisfiable",cap?.let {it>=(52*decor.resources.displayMetrics.density).toInt()} ?: JSONObject.NULL)
                    .put("titleRectScreenPx",rectangle(dialog.findViewById<View>(decor.resources.getIdentifier("alertTitle","id","android"))))
                    .put("positiveRectScreenPx",rectangle(positive)).put("negativeRectScreenPx",rectangle(negative))
                    .put("positiveMeasuredHeightPx",positive?.measuredHeight ?: JSONObject.NULL).put("negativeMeasuredHeightPx",negative?.measuredHeight ?: JSONObject.NULL)
                    .put("imeVisible",if(Build.VERSION.SDK_INT>=30)decor.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) ?: JSONObject.NULL else JSONObject.NULL)
                if(body!=null && cap!=null && frame.height()>0 && decor.height>0 && body.maximumHeightPx!=cap) {
                    body.maximumHeightPx=cap;body.requestLayout();false
                } else true
            }
        }
        tree=decor.viewTreeObserver;tree!!.addOnPreDrawListener(requireNotNull(callback));decor.addOnAttachStateChangeListener(attached);decor.invalidate()
    }
}
