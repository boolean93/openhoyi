package io.openhoyi.mobile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/** Exact byte oracle compiled against unmodified Android 34 org.json sources. */
public final class WindowProofStreamingWriterOracle {
    private static int cases;

    private static String sha(byte[] bytes) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result=new StringBuilder();
        for(byte b:digest)result.append(String.format("%02x",b&255));
        return result.toString();
    }

    private static String shaFile(Path path) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(java.io.InputStream input=Files.newInputStream(path)) {
            byte[] buffer=new byte[8192];int count;
            while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);
        }
        StringBuilder result=new StringBuilder();
        for(byte b:digest.digest())result.append(String.format("%02x",b&255));
        return result.toString();
    }

    private static void same(String label, JSONObject object) throws Exception {
        Path folder=Files.createTempDirectory("window-proof-oracle-");
        try {
            File canonical=folder.resolve("frame-sync.json").toFile();
            byte[] expected=object.toString(2).getBytes(StandardCharsets.UTF_8);
            WindowProofStreamingWriter.write(object,canonical);
            byte[] actual=Files.readAllBytes(canonical.toPath());
            if(!Arrays.equals(expected,actual))throw new AssertionError(label+" bytes differ at length "+expected.length+"/"+actual.length);
            try(java.util.stream.Stream<Path> paths=Files.list(folder)) {
                if(paths.count()!=1)throw new AssertionError(label+" leaked temporary file");
            }
            System.out.println("case="+label+" bytes="+actual.length+" sha256="+sha(actual));
            cases++;
        }finally{
            try(java.util.stream.Stream<Path> paths=Files.list(folder)) {
                paths.forEach(path->{try{Files.deleteIfExists(path);}catch(IOException e){throw new RuntimeException(e);}});
            }
            Files.deleteIfExists(folder);
        }
    }

    private static void sameStrict(String label, JSONObject object) throws Exception {
        Path folder=Files.createTempDirectory("window-proof-strict-");
        try {
            File canonical=folder.resolve("manifest.json").toFile();
            byte[] expected=object.toString(2).getBytes(StandardCharsets.UTF_8);
            WindowProofStreamingWriter.writeStrict(object,canonical);
            byte[] actual=Files.readAllBytes(canonical.toPath());
            if(!Arrays.equals(expected,actual))throw new AssertionError(label+" strict bytes differ");
            try(java.util.stream.Stream<Path> paths=Files.list(folder)) {
                if(paths.count()!=1)throw new AssertionError(label+" leaked strict temporary file");
            }
            System.out.println("case="+label+" strict=true bytes="+actual.length+" sha256="+sha(actual));cases++;
        }finally{
            try(java.util.stream.Stream<Path> paths=Files.list(folder)) {
                paths.forEach(path->{try{Files.deleteIfExists(path);}catch(IOException e){throw new RuntimeException(e);}});
            }
            Files.deleteIfExists(folder);
        }
    }

    private static void strictReject(String label, JSONObject object) throws Exception {
        Path folder=Files.createTempDirectory("window-proof-strict-reject-");
        File canonical=folder.resolve("manifest.json").toFile();
        byte[] original="existing-canonical".getBytes(StandardCharsets.UTF_8);
        try {
            Files.write(canonical.toPath(),original);
            try {WindowProofStreamingWriter.writeStrict(object,canonical);
                throw new AssertionError(label+" strict accepted unsupported value");}
            catch(JSONException expected) {
                if(!Arrays.equals(original,Files.readAllBytes(canonical.toPath())))
                    throw new AssertionError(label+" replaced canonical");
                try(java.util.stream.Stream<Path> paths=Files.list(folder)) {
                    if(paths.count()!=1)throw new AssertionError(label+" leaked strict temporary file");
                }
            }
            System.out.println("case="+label+" strictRejected=true canonicalPreserved=true");cases++;
        }finally{Files.deleteIfExists(canonical.toPath());Files.deleteIfExists(folder);}
    }

    private static final class ProbeCharSequence implements CharSequence {
        int toStringCalls;
        public int length(){return 4;}
        public char charAt(int index){return "text".charAt(index);}
        public CharSequence subSequence(int start,int end){return "text".subSequence(start,end);}
        @Override public String toString(){toStringCalls++;return "text";}
    }

    private static final class ProbeUnknown {
        int toStringCalls;
        @Override public String toString(){toStringCalls++;return "unknown payload";}
    }

    private static void strictRejectDetail(String label, JSONObject object, String path,
            String className, String reason, java.util.function.IntSupplier calls) throws Exception {
        Path folder=Files.createTempDirectory("window-proof-strict-detail-");
        File canonical=folder.resolve("manifest.json").toFile();
        byte[] original="existing-canonical".getBytes(StandardCharsets.UTF_8);
        try {
            Files.write(canonical.toPath(),original);
            try {WindowProofStreamingWriter.writeStrict(object,canonical);
                throw new AssertionError(label+" strict accepted unsupported value");}
            catch(JSONException expected) {
                String message=expected.getMessage();
                if(message==null || !message.startsWith("Unsupported strict JSON value tree") ||
                        !message.contains("path="+path+";") || !message.contains("class="+className+";") ||
                        !message.contains("reason="+reason))
                    throw new AssertionError(label+" wrong strict rejection detail: "+message);
                if(!Arrays.equals(original,Files.readAllBytes(canonical.toPath())))
                    throw new AssertionError(label+" replaced canonical");
                try(java.util.stream.Stream<Path> paths=Files.list(folder)) {
                    if(paths.count()!=1)throw new AssertionError(label+" leaked strict temporary file");
                }
            }
            if(calls!=null && calls.getAsInt()!=0)throw new AssertionError(label+" diagnostic invoked unknown toString");
            System.out.println("case="+label+" strictRejected=true path="+path+" class="+className+
                " reason="+reason+" canonicalPreserved=true");cases++;
        }finally{Files.deleteIfExists(canonical.toPath());Files.deleteIfExists(folder);}
    }

    private static void strictFailure(String label, JSONObject object, int fault) throws Exception {
        Path folder=Files.createTempDirectory("window-proof-strict-fault-");
        File canonical=folder.resolve("manifest.json").toFile();
        byte[] original="existing-canonical".getBytes(StandardCharsets.UTF_8);
        try {
            Files.write(canonical.toPath(),original);
            WindowProofStreamingWriter.OutputFactory output=file->{
                OutputStream target=Files.newOutputStream(file.toPath());
                return new FilterOutputStream(target){int count;
                    @Override public void write(int b)throws IOException {
                        if(fault==1 && count++>=10)throw new IOException("injected-write");out.write(b);
                    }
                    @Override public void write(byte[] bytes,int offset,int length)throws IOException {
                        for(int i=0;i<length;i++)write(bytes[offset+i]);
                    }
                    @Override public void close()throws IOException {
                        super.close();if(fault==2)throw new IOException("injected-close");
                    }
                };
            };
            WindowProofStreamingWriter.Publisher publish=(temporary,destination)->{
                if(fault==3)throw new IOException("injected-rename");
                Files.move(temporary.toPath(),destination.toPath());
            };
            try {WindowProofStreamingWriter.writeStrict(object,canonical,output,publish);
                throw new AssertionError(label+" strict unexpectedly succeeded");}
            catch(IOException expected) {
                if(!expected.getMessage().startsWith("injected-"))throw expected;
                if(!Arrays.equals(original,Files.readAllBytes(canonical.toPath())))
                    throw new AssertionError(label+" changed canonical");
                try(java.util.stream.Stream<Path> paths=Files.list(folder)) {
                    if(paths.count()!=1)throw new AssertionError(label+" leaked strict temporary file");
                }
            }
            System.out.println("case="+label+" strictFailure=true canonicalPreserved=true");cases++;
        }finally{Files.deleteIfExists(canonical.toPath());Files.deleteIfExists(folder);}
    }

    private static void failure(String label, JSONObject object, int fault) throws Exception {
        Path folder=Files.createTempDirectory("window-proof-fault-");
        File canonical=folder.resolve("frame-sync.json").toFile();
        IOException original=new IOException("original capture error");
        try {
            WindowProofStreamingWriter.OutputFactory output=file->{
                OutputStream target=Files.newOutputStream(file.toPath());
                return new FilterOutputStream(target){
                    int count;
                    @Override public void write(int b)throws IOException {
                        if(fault==1 && count++>=10)throw new IOException("injected-write");
                        out.write(b);
                    }
                    @Override public void write(byte[] bytes,int offset,int length)throws IOException {
                        for(int i=0;i<length;i++)write(bytes[offset+i]);
                    }
                    @Override public void close()throws IOException {
                        super.close();
                        if(fault==2)throw new IOException("injected-close");
                    }
                };
            };
            WindowProofStreamingWriter.Publisher publish=(temporary,destination)->{
                if(fault==3)throw new IOException("injected-rename");
                Files.move(temporary.toPath(),destination.toPath());
            };
            try {
                WindowProofStreamingWriter.write(object,canonical,output,publish);
                throw new AssertionError(label+" unexpectedly succeeded");
            }catch(IOException secondary){original.addSuppressed(secondary);}
            try(java.util.stream.Stream<Path> paths=Files.list(folder)) {
                if(Files.exists(canonical.toPath())||paths.findAny().isPresent())
                    throw new AssertionError(label+" published or retained partial file");
            }
            if(original.getSuppressed().length!=1 || !"original capture error".equals(original.getMessage()))
                throw new AssertionError(label+" first error/suppressed contract changed");
            System.out.println("case="+label+" canonicalAbsent=true firstErrorPreserved=true secondarySuppressed=true");
            cases++;
        }finally{Files.deleteIfExists(folder);}
    }

    private static void existingCanonicalSurvives(JSONObject object) throws Exception {
        Path folder=Files.createTempDirectory("window-proof-original-");
        try {
            File canonical=folder.resolve("frame-sync.json").toFile();
            byte[] original="existing-canonical-proof".getBytes(StandardCharsets.UTF_8);
            Files.write(canonical.toPath(),original);
            try {
                WindowProofStreamingWriter.write(object,canonical,
                    file->Files.newOutputStream(file.toPath()),
                    (temporary,destination)->{throw new IOException("injected-rename");});
                throw new AssertionError("existing canonical unexpectedly replaced");
            } catch(IOException expected) {
                if(!Arrays.equals(original,Files.readAllBytes(canonical.toPath())))
                    throw new AssertionError("existing canonical changed");
                try(java.util.stream.Stream<Path> paths=Files.list(folder)) {
                    if(paths.count()!=1)throw new AssertionError("owned temp leaked");
                }
            }
            System.out.println("case=existing-canonical-rename-failure originalBytesPreserved=true");cases++;
            Files.delete(canonical.toPath());
        }finally{Files.deleteIfExists(folder);}
    }

    private static void synthetic()throws Exception {
        same("empty",new JSONObject().put("object",new JSONObject()).put("array",new JSONArray()));
        same("nested-order-null",new JSONObject().put("z",new JSONArray().put((Object)null).put(JSONObject.NULL)
            .put(new JSONObject().put("b",true).put("a",1))).put("a",false));
        same("numbers",new JSONObject().put("int",1).put("long",1L).put("float",1.25f)
            .put("double",1.25d).put("negativeZero",-0.0d).put("floatNegativeZero",-0.0f)
            .put("extremeLong",Long.MAX_VALUE).put("scientific",1e30d));
        same("escaped",new JSONObject().put("\"/\\\n", "\"/\\\t\b\n\r\f\u0001中文\uD83D\uDE00\uD800"));
        // Pretty prefix is ten UTF-16 code units; high surrogate lands at buffer index 8191.
        same("surrogate-buffer-boundary",new JSONObject().put("v", "a".repeat(8181)+"\uD83D\uDE00"+"\uD800"));
        same("surrogate-buffer-next-chunk",new JSONObject().put("v", "a".repeat(8190)+"\uD83D\uDE00"));
        same("custom-fallback",new JSONObject().put("v",new Object(){public String toString(){return "custom/name";}}));
        Number customNumber=new Number(){public int intValue(){return 7;}public long longValue(){return 7;}
            public float floatValue(){return 7;}public double doubleValue(){return 7;}public String toString(){return "7";}};
        same("number-subclass-fallback",new JSONObject().put("v",customNumber));
        same("container-subclass-fallback",new JSONObject().put("v",new JSONArray(){ { put(1); } }));
        sameStrict("strict-empty",new JSONObject().put("object",new JSONObject()).put("array",new JSONArray()));
        sameStrict("strict-nested-escaped",new JSONObject().put("中文/\n",new JSONArray().put(JSONObject.NULL)
            .put(new JSONObject().put("number",1.25d).put("text","\"/\\\t"))));
        strictReject("strict-custom-object",new JSONObject().put("v",new Object(){public String toString(){return "fallback";}}));
        strictReject("strict-custom-number",new JSONObject().put("v",customNumber));
        strictReject("strict-container-subclass",new JSONObject().put("v",new JSONArray(){ {put(1);} }));
        strictReject("strict-object-subclass",new JSONObject(){ {put("v",1);} });
        strictReject("strict-nonfinite",new JSONObject().put("v",new JSONArray().put((Object)Double.NaN)));
        JSONObject textTree=new JSONObject().put("a~/b",new JSONArray().put(new JSONObject().put("text",new StringBuilder("builder text"))));
        if(!textTree.toString(2).contains("\"text\": \"builder text\""))
            throw new AssertionError("SDK CharSequence text output changed");
        strictRejectDetail("strict-stringbuilder-pointer",textTree,"/a~0~1b/0/text",
            StringBuilder.class.getName(),"non_string_char_sequence",null);
        ProbeCharSequence charSequence=new ProbeCharSequence();
        JSONObject customText=new JSONObject().put("nested",new JSONArray().put(charSequence));
        if(!customText.toString(2).contains("\"text\""))
            throw new AssertionError("SDK custom CharSequence text output changed");
        charSequence.toStringCalls=0;
        strictRejectDetail("strict-custom-charsequence",customText,"/nested/0",
            ProbeCharSequence.class.getName(),"non_string_char_sequence",()->charSequence.toStringCalls);
        JSONObject firstBad=new JSONObject().put("first",new StringBuilder("a")).put("second",new Object());
        strictRejectDetail("strict-first-bad-order",firstBad,"/first",
            StringBuilder.class.getName(),"non_string_char_sequence",null);
        strictRejectDetail("strict-custom-number-path",new JSONObject().put("n",customNumber),"/n",
            customNumber.getClass().getName(),"unsupported_number_class",null);
        JSONArray subclass=new JSONArray(){ {put(1);} };
        strictRejectDetail("strict-container-subclass-path",new JSONObject().put("container",subclass),"/container",
            subclass.getClass().getName(),"unsupported_jsonarray_subclass",null);
        strictRejectDetail("strict-nonfinite-path",new JSONObject().put("bad",new JSONArray().put((Object)Double.NaN)),"/bad/0",
            Double.class.getName(),"non_finite_number",null);
        ProbeUnknown unknown=new ProbeUnknown();
        strictRejectDetail("strict-unknown-no-tostring",new JSONObject().put("v",unknown),"/v",
            ProbeUnknown.class.getName(),"unsupported_class",()->unknown.toStringCalls);
        JSONObject invalid=new JSONObject().put("v",new JSONArray().put((Object)Double.NaN));
        Path folder=Files.createTempDirectory("window-proof-invalid-");
        try {
            File canonical=folder.resolve("frame-sync.json").toFile();
            try{WindowProofStreamingWriter.write(invalid,canonical);throw new AssertionError("invalid number accepted");}
            catch(JSONException expected){
                try(java.util.stream.Stream<Path> paths=Files.list(folder)) {
                    if(Files.exists(canonical.toPath())||paths.findAny().isPresent())
                        throw new AssertionError("invalid number published partial proof");
                }
            }
            System.out.println("case=nonfinite-rejected canonicalAbsent=true");cases++;
        }finally{Files.deleteIfExists(folder);}
        JSONObject ordinary=new JSONObject().put("message","fault fixture").put("array",new JSONArray().put(1).put(2));
        failure("write-failure",ordinary,1);
        failure("close-failure",ordinary,2);
        failure("rename-failure",ordinary,3);
        existingCanonicalSurvives(ordinary);
        strictFailure("strict-write-failure",ordinary,1);
        strictFailure("strict-close-failure",ordinary,2);
        strictFailure("strict-rename-failure",ordinary,3);
    }

    private static void realSummary(Path source, String sourceSha) throws Exception {
        byte[] original=Files.readAllBytes(source);
        if(!sha(original).equals(sourceSha))throw new AssertionError("real summary input SHA changed");
        JSONObject parsed=new JSONObject(new String(original,StandardCharsets.UTF_8));
        byte[] expected=parsed.toString(2).getBytes(StandardCharsets.UTF_8);
        Path folder=Files.createTempDirectory("window-proof-real-summary-");
        try {
            Path canonical=folder.resolve("manifest.json");
            WindowProofStreamingWriter.writeStrict(parsed,canonical.toFile());
            byte[] actual=Files.readAllBytes(canonical);
            if(!Arrays.equals(expected,actual))throw new AssertionError("real summary strict differs from SDK toString(2)");
            if(!Arrays.equals(original,actual))throw new AssertionError("real summary strict differs from ZIP member");
            System.out.println("realSummary=true zipBytesEqual=true bytes="+actual.length+" sha256="+sha(actual));
        }finally {
            try(java.util.stream.Stream<Path> paths=Files.list(folder)) {
                paths.forEach(path->{try{Files.deleteIfExists(path);}catch(IOException e){throw new RuntimeException(e);}});
            }
            Files.deleteIfExists(folder);
        }
    }

    private static void limitedRealSummary(Path source, long expectedBytes, String expectedSha) throws Exception {
        JSONObject parsed=new JSONObject(Files.readString(source));
        Path folder=Files.createTempDirectory("window-proof-limited-real-summary-");
        try {
            Path canonical=folder.resolve("manifest.json");
            WindowProofStreamingWriter.writeStrict(parsed,canonical.toFile());
            long bytes=Files.size(canonical);
            String digest=shaFile(canonical);
            if(bytes!=expectedBytes || !digest.equals(expectedSha))
                throw new AssertionError("limited real summary length/SHA differs from normal heap");
            System.out.println("limitedRealSummary=true parsedAndWrittenSameProcess=true bytes="+bytes+" sha256="+digest);
            Files.delete(canonical);
        }finally{Files.deleteIfExists(folder);}
    }

    // Offline control-flow model of the two Kotlin special-state functions. It does not execute Android code;
    // the Kotlin catch/finally boundaries must also be reviewed statically against this model.
    private static final class SpecialStateResult {
        final List<String> writes=new ArrayList<>();
        Throwable thrown;
        boolean completed;
        boolean proofFailed;
    }

    private static void failIf(Throwable error) throws Throwable {if(error!=null)throw error;}

    private static SpecialStateResult specialStateFlow(boolean history, Throwable caseError, Throwable cleanupError,
            Throwable caseJsonError, Throwable casePersistError, Throwable finalPersistError) {
        SpecialStateResult result=new SpecialStateResult();
        Throwable primary=null;
        if(history){result.writes.add("initialPersist");result.writes.add("runningPersist");}
        try {
            if(history) {
                Throwable current=null;
                try {failIf(caseError);}
                catch(Throwable error){current=error;result.proofFailed=true;throw error;}
                finally {
                    try {
                        result.writes.add("caseJson");failIf(caseJsonError);
                        result.writes.add("casePersist");failIf(casePersistError);
                    }catch(Throwable error) {
                        if(current==null)throw error;
                        if(current!=error)current.addSuppressed(error);
                    }
                }
            }else{
                Throwable current=caseError;
                if(current!=null)result.proofFailed=true;
                if(cleanupError!=null){
                    if(current==null)current=cleanupError;
                    else if(current!=cleanupError)current.addSuppressed(cleanupError);
                    result.proofFailed=true;
                }
                try {
                    result.writes.add("caseJson");failIf(caseJsonError);
                    result.writes.add("casePersist");failIf(casePersistError);
                }catch(Throwable error){
                    if(current==null)current=error;
                    else if(current!=error)current.addSuppressed(error);
                }
                failIf(current);
            }
            result.completed=true;
        }catch(Throwable error){primary=error;result.thrown=error;}
        finally{
            try {result.writes.add("finalPersist");failIf(finalPersistError);}
            catch(Throwable error){
                if(primary==null)result.thrown=error;
                else if(primary!=error)primary.addSuppressed(error);
            }
        }
        return result;
    }

    private static void flowAssert(String name, SpecialStateResult actual, Throwable expected,
            boolean completed, boolean proofFailed, String writes, Throwable... suppressed) {
        if(actual.thrown!=expected || actual.completed!=completed || actual.proofFailed!=proofFailed ||
                !String.join(",",actual.writes).equals(writes) ||
                !Arrays.equals(actual.thrown==null?new Throwable[0]:actual.thrown.getSuppressed(),suppressed))
            throw new AssertionError("special flow mismatch: "+name+" writes="+actual.writes);
        System.out.println("specialFlow="+name+" identity=true suppressedOrder=true completed="+completed+
            " writes="+writes);
    }

    private static void specialStateFlows() {
        for(boolean history:new boolean[]{false,true}){
            String prefix=history?"history":"extraction";
            String start=history?"initialPersist,runningPersist,":"";
            String complete=start+"caseJson,casePersist,finalPersist";
            Error write=new AssertionError("case-persist");
            flowAssert(prefix+"-write-only",specialStateFlow(history,null,null,null,write,null),write,
                false,false,complete);
            Error primary=new AssertionError("case");
            Error writeAfterCase=new AssertionError("case-persist");
            Error finalWrite=new AssertionError("final-persist");
            flowAssert(prefix+"-case-and-two-writes",specialStateFlow(history,primary,null,null,writeAfterCase,finalWrite),
                primary,false,true,complete,writeAfterCase,finalWrite);
            Error json=new AssertionError("case-json");
            flowAssert(prefix+"-json-short-circuit",specialStateFlow(history,null,null,json,null,null),
                json,false,false,start+"caseJson,finalPersist");
            Error jsonAfterCase=new AssertionError("case-json");
            Error finalAfterJson=new AssertionError("final-persist");
            Error caseBeforeJson=new AssertionError("case");
            flowAssert(prefix+"-case-json-final",specialStateFlow(history,caseBeforeJson,null,jsonAfterCase,null,finalAfterJson),
                caseBeforeJson,false,true,start+"caseJson,finalPersist",jsonAfterCase,finalAfterJson);
            flowAssert(prefix+"-success",specialStateFlow(history,null,null,null,null,null),null,
                true,false,complete);
            Error onlyFinal=new AssertionError("final-persist");
            flowAssert(prefix+"-final-only",specialStateFlow(history,null,null,null,null,onlyFinal),onlyFinal,
                true,false,complete);
        }
        Error cleanup=new AssertionError("cleanup");
        Error caseWrite=new AssertionError("case-persist");
        Error finalWrite=new AssertionError("final-persist");
        flowAssert("extraction-cleanup-and-two-writes",
            specialStateFlow(false,null,cleanup,null,caseWrite,finalWrite),cleanup,false,true,
            "caseJson,casePersist,finalPersist",caseWrite,finalWrite);
    }

    public static void main(String[] args)throws Exception {
        if(args.length==1 && "--special-state-flows".equals(args[0])) {
            specialStateFlows();return;
        }
        if(args.length==3 && "--real-summary".equals(args[0])) {
            realSummary(Path.of(args[1]),args[2]);return;
        }
        if(args.length==4 && "--limited-summary".equals(args[0])) {
            limitedRealSummary(Path.of(args[1]),Long.parseLong(args[2]),args[3]);return;
        }
        if(args.length==2 && "--limited".equals(args[0])) {
            JSONObject geometry=new JSONObject(Files.readString(Path.of(args[1])));
            JSONObject syntheticPressure=new JSONObject().put("attempts",new JSONArray()
                .put(new JSONObject().put("geometryBefore",geometry).put("geometryAfter",geometry))
                .put(new JSONObject().put("geometryBefore",geometry).put("geometryAfter",geometry)));
            Path folder=Files.createTempDirectory("window-proof-limited-");
            try {
                File canonical=folder.resolve("frame-sync.json").toFile();
                WindowProofStreamingWriter.writeStrict(syntheticPressure,canonical);
                System.out.println("limitedSynthetic=true bytes="+Files.size(canonical.toPath())+
                    " sha256="+shaFile(canonical.toPath()));
                Files.delete(canonical.toPath());
            }finally{Files.deleteIfExists(folder);}
            return;
        }
        synthetic();
        for(int i=0;i<args.length;i++) {
            byte[] source=Files.readAllBytes(Path.of(args[i]));
            JSONObject parsed=new JSONObject(new String(source,StandardCharsets.UTF_8));
            same("real-"+i+" inputSha256="+sha(source),parsed);
            if(i>=3)sameStrict("real-top-manifest-"+(i-3)+" inputSha256="+sha(source),parsed);
            if(i==2) {
                JSONObject syntheticPressure=new JSONObject().put("attempts",new JSONArray()
                    .put(new JSONObject().put("geometryBefore",parsed).put("geometryAfter",parsed))
                    .put(new JSONObject().put("geometryBefore",parsed).put("geometryAfter",parsed)));
                same("synthetic-two-attempt-four-geometries-NOT-original-forty-proof",syntheticPressure);
            }
        }
        System.out.println("checked="+cases);
    }
}
