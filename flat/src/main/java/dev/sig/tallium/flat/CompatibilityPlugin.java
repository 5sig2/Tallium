package dev.sig.tallium.flat;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.MethodRemapper;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;


public final class CompatibilityPlugin implements IMixinConfigPlugin {
    private final Map<String,ClassNode> templates = new ConcurrentHashMap<>();
    private final Map<String,Properties> bindings = new ConcurrentHashMap<>();
    public static final Set<String> APPLIED = ConcurrentHashMap.newKeySet();
    @Override public void onLoad(String mixinPackage) {
        Selection.TABLE.size();System.setProperty("tallium.compatibility.game",Selection.GAME);
        System.setProperty("tallium.compatibility.group",Selection.TABLE.getProperty("group","prototype"));
        System.out.println("Tallium: Minecraft "+Selection.GAME+", adapter "+System.getProperty("tallium.compatibility.group"));
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName,String mixinClassName) { return true; }
    @Override public void acceptTargets(Set<String> mine,Set<String> others) {}
    @Override public List<String> getMixins() { return List.of(Selection.TABLE.getProperty("mixins").split(",")); }
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
    private ClassNode template(String name) {
        return templates.computeIfAbsent(name, key -> {
            try(InputStream input=getClass().getClassLoader().getResourceAsStream(key+".class")) {
                if(input==null)throw new IllegalStateException("Missing shared implementation: "+key);
                var node=new ClassNode();new ClassReader(input).accept(node,0);return node;
            } catch(IOException error) { throw new IllegalStateException(error); }
        });
    }
    @Override public void postApply(String target,ClassNode destination,String mixin,IMixinInfo info) {
        APPLIED.add(mixin);
        if(!mixin.contains(".Transform_"))return;
        String key=target.replace('.','/');
        String shape=Selection.TABLE.getProperty(key+".shape");
        if(shape==null)throw new IllegalStateException("No selected class shape: "+target);
        var assembled=new ClassNode();template(shape).accept(assembled);assembled.name=key;
        assembled.methods.clear();
        String methods=Selection.TABLE.getProperty(key+".methods","");
        for(String selected:methods.split(",")) {
            if(selected.isEmpty())continue;
            String[] spec=selected.split("\\|",3);String[] entry=spec[0].split("#",2);
            MethodNode original=template(entry[0]).methods.get(Integer.parseInt(entry[1]));
            Properties binding=bindings.computeIfAbsent(spec[1],Selection::read);
            var mapper=new Remapper(){
                @Override public String map(String key){return binding.getProperty(key,key);}
                @Override public String mapMethodName(String owner,String key,String descriptor){return binding.getProperty(key,key);}
                @Override public String mapFieldName(String owner,String key,String descriptor){return binding.getProperty(key,key);}
            };
            var method=new MethodNode(original.access,spec[2],mapper.mapMethodDesc(original.desc),mapper.mapSignature(original.signature,false),
                original.exceptions.stream().map(mapper::mapType).toArray(String[]::new));
            original.accept(new MethodRemapper(method,mapper));assembled.methods.add(method);
        }

        destination.version=assembled.version;destination.access=assembled.access;destination.name=assembled.name;
        destination.signature=assembled.signature;destination.superName=assembled.superName;destination.interfaces=assembled.interfaces;
        destination.sourceFile=assembled.sourceFile;destination.sourceDebug=assembled.sourceDebug;destination.module=assembled.module;
        destination.outerClass=assembled.outerClass;destination.outerMethod=assembled.outerMethod;destination.outerMethodDesc=assembled.outerMethodDesc;
        destination.visibleAnnotations=assembled.visibleAnnotations;destination.invisibleAnnotations=assembled.invisibleAnnotations;
        destination.visibleTypeAnnotations=assembled.visibleTypeAnnotations;destination.invisibleTypeAnnotations=assembled.invisibleTypeAnnotations;
        destination.attrs=assembled.attrs;destination.innerClasses=assembled.innerClasses;destination.nestHostClass=assembled.nestHostClass;
        destination.nestMembers=assembled.nestMembers;destination.permittedSubclasses=assembled.permittedSubclasses;
        destination.recordComponents=assembled.recordComponents;destination.fields=assembled.fields;destination.methods=assembled.methods;
    }
}
