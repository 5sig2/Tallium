package dev.sig.tallium.config;

import com.google.gson.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.nio.charset.*;

public final class ShareCode {
    public static final String PREFIX="TLM1:";
    public static final int MAX_TEXT=128*1024,MAX_JSON=512*1024;
    public record Export(int schema,String kind,String game,Config.Profile profile) {}
    public static String encode(Config.Profile original,String kind,String game,Collection<String> groupIds)throws IOException{
        Config.Profile p=ConfigStore.copy(original,Config.Profile.class);
        if(!Set.of("PROFILE","LAYOUT").contains(kind))throw new IOException("Unknown export kind.");
        if(kind.equals("LAYOUT")){
            p.groups.removeIf(g->!groupIds.contains(g.id));Set<String> counters=new HashSet<>();p.groups.forEach(g->counters.addAll(g.members));
            p.counters.removeIf(c->!counters.contains(c.id));Set<String> definitions=new HashSet<>();p.counters.forEach(c->definitions.add(c.definition));
            p.definitions.removeIf(d->!definitions.contains(d.id));p.nametagOrder.removeIf(id->!definitions.contains(id));
            if(p.xpSource!=null&&!counters.contains(p.xpSource)&&p.groups.stream().noneMatch(g->g.id.equals(p.xpSource)))p.xpSource=null;
        }
        for(Config.Counter c:p.counters){if(c.scope==Config.Scope.SELECTED)c.scope=Config.Scope.UNRESOLVED;c.players.clear();}
        byte[] plain=new Gson().toJson(canonical(new Gson().toJsonTree(new Export(1,kind,game,p)))).getBytes(StandardCharsets.UTF_8);
        if(plain.length>MAX_JSON)throw new IOException("Export exceeds 512 KiB.");
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();try(DeflaterOutputStream out=new DeflaterOutputStream(compressed)){out.write(plain);}
        byte[] data=compressed.toByteArray();CRC32 crc=new CRC32();crc.update(data);
        String code=PREFIX+Base64.getUrlEncoder().withoutPadding().encodeToString(data)+"."+String.format(Locale.ROOT,"%08x",crc.getValue());
        if(code.length()>MAX_TEXT)throw new IOException("Export exceeds 128 KiB of text.");return code;
    }
    public static Export decode(String input)throws IOException{
        if(input==null||input.length()>MAX_TEXT)throw new IOException("Code exceeds 128 KiB.");
        String text=input.trim();if(!text.startsWith(PREFIX))throw new IOException("Unsupported code prefix or format version.");
        String[] parts=text.substring(PREFIX.length()).split("\\.",-1);
        if(parts.length!=2||!parts[1].matches("[0-9a-fA-F]{8}")||!parts[0].matches("[A-Za-z0-9_-]+"))throw new IOException("Malformed code.");
        byte[] data;try{data=Base64.getUrlDecoder().decode(parts[0]);}catch(IllegalArgumentException e){throw new IOException("Invalid Base64URL.");}
        CRC32 crc=new CRC32();crc.update(data);if(crc.getValue()!=Long.parseUnsignedLong(parts[1],16))throw new IOException("Checksum mismatch; copy the complete code.");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();Inflater inflater=new Inflater();
        try {inflater.setInput(data);byte[] buffer=new byte[4096];
            while(!inflater.finished()){
                int count=inflater.inflate(buffer);if(bytes.size()+count>MAX_JSON)throw new IOException("Decoded code exceeds 512 KiB.");
                if(count==0&&!inflater.finished())throw new IOException("Incomplete or invalid compressed data.");bytes.write(buffer,0,count);
            }
            if(inflater.getRemaining()!=0)throw new IOException("Trailing compressed data.");
        }catch(DataFormatException e){throw new IOException("Invalid zlib data.");}finally{inflater.end();}
        try {
            String json=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString();
            int nesting=0;boolean quote=false,escape=false;for(char ch:json.toCharArray()){if(quote){if(escape)escape=false;else if(ch=='\\')escape=true;else if(ch=='"')quote=false;}else if(ch=='"')quote=true;else if(ch=='{'||ch=='['){if(++nesting>64)throw new IOException("JSON nesting exceeds 64 levels.");}else if(ch=='}'||ch==']')nesting--;}
            com.google.gson.stream.JsonReader reader=new com.google.gson.stream.JsonReader(new StringReader(json));reader.setLenient(false);
            Export e=ConfigStore.JSON.getAdapter(Export.class).read(reader);
            if(reader.peek()!=com.google.gson.stream.JsonToken.END_DOCUMENT)throw new IOException("Trailing JSON data.");
            if(e==null||e.schema()!=1||e.profile()==null||!Set.of("PROFILE","LAYOUT").contains(e.kind()))throw new IOException("Unsupported export schema or kind.");
            if(e.game()==null||e.game().isBlank()||e.game().length()>40||e.profile().name==null||e.profile().name.isBlank()||e.profile().name.length()>80)throw new IOException("Missing or invalid game context/profile name.");
            if(e.profile().id==null||e.profile().id.isBlank()||e.profile().id.length()>80)throw new IOException("Invalid profile ID.");
            Config.validateProfile(e.profile(),new HashSet<>(Set.of(e.profile().id)));
            for(Config.Counter c:e.profile().counters){if(!c.players.isEmpty()||c.scope==Config.Scope.SELECTED)throw new IOException("Share codes cannot contain player identities.");}
            return e;
        }catch(RuntimeException|StackOverflowError e){throw new IOException("Invalid profile: "+e.getMessage());}
    }
    private static JsonElement canonical(JsonElement value){if(value.isJsonObject()){JsonObject out=new JsonObject();value.getAsJsonObject().keySet().stream().sorted().forEach(key->out.add(key,canonical(value.getAsJsonObject().get(key))));return out;}
        if(value.isJsonArray()){JsonArray out=new JsonArray();value.getAsJsonArray().forEach(v->out.add(canonical(v)));return out;}return value;}
    public static Config.Profile remap(Config.Profile original){
        Config.Profile p=ConfigStore.copy(original,Config.Profile.class);Map<String,String> ids=new HashMap<>();
        ids.put(p.id,Config.id());p.definitions.forEach(d->ids.put(d.id,Config.id()));p.counters.forEach(c->ids.put(c.id,Config.id()));p.groups.forEach(g->ids.put(g.id,Config.id()));
        p.id=ids.get(p.id);p.definitions.forEach(d->d.id=ids.get(d.id));p.counters.forEach(c->{c.id=ids.get(c.id);c.definition=ids.get(c.definition);});
        p.groups.forEach(g->{g.id=ids.get(g.id);g.members.replaceAll(ids::get);});p.nametagOrder.replaceAll(ids::get);p.xpSource=ids.get(p.xpSource);return p;
    }
}
