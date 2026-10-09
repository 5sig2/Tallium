import java.nio.file.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.commons.*;


public final class FlatPack {
    static final String PREFIX="dev/sig/tallium/", BASE=PREFIX+"flat/", MIXINS=BASE+"mixins/", POOL=BASE+"shared/";
    static Path out;
    static final Map<String,byte[]> written=new TreeMap<>();
    static final Map<String,String> methodLocations=new HashMap<>();
    static final Map<String,List<ClassNode>> carriers=new HashMap<>();
    static final Map<String,List<String>> methodHashes=new HashMap<>();
    static final String SYMBOL=BASE+"symbols/Type_";
    static final class Linkage extends Remapper {
        final Map<String,String> classes=new LinkedHashMap<>(),members=new LinkedHashMap<>();
        final Map<String,String> methodNames=new HashMap<>(),fieldNames=new HashMap<>();
        boolean api(String owner){return owner.startsWith("net/minecraft/")||owner.startsWith("com/mojang/blaze3d/")||owner.startsWith(MIXINS);}
        @Override public String map(String owner){return api(owner)?classes.computeIfAbsent(owner,k->SYMBOL+classes.size()):owner;}
        @Override public String mapMethodName(String owner,String name,String desc){
            if(!api(owner)||name.equals("<init>")||name.equals("<clinit>"))return name;
            return methodNames.computeIfAbsent(owner+"."+name+desc,k->{String alias="tallium_api_method_"+methodNames.size();members.put(alias,name);return alias;});
        }
        @Override public String mapFieldName(String owner,String name,String desc){
            if(!api(owner))return name;
            return fieldNames.computeIfAbsent(owner+"."+name+desc,k->{String alias="tallium_api_field_"+fieldNames.size();members.put(alias,name);return alias;});
        }
        Properties bindings(){var p=new Properties();classes.forEach((actual,alias)->p.setProperty(alias,actual));members.forEach(p::setProperty);return p;}
    }
    static byte[] properties(Properties p) throws Exception {

        var result=new ByteArrayOutputStream();
        for(String key:new TreeSet<>(p.stringPropertyNames())){
            var line=new Properties();line.setProperty(key,p.getProperty(key));var part=new ByteArrayOutputStream();line.store(part,null);
            String text=part.toString(java.nio.charset.StandardCharsets.ISO_8859_1).replace("\r\n","\n");
            result.write(text.substring(text.indexOf('\n')+1).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        }return result.toByteArray();
    }
    static String linkedMethod(ClassNode original,MethodNode selected) throws Exception {
        var single=new ClassNode();single.version=65;single.access=Opcodes.ACC_PUBLIC;single.name=original.name;single.superName=original.superName;single.methods.add(selected);
        var linkage=new Linkage();var canonical=new ClassNode();single.accept(new ClassRemapper(canonical,linkage));
        var body=canonical.methods.getFirst();

        if(body.name.equals("extractRenderState"))body.name="render";
        if(body.name.equals("extractWidgetRenderState"))body.name="renderWidget";
        if(body.name.equals("extractContents")||body.name.equals("renderContents"))body.name="renderWidget";
        byte[] bindings=properties(linkage.bindings());String bindingPath="META-INF/tallium/linkage/"+hash(bindings).substring(0,24)+".properties";
        write(bindingPath,bindings);
        return method(canonical,body)+"|"+bindingPath+"|"+selected.name;
    }
    static byte[] bytes(ClassNode n) { var writer=new ClassWriter(0);n.accept(writer);return writer.toByteArray(); }
    static ClassNode node(byte[] b) { var n=new ClassNode();new ClassReader(b).accept(n,ClassReader.SKIP_DEBUG);return n; }
    static ClassNode nativeNode(byte[] b) { var n=new ClassNode();new ClassReader(b).accept(n,0);return n; }
    static String hash(byte[] b) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
    static ClassNode rename(ClassNode n,Map<String,String> names) {var r=new ClassNode();n.accept(new ClassRemapper(r,new Remapper(){@Override public String map(String s){return names.getOrDefault(s,s);}}));return r;}
    static ClassNode copy(ClassNode n){var r=new ClassNode();n.accept(r);return r;}
    static void write(String name,byte[] data) throws Exception {byte[] prior=written.putIfAbsent(name,data);if(prior!=null&&!Arrays.equals(prior,data))throw new IllegalStateException("Duplicate path: "+name);}
    static String method(ClassNode original,MethodNode method) throws Exception {
        var single=new ClassNode();single.version=65;single.access=Opcodes.ACC_PUBLIC;single.name=original.name;
        single.superName=original.superName;single.methods.add(method);
        String digest=hash(bytes(single));String known=methodLocations.get(digest);if(known!=null)return known;
        String logical=original.name.substring(PREFIX.length()).replace('/','_');
        List<ClassNode> parts=carriers.computeIfAbsent(logical,k->new ArrayList<>());
        ClassNode chosen=null;int index=0;
        for(ClassNode part:parts){
            if(!Objects.equals(part.superName,original.superName))continue;
            if(part.methods.stream().noneMatch(m->m.name.equals(method.name)&&m.desc.equals(method.desc))){chosen=part;break;}index++;
        }
        if(chosen==null){index=parts.size();chosen=new ClassNode();chosen.version=65;chosen.access=Opcodes.ACC_PUBLIC;chosen.name=POOL+logical+"_"+index;chosen.superName=original.superName;parts.add(chosen);}
        String location=chosen.name+"#"+chosen.methods.size();chosen.methods.add(method);methodLocations.put(digest,location);
        methodHashes.computeIfAbsent(chosen.name,k->new ArrayList<>()).add(digest);return location;
    }
    static void target(String game,Path jar) throws Exception {
        Map<String,ClassNode> nodes=new TreeMap<>();
        try(var zip=new ZipFile(jar.toFile())){for(var entry:Collections.list(zip.entries()))if(entry.getName().endsWith(".class")){
            byte[] raw=zip.getInputStream(entry).readAllBytes();nodes.put(entry.getName().substring(0,entry.getName().length()-6),entry.getName().startsWith(PREFIX+"mixin/")?nativeNode(raw):node(raw));
        }}
        Map<String,String> names=new HashMap<>();
        for(var e:nodes.entrySet())if(e.getKey().startsWith(PREFIX+"mixin/")){
            var nativeNode=copy(e.getValue());nativeNode.version=65;
            names.put(e.getKey(),MIXINS+"Native_"+hash(bytes(nativeNode)).substring(0,24));
        }
        var selection=new Properties();selection.setProperty("java",game.startsWith("26.")?"25":"21");
        List<String> hooks=new ArrayList<>();
        for(var e:nodes.entrySet()){
            String name=e.getKey();ClassNode selected=rename(e.getValue(),names);
            if(name.startsWith(PREFIX+"mixin/")){
                selected.version=65;write(selected.name+".class",bytes(selected));hooks.add(selected.name.substring(MIXINS.length()));continue;
            }
            if(!name.startsWith(PREFIX+"adapter/")){write(name+".class",bytes(selected));continue;}
            List<String> methods=new ArrayList<>();for(MethodNode m:selected.methods)methods.add(linkedMethod(selected,m));
            var shape=copy(selected);shape.methods.clear();String shapeName=POOL+"Shape_"+hash(bytes(shape)).substring(0,24);

            shape.name=shapeName;write(shapeName+".class",bytes(shape));
            selection.setProperty(name+".shape",shapeName);selection.setProperty(name+".methods",String.join(",",methods));

            var stub=new ClassNode();stub.version=65;stub.access=Opcodes.ACC_PUBLIC;stub.name=name;stub.superName="java/lang/Object";
            write(name+".class",bytes(stub));
            String transform="Transform_"+hash(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0,24);
            var marker=new ClassNode();marker.version=65;marker.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT;marker.name=MIXINS+transform;marker.superName="java/lang/Object";
            var annotation=new AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;");
            annotation.values=new ArrayList<>(List.of("targets",new ArrayList<>(List.of(name.replace('/','.'))),"remap",false));
            marker.invisibleAnnotations=new ArrayList<>(List.of(annotation));write(marker.name+".class",bytes(marker));hooks.add(transform);
        }
        selection.setProperty("mixins",String.join(",",hooks));
        write("META-INF/tallium/versions/"+game+".properties",properties(selection));
        var map=new Properties();names.forEach(map::setProperty);write("META-INF/tallium/versions/"+game+"-mixins.properties",properties(map));
    }
    public static void main(String[] args) throws Exception {
        if(args[0].equals("helper")){helper(args);return;}
        if(args[0].equals("audit")){audit(args[1]);return;}
        out=Path.of(args[0]);
        for(int i=1;i<args.length;i+=2)target(args[i],Path.of(args[i+1]));
        for(var parts:carriers.values())for(var carrier:parts)write(carrier.name+".class",bytes(carrier));
        for(var e:written.entrySet()){var destination=out.resolve(e.getKey());Files.createDirectories(destination.getParent());Files.write(destination,e.getValue());}
        System.out.println("Unique shared methods: "+methodLocations.size()+"; flat files: "+written.size());
    }
    static void audit(String path) throws Exception {
        Map<String,String> seen=new HashMap<>();int methods=0,shapes=0;
        try(var jar=new ZipFile(path)){
            for(var entry:Collections.list(jar.entries())){
                if(!entry.getName().startsWith(POOL)||!entry.getName().endsWith(".class"))continue;
                ClassNode carrier=node(jar.getInputStream(entry).readAllBytes());
                if(entry.getName().contains("/Shape_")){
                    if(!carrier.methods.isEmpty())throw new IllegalStateException("Class shape repeats implementation methods: "+entry.getName());shapes++;continue;
                }
                String logical=carrier.name.substring(POOL.length()).replaceFirst("_[0-9]+$","");
                for(MethodNode method:carrier.methods){
                    var single=new ClassNode();single.version=65;single.access=Opcodes.ACC_PUBLIC;single.name=logical;single.superName=carrier.superName;single.methods.add(method);
                    String digest=hash(bytes(single));String prior=seen.putIfAbsent(digest,entry.getName()+"#"+method.name+method.desc);
                    if(prior!=null)throw new IllegalStateException("Repeated shared method implementation: "+prior+" / "+entry.getName());methods++;
                }
            }
        }
        System.out.println("Flat bytecode sharing audit passed: "+methods+" unique method bodies; "+shapes+" method-free API class shapes.");
    }
    static void helper(String[] args) throws Exception {

        try(var flat=new ZipFile(args[1]);var diagnostic=new ZipFile(args[2]);var output=new ZipOutputStream(Files.newOutputStream(Path.of(args[3])))){
            String game=args[4];var index=new Properties();index.load(flat.getInputStream(flat.getEntry("META-INF/tallium/versions.properties")));String group=index.getProperty(game);
            var selection=new Properties();selection.load(flat.getInputStream(flat.getEntry("META-INF/tallium/groups/"+group+".properties")));
            var nativeNames=new Properties();nativeNames.load(flat.getInputStream(flat.getEntry("META-INF/tallium/groups/"+group+"-mixins.properties")));
            Map<String,String> names=new HashMap<>();nativeNames.forEach((k,v)->names.put((String)k,(String)v));
            for(var entry:Collections.list(diagnostic.entries())){
                String path=entry.getName();if(!path.endsWith(".class"))continue;
                var original=node(diagnostic.getInputStream(entry).readAllBytes());
                var remapped=rename(original,names);String key=original.name;
                boolean isDiagnostic=List.of("UiGallery","GameplayHarness","ObserverHarness","KeyHarness","InspectionHarness").stream().anyMatch(key::contains);
                if(isDiagnostic){output.putNextEntry(new ZipEntry(path));output.write(bytes(remapped));output.closeEntry();continue;}
                String shape=selection.getProperty(key+".shape");
                if(shape!=null){
                    var expected=node(flat.getInputStream(flat.getEntry(shape+".class")).readAllBytes());expected.name=key;expected.methods.clear();
                    for(String method:selection.getProperty(key+".methods").split(",")){
                        if(method.isEmpty())continue;String[] spec=method.split("\\|",3);String[] location=spec[0].split("#",2);
                        var donor=node(flat.getInputStream(flat.getEntry(location[0]+".class")).readAllBytes());
                        var binding=new Properties();binding.load(flat.getInputStream(flat.getEntry(spec[1])));
                        expected.methods.add(bind(donor.methods.get(Integer.parseInt(location[1])),binding,spec[2]));
                    }
                    if(!Arrays.equals(bytes(expected),bytes(remapped)))throw new IllegalStateException("Production method reconstruction differs from independent compile: "+path);
                }
            }
        }
        System.out.println("Every reconstructed production class matches the diagnostic compile; helper contains diagnostics only.");
    }
    static MethodNode bind(MethodNode source,Properties binding,String name){
        var mapper=new Remapper(){
            @Override public String map(String key){return binding.getProperty(key,key);}
            @Override public String mapMethodName(String owner,String key,String desc){return binding.getProperty(key,key);}
            @Override public String mapFieldName(String owner,String key,String desc){return binding.getProperty(key,key);}
        };
        var result=new MethodNode(source.access,name,mapper.mapMethodDesc(source.desc),mapper.mapSignature(source.signature,false),
            source.exceptions.stream().map(mapper::mapType).toArray(String[]::new));
        source.accept(new MethodRemapper(result,mapper));return result;
    }
}
