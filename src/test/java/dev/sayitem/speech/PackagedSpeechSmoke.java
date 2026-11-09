package dev.sayitem.speech;

import com.google.gson.*;
import dev.speechlib.SpeechLib;
import dev.speechlib.AudioSamples;
import javax.sound.sampled.AudioSystem;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.zip.ZipFile;

/** Verifies the two real distribution jars through an isolated consumer classloader. */
public final class PackagedSpeechSmoke {
    public static void main(String[] args)throws Exception {
        Path mod=Path.of(args[0]),work=Path.of(args[1]);Files.createDirectories(work);
        Path library=Path.of(SpeechLib.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try(var jar=new ZipFile(mod.toFile());var lib=new ZipFile(library.toFile())){
            var metadata=metadata(jar);var dependencies=metadata.getAsJsonObject("depends");
            if(!dependencies.has("fabric-api")||!dependencies.has("speechlib"))throw new AssertionError("Missing external dependencies");
            if(jar.stream().anyMatch(e->e.getName().startsWith("dev/speechlib/")||e.getName().startsWith("dev/sayitem/speech/")||e.getName().startsWith("sayitem-speech/")))
                throw new AssertionError("SayItem must not embed the engine or library");
            for(var archive:new ZipFile[]{jar,lib}){
                if(archive.stream().anyMatch(e->e.getName().matches(".*\\.(jar|dll|so|dylib|bin|onnx|part)$")))throw new AssertionError("Heavy or nested dependencies in mod jar");
                if(Files.size(Path.of(archive.getName()))>2*1024*1024)throw new AssertionError("Unexpected jar size");
                if(metadata(archive).has("jars"))throw new AssertionError("Nested mods are not allowed");
            }
            if(!metadata(lib).get("id").getAsString().equals("speechlib"))throw new AssertionError("Wrong library mod");
            if(lib.getEntry("speechlib/model.json")==null||lib.getEntry("speechlib/runtime-index.json")==null||lib.getEntry("speechlib/gpu-index.json")==null)
                throw new AssertionError("Missing library download manifests");
        }
        URL gson=Gson.class.getProtectionDomain().getCodeSource().getLocation();
        URL jna=com.sun.jna.Native.class.getProtectionDomain().getCodeSource().getLocation();
        URL fabric=net.fabricmc.loader.api.FabricLoader.class.getProtectionDomain().getCodeSource().getLocation();
        try(var loader=new URLClassLoader(new URL[]{mod.toUri().toURL(),library.toUri().toURL(),gson,jna,fabric},ClassLoader.getPlatformClassLoader())){
            Class<?> type=loader.loadClass("dev.speechlib.SpeechLib");
            if(!type.getProtectionDomain().getCodeSource().getLocation().equals(library.toUri().toURL()))throw new AssertionError("API not loaded from dependency jar");
            exercise(type,work.resolve("cpu"),null);
            if(Boolean.getBoolean("speechlib.gpu.tests"))exercise(type,work.resolve("gpu"),"GPU_SMALL");
        }
        System.out.println("Packaged SayItem + SpeechLib speech verified; separate small jars, no nested mods, external Fabric API.");
    }
    private static JsonObject metadata(ZipFile jar)throws Exception {
        try(var input=jar.getInputStream(jar.getEntry("fabric.mod.json"));var reader=new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)){
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private static void exercise(Class<?> type,Path cache,String profile)throws Exception {
        Object builder=type.getMethod("builder").invoke(null);
        builder.getClass().getMethod("cacheDirectory",Path.class).invoke(builder,cache);
        if(profile!=null){
            Class<?> profiles=type.getClassLoader().loadClass("dev.speechlib.SpeechProfile");
            builder.getClass().getMethod("profile",profiles).invoke(builder,profiles.getField(profile).get(null));
        }
        Object engine=builder.getClass().getMethod("build").invoke(builder);
        Class<?> api=type.getClassLoader().loadClass("dev.speechlib.SpeechEngine");
        try{
            ((CompletableFuture<?>)api.getMethod("initialize").invoke(engine)).get(180,TimeUnit.SECONDS);
            if(profile!=null&&api.getMethod("backend").invoke(engine).toString().equals("CPU"))throw new AssertionError("GPU unexpectedly fell back");
            try(var input=PackagedSpeechSmoke.class.getResourceAsStream("/audio/diamond-sword.wav");var audio=AudioSystem.getAudioInputStream(new java.io.BufferedInputStream(input))){
                String answer=(String)((CompletableFuture<?>)api.getMethod("transcribe",float[].class,String.class).invoke(engine,AudioSamples.fromPcm16(audio.readAllBytes()),"en")).get(90,TimeUnit.SECONDS);
                if(!dev.sayitem.core.AnswerMatcher.matches(answer,"Diamond sword",java.util.List.of()))throw new AssertionError(answer);
            }
        }finally{((AutoCloseable)engine).close();}
    }
}
