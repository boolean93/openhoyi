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
    }

    public static void main(String[] args)throws Exception {
        if(args.length==2 && "--limited".equals(args[0])) {
            JSONObject geometry=new JSONObject(Files.readString(Path.of(args[1])));
            JSONObject syntheticPressure=new JSONObject().put("attempts",new JSONArray()
                .put(new JSONObject().put("geometryBefore",geometry).put("geometryAfter",geometry))
                .put(new JSONObject().put("geometryBefore",geometry).put("geometryAfter",geometry)));
            Path folder=Files.createTempDirectory("window-proof-limited-");
            try {
                File canonical=folder.resolve("frame-sync.json").toFile();
                WindowProofStreamingWriter.write(syntheticPressure,canonical);
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
