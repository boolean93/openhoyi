package io.openhoyi.mobile;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Xml;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;
import org.xmlpull.v1.XmlSerializer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Self-target, no Activity. Never loads or restarts the observed Mock application's owner. */
public final class MockHierarchyInstrumentation extends Instrumentation {
    private static final String SELF="io.openhoyi.mobile.mock.test", MOCK="io.openhoyi.mobile.mock";
    private Bundle arguments;
    private int nodes;
    private final Object readiness=new Object();
    private long eventGeneration;
    private int rootQueries;
    private String lastWindows="";
    private AccessibilityNodeInfo awaitInitialRoot(UiAutomation automation,long deadline)throws Exception {
        while(true){
            long generation;synchronized(readiness){generation=eventGeneration;}
            // Public window enumeration establishes interactive-window tracking after setServiceInfo.
            // It observes real windows; it does not inject input or wait for event-stream idle.
            List<AccessibilityWindowInfo> windows=automation.getWindows();StringBuilder state=new StringBuilder();
            try{for(AccessibilityWindowInfo window:windows)state.append(window.getId()).append(":").append(window.getType()).append(":active=").append(window.isActive()).append(":focused=").append(window.isFocused()).append(";");}
            finally{for(AccessibilityWindowInfo window:windows)window.recycle();}
            lastWindows=state.toString();rootQueries++;
            AccessibilityNodeInfo value=automation.getRootInActiveWindow();
            if(value!=null)return value;
            synchronized(readiness){
                long remaining=deadline-SystemClock.elapsedRealtime();
                require(remaining>0,"Initial active root unavailable within original request budget; windows="+lastWindows+" queries="+rootQueries);
                if(generation==eventGeneration)readiness.wait(remaining);
            }
        }
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args);arguments=args;start(); }
    private static void require(boolean value,String message) {if(!value)throw new IllegalStateException(message);}
    private static String text(CharSequence input) {
        if(input==null)return "";
        StringBuilder out=new StringBuilder();
        input.codePoints().forEach(cp -> out.appendCodePoint(cp==9||cp==10||cp==13||cp>=32&&cp<=0xd7ff||cp>=0xe000&&cp<=0xfffd||cp>=0x10000&&cp<=0x10ffff?cp:0xfffd));
        return out.toString();
    }
    private static String bounds(Rect r){return "["+r.left+","+r.top+"]["+r.right+","+r.bottom+"]";}
    private static final class Window {
        final int id;final Rect bounds;final String type;
        Window(AccessibilityNodeInfo root,Rect display) {
            require(root!=null,"No active root");require(MOCK.contentEquals(root.getPackageName()),"Active root is not isolated Mock");
            id=root.getWindowId();require(id>=0,"Unknown active window ID");type=text(root.getClassName());
            bounds=new Rect();root.getBoundsInScreen(bounds);require(bounds.intersect(display)&&bounds.width()>0&&bounds.height()>0,"Empty Mock root bounds");
        }
        boolean same(Window other){return id==other.id&&bounds.equals(other.bounds)&&type.equals(other.type);}
    }
    private void write(XmlSerializer xml,AccessibilityNodeInfo node,int index,Rect clip,int windowId)throws Exception {
        require(node!=null,"Null child: incomplete accessibility tree");
        require(node.getWindowId()==windowId&&MOCK.contentEquals(node.getPackageName()),"Tree changed package/window while reading");
        if(!node.isVisibleToUser())return;
        Rect box=new Rect();node.getBoundsInScreen(box);if(!box.intersect(clip))box.setEmpty();
        xml.startTag("","node");nodes++;
        xml.attribute("","index",Integer.toString(index));xml.attribute("","text",text(node.getText()));
        xml.attribute("","resource-id",text(node.getViewIdResourceName()));xml.attribute("","class",text(node.getClassName()));
        xml.attribute("","package",MOCK);xml.attribute("","content-desc",text(node.getContentDescription()));
        xml.attribute("","hint",text(node.getHintText()));
        xml.attribute("","checkable",Boolean.toString(node.isCheckable()));xml.attribute("","checked",Boolean.toString(node.isChecked()));
        xml.attribute("","clickable",Boolean.toString(node.isClickable()));xml.attribute("","enabled",Boolean.toString(node.isEnabled()));
        xml.attribute("","focusable",Boolean.toString(node.isFocusable()));xml.attribute("","focused",Boolean.toString(node.isFocused()));
        xml.attribute("","scrollable",Boolean.toString(node.isScrollable()));xml.attribute("","long-clickable",Boolean.toString(node.isLongClickable()));
        xml.attribute("","password",Boolean.toString(node.isPassword()));xml.attribute("","selected",Boolean.toString(node.isSelected()));
        xml.attribute("","visible-to-user","true");xml.attribute("","bounds",bounds(box));
        int children=node.getChildCount();
        for(int i=0;i<children;i++){AccessibilityNodeInfo child=node.getChild(i);require(child!=null,"Missing child: incomplete tree");try{write(xml,child,i,clip,windowId);}finally{child.recycle();}}
        xml.endTag("","node");
    }
    @Override public void onStart() {
        Bundle result=new Bundle();File temporary=null;AccessibilityNodeInfo root=null,after=null;UiAutomation automation=null;
        try {
            require(SELF.equals(getContext().getPackageName())&&SELF.equals(getTargetContext().getPackageName()),"Collector must target only itself");
            String nonce=arguments.getString("nonce","");require(UUID.fromString(nonce).toString().equals(nonce),"Invalid capture nonce");
            long budget=Long.parseLong(arguments.getString("budgetMs",""));require(budget>0&&budget<=30000,"Invalid remaining request budget");
            long readyStarted=SystemClock.elapsedRealtime(),deadline=readyStarted+budget;
            automation=getUiAutomation();
            automation.setOnAccessibilityEventListener(event->{synchronized(readiness){eventGeneration++;readiness.notifyAll();}});
            AccessibilityServiceInfo info=automation.getServiceInfo();
            info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS|AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            automation.setServiceInfo(info);
            Point size=new Point();((WindowManager)getContext().getSystemService(android.content.Context.WINDOW_SERVICE)).getDefaultDisplay().getRealSize(size);
            Rect display=new Rect(0,0,size.x,size.y);long started=SystemClock.elapsedRealtimeNanos();
            root=awaitInitialRoot(automation,deadline);Window before=new Window(root,display);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();XmlSerializer xml=Xml.newSerializer();xml.setOutput(bytes,"UTF-8");xml.startDocument("UTF-8",true);
            xml.startTag("","hierarchy");xml.attribute("","schema","mock-active-tree-v1");xml.attribute("","nonce",nonce);
            xml.attribute("","package",MOCK);xml.attribute("","window-id",Integer.toString(before.id));xml.attribute("","bounds",bounds(before.bounds));
            xml.attribute("","started-elapsed-nanos",Long.toString(started));
            xml.attribute("","initial-root-wait-ms",Long.toString(SystemClock.elapsedRealtime()-readyStarted));
            xml.attribute("","initial-root-queries",Integer.toString(rootQueries));xml.attribute("","initial-windows",lastWindows);
            write(xml,root,0,before.bounds,before.id);require(nodes>1,"Empty app tree");
            after=automation.getRootInActiveWindow();Window next=new Window(after,display);require(before.same(next),"Active window changed during capture");
            xml.startTag("","capture");xml.attribute("","complete","true");xml.attribute("","nodes",Integer.toString(nodes));
            xml.attribute("","window-id",Integer.toString(next.id));xml.attribute("","bounds",bounds(next.bounds));
            xml.attribute("","finished-elapsed-nanos",Long.toString(SystemClock.elapsedRealtimeNanos()));xml.endTag("","capture");
            xml.endTag("","hierarchy");xml.endDocument();xml.flush();
            File dir=new File(getContext().getFilesDir(),"mock-hierarchy");require(dir.isDirectory()||dir.mkdirs(),"Cannot create owned capture directory");
            File destination=new File(dir,nonce+".xml");require(!destination.exists(),"Nonce file already exists");
            temporary=File.createTempFile("capture-", ".owned",dir);
            try(FileOutputStream output=new FileOutputStream(temporary)){bytes.writeTo(output);output.getFD().sync();}
            Files.move(temporary.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE);temporary=null;
            result.putString("stream","MOCK_ACTIVE_TREE_CAPTURED nonce="+nonce+" nodes="+nodes+" window="+before.id+"\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","MOCK_ACTIVE_TREE_FAILED "+error.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}
        finally{if(automation!=null)automation.setOnAccessibilityEventListener(null);if(root!=null)root.recycle();if(after!=null&&after!=root)after.recycle();if(temporary!=null)temporary.delete();}
    }
}
