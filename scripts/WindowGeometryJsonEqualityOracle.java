package io.openhoyi.mobile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Offline oracle; compiled with the unmodified Android 34 org.json sources. */
public final class WindowGeometryJsonEqualityOracle {
    private static int checked;
    private static final List<String> measurements = new ArrayList<>();

    private static boolean old(JSONObject a, JSONObject b) {
        String first = a.toString(), second = b.toString();
        return first == null ? second == null : first.equals(second);
    }

    private static void pair(String label, JSONObject a, JSONObject b, Boolean expected) {
        boolean original = old(a, b);
        boolean replacement = WindowGeometryJsonEquality.same(a, b);
        if (original != replacement || expected != null && original != expected) {
            throw new AssertionError(label + " old=" + original + " new=" + replacement + " expected=" + expected);
        }
        measurements.add("case="+label+" old="+original+" new="+replacement);
        checked++;
    }

    private static JSONObject object(String name, Object value) throws Exception {
        return new JSONObject().put(name, value);
    }

    private static void synthetic() throws Exception {
        pair("empty-object",new JSONObject(),new JSONObject(),true);
        pair("empty-array",object("v",new JSONArray()),object("v",new JSONArray()),true);
        pair("nested-same",object("v",new JSONArray().put(object("x",1)).put(JSONObject.NULL)),
            object("v",new JSONArray().put(object("x",1)).put(null)),true);
        pair("nested-different",object("v",new JSONArray().put(object("x",1))),
            object("v",new JSONArray().put(object("x",2))),false);
        pair("key-order",new JSONObject().put("a",1).put("b",2),
            new JSONObject().put("b",2).put("a",1),false);
        pair("number-1-int-long",object("v",1),object("v",1L),true);
        pair("number-1-int-double",object("v",1),object("v",1.0),true);
        pair("number-float-double",object("v",1.25f),object("v",1.25d),true);
        pair("number-byte-short",object("v",(byte)7),object("v",(short)7),true);
        pair("number-negative-zero",object("v",-0.0d),object("v",0.0d),false);
        pair("number-float-negative-zero",object("v",-0.0f),object("v",0.0f),true);
        pair("number-extremes",object("v",Long.MAX_VALUE),object("v",Long.MIN_VALUE),false);
        pair("number-double-min",object("v",Double.MIN_VALUE),object("v",Double.MIN_VALUE),true);
        pair("number-float-max",object("v",Float.MAX_VALUE),object("v",Float.MAX_VALUE),true);
        pair("number-scientific",object("v",1e30d),object("v",1e30d),true);
        pair("number-vs-string",object("v",1),object("v","1"),false);
        pair("boolean-vs-string",object("v",true),object("v","true"),false);
        for (String value : Arrays.asList("\"", "/", "\\", "\t\b\n\r\f\u0001", "中文", "\uD83D\uDE00", "\uD800")) {
            pair("string-same",object("v",value),object("v",value),true);
            pair("string-changed",object("v",value),object("v",value+"x"),false);
        }
        pair("boolean-changed",object("v",true),object("v",false),false);
        pair("field-deleted",object("v",1),new JSONObject(),false);
        pair("field-added",new JSONObject(),object("v",1),false);
        pair("null-literal",object("v",new JSONArray().put(JSONObject.NULL)),
            object("v",new JSONArray().put((Object)null)),true);
        pair("unknown-object-same",object("v",new Object(){public String toString(){return "custom";}}),object("v","custom"),true);
        pair("unknown-object-different",object("v",new Object(){public String toString(){return "custom";}}),object("v","other"),false);
        Number odd = new Number(){public int intValue(){return 7;} public long longValue(){return 7;} public float floatValue(){return 7;} public double doubleValue(){return 7;} public String toString(){return "7";}};
        pair("number-subclass",object("v",odd),object("v",7),true);
        pair("nonfinite-both-null-after-first-difference",
            object("early",1).put("late",new JSONArray().put((Object)Double.NaN)),
            object("early",2).put("late",new JSONArray().put((Object)Double.POSITIVE_INFINITY)),true);
        pair("nonfinite-one-null",object("v",new JSONArray().put((Object)Double.NaN)),object("v",1),false);
        pair("object-subclass",object("v",new JSONObject(){public String toString(){return "different";}}),
            object("v",new JSONObject()),true);
        pair("array-subclass",object("v",new JSONArray(){public String toString(){return "different";}}),
            object("v",new JSONArray()),true);
    }

    private static JSONObject reversed(JSONObject source) throws Exception {
        List<String> names = new ArrayList<>();
        for (Iterator<String> keys=source.keys(); keys.hasNext();) names.add(keys.next());
        Collections.reverse(names);
        JSONObject result = new JSONObject();
        for (String name : names) result.put(name,source.get(name));
        return result;
    }

    private static long median(List<Long> data) {
        Collections.sort(data);
        return data.get(data.size()/2);
    }

    private static void measured(String label, JSONObject a, JSONObject b) {
        for (int i=0;i<20;i++) {old(a,b);WindowGeometryJsonEquality.same(a,b);}
        List<Long> oldNs=new ArrayList<>(),newNs=new ArrayList<>(),oldBytes=new ArrayList<>(),newBytes=new ArrayList<>();
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        long thread=Thread.currentThread().getId();
        for (int i=0;i<51;i++) {
            long bytes=bean.getThreadAllocatedBytes(thread), start=System.nanoTime();
            boolean x=old(a,b); long end=System.nanoTime(), afterBytes=bean.getThreadAllocatedBytes(thread);
            oldNs.add(end-start);oldBytes.add(afterBytes-bytes);
            bytes=bean.getThreadAllocatedBytes(thread);start=System.nanoTime();
            boolean y=WindowGeometryJsonEquality.same(a,b);end=System.nanoTime();afterBytes=bean.getThreadAllocatedBytes(thread);
            newNs.add(end-start);newBytes.add(afterBytes-bytes);
            if(x!=y) throw new AssertionError(label+" benchmark result mismatch");
        }
        measurements.add(label+" oldMedianNs="+median(oldNs)+" newMedianNs="+median(newNs)+
            " oldAllocBytes="+Collections.min(oldBytes)+".."+Collections.max(oldBytes)+" median="+median(oldBytes)+
            " newAllocBytes="+Collections.min(newBytes)+".."+Collections.max(newBytes)+" median="+median(newBytes));
    }

    public static void main(String[] args) throws Exception {
        synthetic();
        for (String path : args) {
            JSONObject proof=new JSONObject(Files.readString(Path.of(path)));
            JSONArray attempts=proof.getJSONArray("attempts");
            for(int i=0;i<attempts.length();i++) {
                JSONObject attempt=attempts.getJSONObject(i);
                JSONObject before=attempt.optJSONObject("geometryBefore"), after=attempt.optJSONObject("geometryAfter");
                if(before==null||after==null)continue;
                String label=path+"#"+(i+1);
                pair(label,before,after,null);
                pair(label+"-same",before,before,true);
                pair(label+"-field",before,new JSONObject(before.toString()).put("frameCallbackSequence",-999),false);
                pair(label+"-order",before,reversed(before),false);
                JSONArray size=before.optJSONArray("appSizePx");
                if(size!=null)pair(label+"-array",before,new JSONObject(before.toString()).put("appSizePx",new JSONArray().put(-999)),false);
                measured(label,before,after);
            }
        }
        System.out.println("checked="+checked);
        for(String row:measurements)System.out.println(row);
    }
}
